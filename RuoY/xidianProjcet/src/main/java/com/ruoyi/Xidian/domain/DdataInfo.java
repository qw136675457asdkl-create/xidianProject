package com.ruoyi.Xidian.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class DdataInfo extends BaseEntity {
    private Integer id;
    private String targetId;
    private String targetType;
    private String targetCategory;
    private String experimentId;
    private String experimentName;
    private Date startTime;
    private String location;
    private String contentDesc;
    private Long projectId;
    private String projectName;
    private String dataName;
    private String dataType;
    private Long storageFileId;
    private String deviceId;
    private String deviceInfo;
    private Integer sampleFrequency;
    private String workStatus;
    private String extAttr;
    private Integer pageNum;
    private Integer pageSize;
    private Boolean isSimulation;
    private String FileName;
    private String fullPath;
    private String fileSize; //文件大小
}
