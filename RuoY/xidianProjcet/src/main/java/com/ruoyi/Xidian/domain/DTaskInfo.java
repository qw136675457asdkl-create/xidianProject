package com.ruoyi.Xidian.domain;

import com.ruoyi.common.core.domain.BaseEntity;

import java.util.List;

public class DTaskInfo extends BaseEntity {
    private Long taskId;
    private String taskName;
    private Long projectId;
    private String projectName;
    private String experimentId;
    private String experimentName;
    private String path;
    private String targetId;
    private String targetType;
    private Integer taskStatus;
    //数据种类
    private List<String> dataTypes;
}
