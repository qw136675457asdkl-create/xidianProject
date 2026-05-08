package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MultipartUploadCompleteRequest {
    @NotBlank(message = "uploadId is required")
    private String uploadId;
}
