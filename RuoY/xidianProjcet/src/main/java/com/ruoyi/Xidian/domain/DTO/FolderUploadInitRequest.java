package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class FolderUploadInitRequest {
    private String dataName;

    private String experimentId;

    @NotBlank(message = "folderName is required")
    private String folderName;

    @NotBlank(message = "folderUploadId is required")
    private String folderUploadId;

    @NotBlank(message = "folderUploadDate is required")
    private String folderUploadDate;

    private Long totalSize;
}
