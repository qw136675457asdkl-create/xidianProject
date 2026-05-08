package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class MultipartUploadInitRequest {
    @NotBlank(message = "fileName is required")
    private String fileName;

    @NotNull(message = "fileSize is required")
    private Long fileSize;

    private String contentType;

    private String businessType;

    private String businessId;
}
