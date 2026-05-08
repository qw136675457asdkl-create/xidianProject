package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

@Data
public class MultipartUploadPartUrlResponse {
    private String uploadId;
    private Integer partNumber;
    private String uploadUrl;
    private String uploadMethod;
    private Integer expireSeconds;
}
