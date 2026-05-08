package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class FileUploadInitResponse {
    private Long fileStorageId;
    private String bucket;
    private String folderObjectName;
    private String fileName;
    private boolean fileExists;
    private String message;
}
