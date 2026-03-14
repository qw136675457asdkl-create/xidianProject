package com.ruoyi.system.service;

public interface ISysLogArchiveService {
    /**
     * 归档并清理指定月份之前的日志
     *
     * @param months 几个月前（如 6 代表 6个月前）
     */
    void archiveAndCleanOldLogs(int months);
}