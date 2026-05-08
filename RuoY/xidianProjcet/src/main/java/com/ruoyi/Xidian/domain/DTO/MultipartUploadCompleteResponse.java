package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class MultipartUploadCompleteResponse {
    private String uploadId;
    private String bucket;
    private String objectName;
    private String etag;
    private String status;
    private Long storageFileId;
}
