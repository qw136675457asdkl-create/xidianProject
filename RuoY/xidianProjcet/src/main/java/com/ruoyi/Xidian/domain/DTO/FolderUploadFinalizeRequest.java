package com.ruoyi.Xidian.domain.DTO;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class FolderUploadFinalizeRequest {
    private String dataName;

    @NotBlank(message = "experimentId is required")
    private String experimentId;

    private String targetId;

    private String targetType;

    private String targetCategory;

    private String dataType;

    private Boolean isSimulation;

    @NotBlank(message = "folderName is required")
    private String folderName;

    @NotNull(message = "folderStorageId is required")
    private Long folderStorageId;

    @Valid
    @NotEmpty(message = "files is required")
    private List<BusinessDataImportFile> files;
}
