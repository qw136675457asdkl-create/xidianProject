package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class MinioUploadInitRequest {
    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String contentType;

    private Long fileSize;

    private String businessType;

    private String businessId;

    private String relativePath;

    private String folderName;

    private String folderUploadId;

    private String folderUploadDate;

    private Boolean isFolder;
}
