package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.*;
import com.ruoyi.Xidian.domain.DTO.BusinessDataImportFile;

import java.util.List;
import java.util.Map;

public interface IDdataService {
    /**
     * 查询数据列表
     * @param ddataInfo
     * @return
     */
    List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo);

    DdataInfo selectDdataInfoByDdataId(Integer id);

    Integer updateDdataInfo(DdataInfo ddataInfo);

    Integer deleteDdataInfos(List<Integer> ids);

    List<Map<String, Object>> getMovePathTree();

    void syncSimulationResultFiles(
            String experimentId,
            List<MdFileStorage> mdFileStorageList,
            List<String> sourceFileNames,
            String createBy,
            String targetCategory, List<TaskDataGroup> taskDataGroup);

    int renameDataName(List<DdataInfo> ddataInfo);

    int backupDataById(Integer id);

    List<BackupData> selectBackupDataList(BackupData backupData);

    String restoreDataFile(Integer BackDataId);

    Integer insertDdataInfosByObjectNames(DdataInfo ddataInfo, List<UploadedFileInfo> uploadedFileInfoList);

    Integer insertDdataInfosByStorageFiles(DdataInfo ddataInfo, List<BusinessDataImportFile> files);

    Integer insertFolderDdataInfoByObjectNames(DdataInfo ddataInfo, List<UploadedFileInfo> uploadedFileInfoList, String folderName);

    Integer insertFolderDdataInfoByStorageFiles(DdataInfo ddataInfo, Long folderStorageId, List<BusinessDataImportFile> files, String folderName);

    int deleteDataInfoById(Integer id);
}
