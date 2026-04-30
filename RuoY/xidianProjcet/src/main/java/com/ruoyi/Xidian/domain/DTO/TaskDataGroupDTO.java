package com.ruoyi.Xidian.domain.DTO;

import java.util.List;
import java.util.Map;

public class TaskDataGroupDTO {
    private String groupCode;
    private String groupName;
    private Integer sortNo;
    private Boolean enabled;
    private List<TaskDataItemDTO> items;
    private Map<String, TaskDataMetricDTO> variables;

    public String getGroupCode() {
        return groupCode;
    }
    public void setGroupCode(String groupCode) {
        this.groupCode = groupCode;
    }
    public String getGroupName() {
        return groupName;
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    public Integer getSortNo() {
        return sortNo;
    }
    public void setSortNo(Integer sortNo) {
        this.sortNo = sortNo;
    }
    public Boolean getEnabled() {
        return enabled;
    }
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    public List<TaskDataItemDTO> getItems() {
        return items;
    }
    public void setItems(List<TaskDataItemDTO> items) {
        this.items = items;
    }
    public Map<String, TaskDataMetricDTO> getVariables() {
        return variables;
    }
    public void setVariables(Map<String, TaskDataMetricDTO> variables) {
        this.variables = variables;
    }
}
