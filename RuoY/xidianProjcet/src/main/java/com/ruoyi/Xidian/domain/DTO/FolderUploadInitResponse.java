package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class FolderUploadInitResponse {
    private Long folderStorageId;
    private String bucket;
    private String folderObjectName;
    private String folderName;
    private boolean folderExists;
    private String message;
}
