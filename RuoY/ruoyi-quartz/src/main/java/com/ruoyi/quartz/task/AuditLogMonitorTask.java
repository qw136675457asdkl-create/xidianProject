package com.ruoyi.quartz.task;

import com.ruoyi.system.mapper.SysOperLogMapper;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysLogArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 审计日志空间监控定时任务
 */
@Component("auditLogMonitorTask") // 这里的名字就是前端配置的 Bean 名称
public class AuditLogMonitorTask {

    private static final Logger log = LoggerFactory.getLogger(AuditLogMonitorTask.class);

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysOperLogMapper operLogMapper;

    @Autowired
    private ISysLogArchiveService logArchiveService;

    // 如果你想发站内信，可以注入公告服务
    // @Autowired
    // private ISysNoticeService noticeService;

    /**
     * 执行监控检测
     * 前端调用目标字符串: auditLogMonitorTask.checkLogSpace()
     */
    public void checkLogSpace() {
        log.info("【审计日志监控】=== 开始检测存储空间 ===");
        try {
            // 1. 从 sys_config 表获取阈值 (如果没有配置，默认给个 5000MB，即 5GB)
            String thresholdStr = configService.selectConfigByKey("audit.log.max.size");
            double thresholdMb = 5000.0;
            if (thresholdStr != null && !thresholdStr.isEmpty()) {
                thresholdMb = Double.parseDouble(thresholdStr);
            }

            // 2. 调用 Mapper 获取当前表实际大小 (需按前面说的在Mapper.xml写好SQL)
            Double currentSizeMb = operLogMapper.getAuditLogTableSize();
            if (currentSizeMb == null) {
                currentSizeMb = 0.0;
            }

            log.info("【审计日志监控】当前大小: {} MB, 告警阈值: {} MB", currentSizeMb, thresholdMb);

            // 3. 判断并触发告警
            if (currentSizeMb >= thresholdMb) {
                triggerAlert(currentSizeMb, thresholdMb);
                // 超过阈值时，自动将 6 个月前的日志备份到硬盘，并从数据库删除
                logArchiveService.archiveAndCleanOldLogs(6);
            }

        } catch (Exception e) {
            log.error("【审计日志监控】检测任务执行异常", e);
        }
        log.info("【审计日志监控】=== 检测结束 ===");
    }

    /**
     * 触发告警逻辑
     */
    private void triggerAlert(double currentSize, double threshold) {
        String msg = String.format("【安全预警】系统审计日志存储空间已达临界值！当前大小: %.2f MB，设定的阈值: %.2f MB。请超级管理员尽快进行清理，防止系统存储爆满！",
                currentSize, threshold);

        // 1. 打印 Error 日志（如果你们对接了日志收集系统，这一步可以直接触发外部告警）
        log.error(msg);

        // 2. TODO: 写入系统通知（站内信），让管理员登录就能看到
        /*
        SysNotice notice = new SysNotice();
        notice.setNoticeTitle("【严重】审计日志空间告警");
        notice.setNoticeType("2"); // 1通知 2公告
        notice.setNoticeContent(msg);
        notice.setStatus("0");
        notice.setCreateBy("system");
        noticeService.insertNotice(notice);
        */

        // 3. TODO: 发送邮件 / 钉钉 / 企业微信 (根据你们公司的基建补充)
    }
}
