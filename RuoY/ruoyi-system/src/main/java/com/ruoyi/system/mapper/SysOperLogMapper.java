package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.SysOperLog;
import org.apache.ibatis.annotations.Param;

/**
 * 操作日志 数据层
 * 
 * @author ruoyi
 */
public interface SysOperLogMapper
{
    /**
     * 新增操作日志
     * 
     * @param operLog 操作日志对象
     */
    public void insertOperlog(SysOperLog operLog);

    /**
     * 查询系统操作日志集合
     * 
     * @param operLog 操作日志对象
     * @return 操作日志集合
     */
    public List<SysOperLog> selectOperLogList(SysOperLog operLog);

    /**
     * 批量删除系统操作日志
     * 
     * @param operIds 需要删除的操作日志ID
     * @return 结果
     */
    public int deleteOperLogByIds(Long[] operIds);

    /**
     * 查询操作日志详细
     * 
     * @param operId 操作ID
     * @return 操作日志对象
     */
    public SysOperLog selectOperLogById(Long operId);

    /**
     * 清空操作日志
     */
    public void cleanOperLog();


    /**
     * 查询操作日志详细
     *
     * @param operIds 需要查询的操作日志ID
     * @return 操作日志对象
     */
    public List<SysOperLog> selectOperLogByIds(Long[] operIds);


    /*
     * 计算操作日志表大小（单位：MB）
     */
    public double getAuditLogTableSize();

    /**
     * 查询指定时间之前的操作日志
     * @param cutoffDate 截止时间
     * @return 日志集合
     */
    public List<SysOperLog> selectOldOperLogs(@Param("cutoffDate") Date cutoffDate);

    /**
     * 删除指定时间之前的操作日志
     * @param cutoffDate 截止时间
     * @return 影响行数
     */
    public int deleteOldOperLogs(@Param("cutoffDate") Date cutoffDate);
}
