package com.ruoyi.Xidian.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

import java.util.Date;

public class BackupData extends BaseEntity {

    /** 主键ID */
    private Integer id;

    /** 原始数据表ID，对应 DdataInfo 的 id */
    private Integer dataInfoId;
    /** 恢复之后的数据id*/
    private Integer restoredDataInfoId;

    /** 目标ID */
    private String targetId;

    /** 目标类型 */
    private String targetType;

    /** 目标类别 */
    private String targetCategory;

    /** 试验ID */
    private String experimentId;

    /** 试验名称 */
    private String experimentName;

    /** 项目ID */
    private Long projectId;

    /** 项目名称 */
    private String projectName;

    /** 数据名称 */
    private String dataName;

    /** 数据类型 */
    private String dataType;

    /** 设备ID */
    private String deviceId;

    /** 设备信息 */
    private String deviceInfo;

    /** 源文件地址 */
    private String sourcePath;

    /** 备份文件地址 */
    private String backupFilePath;

    /** 删除人 */
    private String deleteBy;

    /** 删除时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deleteTime;

    /** 恢复人 */
    private String restoreBy;

    /** 恢复时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date restoreTime;

    /** 是否恢复（0-未恢复，1-已恢复） */
    private Integer isRestored;

    /** 采样频率 */
    private Integer sampleFrequency;

    /** 工作状态 */
    private String workStatus;

    /** 是否仿真 */
    private Integer isSimulation;

    /** 扩展属性 */
    private String extAttr;


    /** 备注 */
    private String remark;

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setDataInfoId(Integer dataInfoId) {
        this.dataInfoId = dataInfoId;
    }

    public Integer getDataInfoId() {
        return dataInfoId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetCategory(String targetCategory) {
        this.targetCategory = targetCategory;
    }

    public String getTargetCategory() {
        return targetCategory;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentName(String experimentName) {
        this.experimentName = experimentName;
    }

    public String getExperimentName() {
        return experimentName;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setDataName(String dataName) {
        this.dataName = dataName;
    }

    public String getDataName() {
        return dataName;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setBackupFilePath(String backupFilePath) {
        this.backupFilePath = backupFilePath;
    }

    public String getBackupFilePath() {
        return backupFilePath;
    }

    public void setDeleteBy(String deleteBy) {
        this.deleteBy = deleteBy;
    }

    public String getDeleteBy() {
        return deleteBy;
    }

    public void setDeleteTime(Date deleteTime) {
        this.deleteTime = deleteTime;
    }

    public Date getDeleteTime() {
        return deleteTime;
    }

    public void setRestoreBy(String restoreBy) {
        this.restoreBy = restoreBy;
    }

    public String getRestoreBy() {
        return restoreBy;
    }

    public void setRestoreTime(Date restoreTime) {
        this.restoreTime = restoreTime;
    }

    public Date getRestoreTime() {
        return restoreTime;
    }

    public void setIsRestored(Integer isRestored) {
        this.isRestored = isRestored;
    }

    public Integer getIsRestored() {
        return isRestored;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getRemark() {
        return remark;
    }
    public void setRestoredDataInfoId(Integer restoredDataInfoId){
        this.restoredDataInfoId = restoredDataInfoId;
    }
    public Integer getRestoredDataInfoId(){
        return  restoredDataInfoId;
    }

    public void setSampleFrequency(Integer sampleFrequency) {
        this.sampleFrequency = sampleFrequency;
    }

    public Integer getSampleFrequency() {
        return sampleFrequency;
    }

    public void setWorkStatus(String workStatus) {
        this.workStatus = workStatus;
    }

    public String getWorkStatus() {
        return workStatus;
    }

    public void setIsSimulation(Integer isSimulation) {
        this.isSimulation = isSimulation;
    }

    public Integer getIsSimulation() {
        return isSimulation;
    }

    public void setExtAttr(String extAttr) {
        this.extAttr = extAttr;
    }

    public String getExtAttr() {
        return extAttr;
    }
}