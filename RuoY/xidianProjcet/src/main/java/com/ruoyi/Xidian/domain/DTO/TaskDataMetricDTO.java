package com.ruoyi.Xidian.domain.DTO;

import com.fasterxml.jackson.annotation.JsonAlias;

public class TaskDataMetricDTO {
    @JsonAlias("field_name")
    private String fieldName;
    @JsonAlias("data_type")
    private String dataType;
    @JsonAlias("recommended_value")
    private String recommendedValue;
    @JsonAlias("fluctuation_range")
    private String fluctuationRange;
    private String description;
    @JsonAlias("sort_no")
    private Integer sortNo;

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
