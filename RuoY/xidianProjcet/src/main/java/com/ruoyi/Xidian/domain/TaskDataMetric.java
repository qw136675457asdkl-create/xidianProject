package com.ruoyi.Xidian.domain;

import java.io.Serializable;
public class TaskDataMetric implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long taskDataGroupId;
    private String fieldName;
    private String dataType;
    private String recommendedValue;
    private String fluctuationRange;
    private String description;
    private Integer sortNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskDataGroupId() {
        return taskDataGroupId;
    }

    public void setTaskDataGroupId(Long taskDataGroupId) {
        this.taskDataGroupId = taskDataGroupId;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public String getRecommendedValue() {
        return recommendedValue;
    }

    public void setRecommendedValue(String recommendedValue) {
        this.recommendedValue = recommendedValue;
    }

    public String getFluctuationRange() {
        return fluctuationRange;
    }

    public void setFluctuationRange(String fluctuationRange) {
        this.fluctuationRange = fluctuationRange;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortNo() {
        return sortNo;
    }

    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
}
