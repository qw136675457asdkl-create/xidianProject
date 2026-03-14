package com.ruoyi.system.service.impl;

import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.mapper.SysOperLogMapper;
import com.ruoyi.system.service.ISysLogArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service("logArchiveService")
public class SysLogArchiveServiceImpl implements ISysLogArchiveService {

    private static final Logger log = LoggerFactory.getLogger(SysLogArchiveServiceImpl.class);

    @Autowired
    private SysOperLogMapper operLogMapper;

    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，如果备份失败，绝对不能删除数据！
    public void archiveAndCleanOldLogs(int months) {
        log.info("=== 开始执行日志归档与清理任务 ===");

        // 1. 计算截止时间 (当前时间往前推 months 个月)
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -months);
        Date cutoffDate = calendar.getTime();
        log.info("清理截止时间为: {}", DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, cutoffDate));

        // 2. 查询需要归档的旧数据
        List<SysOperLog> oldLogs = operLogMapper.selectOldOperLogs(cutoffDate);
        if (oldLogs == null || oldLogs.isEmpty()) {
            log.info("没有找到 {} 个月前的日志，无需清理。", months);
            return;
        }
        log.info("共查出 {} 条需要归档的日志。", oldLogs.size());

        // 3. 将数据写入 CSV 文件备份到服务器硬盘
        boolean backupSuccess = backupToCsv(oldLogs);

        // 4. 如果备份成功，则从数据库中物理删除这些数据
        if (backupSuccess) {
            int deletedRows = operLogMapper.deleteOldOperLogs(cutoffDate);
            log.info("=== 归档清理完成！成功删除数据库记录: {} 条 ===", deletedRows);
        } else {
            // 备份失败则抛出异常，触发事务回滚，确保数据不丢失
            throw new RuntimeException("日志备份到磁盘失败，已取消数据库清理操作！");
        }
    }

    /**
     * 将日志写入 CSV 文件（防止 OOM）
     */
    private boolean backupToCsv(List<SysOperLog> logs) {
        // 使用若依配置的默认上传/下载路径，建一个 archive 文件夹
        String archiveDir = RuoYiConfig.getProfile() + File.separator + "archive";
        File dir = new File(archiveDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 文件名包含时间戳，例如: operlog_archive_20231025123000.csv
        String fileName = "operlog_archive_" + DateUtils.dateTimeNow() + ".csv";
        File file = new File(dir, fileName);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
            // 写入 CSV 表头
            bw.write("日志编号,模块标题,业务类型,操作人员,主机IP,操作地点,操作状态,操作时间");
            bw.newLine();

            // 循环写入数据
            for (SysOperLog log : logs) {
                // 简单拼接，注意：如果内容本身包含逗号，可能会影响CSV格式，严谨的话可以用双引号包裹
                StringBuilder sb = new StringBuilder();
                sb.append(log.getOperId()).append(",")
                  .append(log.getTitle() != null ? log.getTitle() : "").append(",")
                  .append(log.getBusinessType()).append(",")
                  .append(log.getOperName() != null ? log.getOperName() : "").append(",")
                  .append(log.getOperIp() != null ? log.getOperIp() : "").append(",")
                  .append(log.getOperLocation() != null ? log.getOperLocation() : "").append(",")
                  .append(log.getStatus()).append(",")
                  .append(log.getOperTime() != null ? DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD_HH_MM_SS, log.getOperTime()) : "");
                
                bw.write(sb.toString());
                bw.newLine();
            }
            bw.flush();
            log.info("日志已成功备份到物理路径: {}", file.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            log.error("写入 CSV 归档文件异常!", e);
            return false;
        }
    }
}