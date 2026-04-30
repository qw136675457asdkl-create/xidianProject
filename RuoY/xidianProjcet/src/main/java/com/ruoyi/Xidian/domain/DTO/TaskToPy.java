package com.ruoyi.Xidian.domain.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ruoyi.Xidian.domain.Coordinate;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaskToPy implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("basic")
    private BasicConfig basic;

    @JsonProperty("datasets")
    private Map<String, DatasetConfig> datasets = new LinkedHashMap<>();

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public BasicConfig getBasic() {
        return basic;
    }

    public void setBasic(BasicConfig basic) {
        this.basic = basic;
    }

    public Map<String, DatasetConfig> getDatasets() {
        return datasets;
    }

    public void setDatasets(Map<String, DatasetConfig> datasets) {
        this.datasets = datasets == null ? new LinkedHashMap<>() : datasets;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class BasicConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("motion_model")
        private String motionModel;

        @JsonProperty("start_coords")
        private Coordinate startCoords;

        @JsonProperty("end_coords")
        private Coordinate endCoords;

        public String getMotionModel() {
            return motionModel;
        }

        public void setMotionModel(String motionModel) {
            this.motionModel = motionModel;
        }

        public Coordinate getStartCoords() {
            return startCoords;
        }

        public void setStartCoords(Coordinate startCoords) {
            this.startCoords = startCoords;
        }

        public Coordinate getEndCoords() {
            return endCoords;
        }

        public void setEndCoords(Coordinate endCoords) {
            this.endCoords = endCoords;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class DatasetConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("enabled")
        private Boolean enabled;

        @JsonProperty("filename")
        private String filename;

        @JsonProperty("flight_start_datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
        private Date flightStartDatetime;

        @JsonProperty("flight_end_datetime")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss.SSS", timezone = "GMT+8")
        private Date flightEndDatetime;

        @JsonProperty("sample_rate_hz")
        private BigDecimal sampleRateHz;

        @JsonProperty("target_num")
        private Integer targetNum;

        @JsonProperty("enemy_num")
        private Integer enemyNum;

        @JsonProperty("friendly_num")
        private Integer friendlyNum;

        @JsonProperty("variables")
        private Map<String, VariableConfig> variables = new LinkedHashMap<>();

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public Date getFlightStartDatetime() {
            return flightStartDatetime;
        }

        public void setFlightStartDatetime(Date flightStartDatetime) {
            this.flightStartDatetime = flightStartDatetime;
        }

        public Date getFlightEndDatetime() {
            return flightEndDatetime;
        }

        public void setFlightEndDatetime(Date flightEndDatetime) {
            this.flightEndDatetime = flightEndDatetime;
        }

        public BigDecimal getSampleRateHz() {
            return sampleRateHz;
        }

        public void setSampleRateHz(BigDecimal sampleRateHz) {
            this.sampleRateHz = sampleRateHz;
        }

        public Integer getTargetNum() {
            return targetNum;
        }

        public void setTargetNum(Integer targetNum) {
            this.targetNum = targetNum;
        }

        public Integer getEnemyNum() {
            return enemyNum;
        }

        public void setEnemyNum(Integer enemyNum) {
            this.enemyNum = enemyNum;
        }

        public Integer getFriendlyNum() {
            return friendlyNum;
        }

        public void setFriendlyNum(Integer friendlyNum) {
            this.friendlyNum = friendlyNum;
        }

        public Map<String, VariableConfig> getVariables() {
            return variables;
        }

        public void setVariables(Map<String, VariableConfig> variables) {
            this.variables = variables == null ? new LinkedHashMap<>() : variables;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class VariableConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        @JsonProperty("data_type")
        private String dataType;

        @JsonProperty("recommended_value")
        private String recommendedValue;

        @JsonProperty("fluctuation_range")
        private String fluctuationRange;

        @JsonProperty("description")
        private String description;

        @JsonProperty("sort_no")
        private Integer sortNo;

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
}
