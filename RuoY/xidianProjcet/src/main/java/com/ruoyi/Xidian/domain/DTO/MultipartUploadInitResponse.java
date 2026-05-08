package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class MultipartUploadInitResponse {
    private String uploadId;
    private String bucket;
    private String objectName;
    private String originalFileName;
    private String contentType;
    private Long fileSize;
    private Long partSize;
    private Integer totalParts;
}
