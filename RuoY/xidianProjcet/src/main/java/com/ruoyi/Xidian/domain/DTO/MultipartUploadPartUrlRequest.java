package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class MultipartUploadPartUrlRequest {
    @NotBlank(message = "uploadId is required")
    private String uploadId;

    @NotNull(message = "partNumber is required")
    private Integer partNumber;
}
