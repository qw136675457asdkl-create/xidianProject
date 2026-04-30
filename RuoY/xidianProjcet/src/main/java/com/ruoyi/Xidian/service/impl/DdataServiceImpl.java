package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.config.MinioProperties;
import com.ruoyi.Xidian.domain.*;
import com.ruoyi.Xidian.domain.enums.FileStorageProviderEnum;
import com.ruoyi.Xidian.domain.enums.FileStorageStatusEnum;
import com.ruoyi.Xidian.domain.enums.MinioBusinessTypeEnum;
import com.ruoyi.Xidian.mapper.*;
import com.ruoyi.Xidian.service.*;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.Xidian.utils.FileSizeUtil;
import com.ruoyi.Xidian.utils.NickNameUtil;
import com.ruoyi.Xidian.utils.RegexUtils;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.uuid.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.apache.commons.lang3.StringUtils.trimToNull;

@Service
public class DdataServiceImpl implements IDdataService
{
    private static final Logger log = LoggerFactory.getLogger(DdataServiceImpl.class);
    private static final Set<String> EXPERIMENT_ALLOWED_EXTENSIONS = new HashSet<>(
            Arrays.asList("zip", "csv", "xls", "xlsx", "txt", "json", "doc", "docx", "pdf", "bin", "dat", "raw", "png" , "jpg" ,"jpeg","mp3","mp4")
    );

    @Autowired
    private DdataMapper ddataMapper;

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private DTargetInfoMapper dTargetInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private PathLockManager pathLockManager;

    @Autowired
    private BackDataMapper backDataMapper;

    @Autowired
    private MdFileStorageMapper mdFileStorageMapper;

    @Autowired
    private IDProjectInfoService projectInfoService;

    @Autowired
    private IDExperimentInfoService dExperimentInfoService;

    @Autowired
    private MinioDirectUploadService minioDirectUploadService;

    private final String profile = RuoYiConfig.getProfile() + "/data";
    private final String backUPdir = RuoYiConfig.getBackupDir();
    private final String backAndRestore = RuoYiConfig.getBackAndRestore();
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private FileStorageService fileStorageService;

    @Override
    public List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo)
    {
        List<DdataInfo> ddataInfos = ddataMapper.selectDdataInfoList(ddataInfo);
        ddataInfos.forEach(ddataInfo1 -> {
            List<MdFileStorage> fileStorageList = mdFileStorageMapper.selectListByBussinessId(ddataInfo1.getId().toString());
            Long fileSize = null;
            if(fileStorageList != null && !fileStorageList.isEmpty()){
                fileSize = fileStorageList.stream()
                        .filter(Objects::nonNull)
                        .map(MdFileStorage::getFileSize)
                        .filter(Objects::nonNull)
                        .reduce(0L, Long::sum);
            }
            ddataInfo1.setFileSize(fileSize != null ? FileSizeUtil.formatFileSize(fileSize) : null);
        });
        return ddataInfos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String getpreviewUrl(DdataInfo ddataInfo){
        if (ddataInfo == null || ddataInfo.getId() == null) {
            return null;
        }

        DdataInfo dataInfo = ddataMapper.selectDdataInfoById(ddataInfo.getId());
        if (dataInfo == null || dataInfo.getStorageFileId() == null) {
            log.warn("该数据没有上传文件");
            return null;
        }

        MdFileStorage fileStorage = mdFileStorageMapper.selectById(dataInfo.getStorageFileId());
        if (fileStorage == null || StringUtils.isEmpty(fileStorage.getObjectName())) {
            log.warn("文件存储记录不存在或对象名为空，storageFileId={}", dataInfo.getStorageFileId());
            return null;
        }

        return fileStorageService.createPresignedGetUrl(fileStorage.getObjectName());
    }

    //批量插入数据文件
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer insertDdataInfosByObjectNames(DdataInfo ddataInfo,List<UploadedFileInfo> uploadedFileInfoLists){
        if (uploadedFileInfoLists.isEmpty()) {
            return 0;
        }
        Long userId = SecurityUtils.getUserId();
        String username = NickNameUtil.getNickName();
        List<String> uploadObjectNames = new ArrayList<>();
        int successCount = 0;
        try {
            for (UploadedFileInfo uploadedFileInfo : uploadedFileInfoLists) {
                String originalFileName = trimToNull(uploadedFileInfo.getOriginalFilename());
                String relativePath = normalizeExperimentUploadPath(
                        originalFileName != null ? originalFileName : uploadedFileInfo.getObjectName()
                );
                uploadObjectNames.add(uploadedFileInfo.getObjectName());
                //构建插入数据对象
                DdataInfo insertDataInfo = buildBusinessImportDataInfo(ddataInfo, relativePath, relativePath, true);
                insertDataInfo.setCreateBy(username);
                ddataMapper.insertDdataInfo(insertDataInfo);

                MdFileStorage fileStorage = buildInitFileStorage(
                        minioProperties.getBucket(),
                        uploadedFileInfo.getObjectName(),
                        originalFileName != null ? originalFileName : relativePath,
                        trimToNull(uploadedFileInfo.getContentType()),
                        uploadedFileInfo.getSize(),
                        MinioBusinessTypeEnum.DATA_RELATION.getCode(),
                        insertDataInfo.getId().toString(),
                        userId,
                        username
                );
                mdFileStorageMapper.insertMdFileStorage(fileStorage);
                fileStorage.setCompletedTime(new Date());
                bindStorageFileToBusinessData(fileStorage, insertDataInfo);
                ddataMapper.updateStorageFileId(insertDataInfo.getId(), fileStorage.getId());
                successCount++;
            }
            return successCount;
        } catch (Exception e) {
            for (String objectName : uploadObjectNames) {
                try {
                    fileStorageService.delete(objectName);
                } catch (Exception deleteEx) {
                    log.error("批量新增失败后，回滚删除 MinIO 文件失败，objectName={}", objectName, deleteEx);
                }
            }
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer insertFolderDdataInfoByObjectNames(
            DdataInfo ddataInfo,
            List<UploadedFileInfo> uploadedFileInfoLists,
            String folderName)
    {
        if (uploadedFileInfoLists == null || uploadedFileInfoLists.isEmpty())
        {
            return 0;
        }

        Long userId = SecurityUtils.getUserId();
        String username = NickNameUtil.getNickName();
        List<String> uploadObjectNames = new ArrayList<>();
        try
        {
            String folderDataName = resolveFolderUploadDataName(ddataInfo, folderName, uploadedFileInfoLists);
            DdataInfo insertDataInfo = buildBusinessImportDataInfo(ddataInfo, folderDataName, folderDataName, true);
            insertDataInfo.setDataName(folderDataName);
            insertDataInfo.setCreateBy(username);
            ddataMapper.insertDdataInfo(insertDataInfo);

            Long firstStorageFileId = null;
            for (UploadedFileInfo uploadedFileInfo : uploadedFileInfoLists)
            {
                if (uploadedFileInfo == null || StringUtils.isEmpty(uploadedFileInfo.getObjectName()))
                {
                    continue;
                }

                uploadObjectNames.add(uploadedFileInfo.getObjectName());
                String originalFileName = trimToNull(uploadedFileInfo.getOriginalFilename());
                String relativePath = normalizeExperimentUploadPath(
                        originalFileName != null ? originalFileName : uploadedFileInfo.getObjectName()
                );
                MdFileStorage fileStorage = buildInitFileStorage(
                        minioProperties.getBucket(),
                        uploadedFileInfo.getObjectName(),
                        folderDataName,
                        trimToNull(uploadedFileInfo.getContentType()),
                        uploadedFileInfo.getSize(),
                        MinioBusinessTypeEnum.DATA_RELATION.getCode(),
                        insertDataInfo.getId().toString(),
                        userId,
                        username
                );
                mdFileStorageMapper.insertMdFileStorage(fileStorage);
                fileStorage.setCompletedTime(new Date());
                bindStorageFileToBusinessData(fileStorage, insertDataInfo);
                if (firstStorageFileId == null)
                {
                    firstStorageFileId = fileStorage.getId();
                }
            }

            if (firstStorageFileId == null)
            {
                throw new ServiceException("文件全部上传失败");
            }

            ddataMapper.updateStorageFileId(insertDataInfo.getId(), firstStorageFileId);
            return 1;
        }
        catch (Exception e)
        {
            for (String objectName : uploadObjectNames)
            {
                try
                {
                    fileStorageService.delete(objectName);
                }
                catch (Exception deleteEx)
                {
                    log.error("文件夹新增失败后，回滚删除 MinIO 文件失败，objectName={}", objectName, deleteEx);
                }
            }
            throw new RuntimeException(e);
        }
    }

    private String resolveFolderUploadDataName(
            DdataInfo template,
            String folderName,
            List<UploadedFileInfo> uploadedFileInfoLists)
    {
        String templateName = normalizeFolderUploadDataName(template == null ? null : template.getDataName());
        if (StringUtils.isNotEmpty(templateName))
        {
            return templateName;
        }

        String requestFolderName = normalizeFolderUploadDataName(folderName);
        if (StringUtils.isNotEmpty(requestFolderName))
        {
            return requestFolderName;
        }

        for (UploadedFileInfo uploadedFileInfo : uploadedFileInfoLists)
        {
            String originalFileName = uploadedFileInfo == null ? null : uploadedFileInfo.getOriginalFilename();
            String resolvedFolderName = normalizeFolderUploadDataName(originalFileName);
            if (StringUtils.isNotEmpty(resolvedFolderName))
            {
                return resolvedFolderName;
            }
        }

        return "文件夹";
    }

    private String normalizeFolderUploadDataName(String value)
    {
        String candidate = trimToNull(value);
        if (candidate == null)
        {
            return null;
        }

        String normalizedPath = normalizeExperimentUploadPath(candidate);
        String relativePath = StringUtils.removeStart(normalizedPath, "/");
        if (StringUtils.isEmpty(relativePath))
        {
            return null;
        }
        return relativePath.split("/")[0];
    }

    private MdFileStorage buildInitFileStorage(String bucket,
                                               String objectName,
                                               String originalFileName,
                                               String contentType,
                                               Long fileSize,
                                               String businessType,
                                               String businessId,
                                               Long userId,
                                               String username) {
        Date now = new Date();
        MdFileStorage fileStorage = new MdFileStorage();
        fileStorage.setBusinessType(businessType);
        fileStorage.setBusinessId(businessId);
        fileStorage.setStorageProvider(FileStorageProviderEnum.MINIO.getCode());
        fileStorage.setBucketName(bucket);
        fileStorage.setObjectName(objectName);
        fileStorage.setOriginalFileName(originalFileName);
        fileStorage.setFileExt(extractStorageFileExt(originalFileName != null ? originalFileName : objectName));
        fileStorage.setContentType(contentType);
        fileStorage.setFileSize(fileSize);
        fileStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        fileStorage.setUploadUserId(userId);
        fileStorage.setUploadUserName(username);
        fileStorage.setCreateBy(username);
        fileStorage.setCreateTime(now);
        fileStorage.setUpdateBy(username);
        fileStorage.setUpdateTime(now);
        return fileStorage;
    }

    private String extractStorageFileExt(String fileName)
    {
        if (StringUtils.isEmpty(fileName))
        {
            return "";
        }
        String normalizedName = fileName.replace("\\", "/");
        String name = normalizedName.substring(normalizedName.lastIndexOf("/") + 1);
        int dotIndex = name.lastIndexOf(".");
        if (dotIndex <= 0 || dotIndex == name.length() - 1)
        {
            return "";
        }
        return name.substring(dotIndex).toLowerCase(Locale.ROOT);
    }

    //删除数据文件
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDataInfoById(Integer id){
        if(id == null)
            return 0;
        DdataInfo ddataInfo = ddataMapper.selectDdataInfoById(id);
        if(ddataInfo == null){
            log.warn("数据不存在");
            return 0;
        }
        List<MdFileStorage> storageList = mdFileStorageMapper.selectListByBussinessId(String.valueOf(id));
        if ((storageList == null || storageList.isEmpty()) && ddataInfo.getStorageFileId() != null) {
            MdFileStorage mdFileStorage = mdFileStorageMapper.selectById(ddataInfo.getStorageFileId());
            storageList = mdFileStorage == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(mdFileStorage));
        }
        storageList = storageList == null ? new ArrayList<>() : storageList.stream()
                .filter(item -> item != null && Objects.equals(item.getUploadStatus(), FileStorageStatusEnum.BOUND.getCode()))
                .collect(Collectors.toList());
        if(storageList.isEmpty()){
            return 0;
        }
        String updateBy = NickNameUtil.getNickName();
        //逻辑删除，修改数据库状态
        storageList.forEach(mdFileStorage -> {
            mdFileStorage.setUpdateBy(updateBy);
            mdFileStorage.setUpdateTime(new Date());
            mdFileStorage.setUploadStatus(FileStorageStatusEnum.DELETED.getCode());
        });
        mdFileStorageMapper.updateFileStorgeStatus(storageList);
        List<Integer> deleteId = new ArrayList<>();
        deleteId.add(id);
        ddataMapper.deleteDdataInfos(deleteId);
        return 1;
    }

    private void uploadZipArchive(MultipartFile file, DExperimentInfo experimentInfo, String archivePath)
    {
        boolean hasUploadedEntry = false;
        String archiveParentPath = extractDirectory(archivePath);

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream()))
        {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null)
            {
                if (entry.isDirectory())
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String entryPath = buildArchiveEntryPath(archiveParentPath, entry.getName());
                if (shouldSkipExperimentPath(entryPath))
                {
                    zipInputStream.closeEntry();
                    continue;
                }

                String extension = extractExtensionName(entryPath);
                //检查文件扩展名是否为支持的类型
                assertExperimentExtension(extension, entryPath);
                storeExperimentFile(experimentInfo, entryPath, zipInputStream);
                hasUploadedEntry = true;
                zipInputStream.closeEntry();
            }
        }
        catch (IOException e)
        {
            log.error("上传ZIP档案失败: {}", e.getMessage(), e);
            throw new ServiceException("上传ZIP档案失败: " + e.getMessage());
        }

        if (!hasUploadedEntry)
        {
            log.warn("ZIP档案为空");
            throw new ServiceException("ZIP档案为空");
        }
    }

    private void storeExperimentFile(DExperimentInfo experimentInfo, String relativePath, InputStream inputStream)
            throws IOException
    {
        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        //文件名包含无效字符
        String normalizedPath = normalizeExperimentUploadPath(relativePath);
        Path projectRoot = buildProjectRootPath(projectInfo);
        Path experimentRoot = buildExperimentRootPath(projectInfo, experimentInfo);
        String storagePath = buildExperimentStoragePath(normalizedPath);
        Path targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildLockPaths(projectRoot, experimentRoot),
                buildLockPaths(targetPath)))
        {
            Path parentPath = targetPath.getParent();
            if (parentPath != null && Files.notExists(parentPath))
            {
                log.info("鍒涘缓鐩綍: {}", parentPath);
                Files.createDirectories(parentPath);
            }
            //检查文件是否存在，避免重复上传
            storagePath = resolveAvailableExperimentStoragePath(
                    experimentInfo.getExperimentId(),
                    experimentRoot,
                    storagePath
            );
            targetPath = resolveAbsoluteDataPath(experimentRoot, storagePath);

            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

            DdataInfo ddataInfo = buildExperimentUploadDataInfo(experimentInfo, normalizedPath, storagePath);
            DdataInfo oldInfo = ddataMapper.selectSameNameFile(experimentInfo.getExperimentId(), storagePath);
            if (oldInfo != null)
            {
                log.info("文件已存在，合并数据: {}", oldInfo);
                //合并数据
                mergeExistingDataInfo(ddataInfo, oldInfo);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + oldInfo.getId());
                ddataMapper.updateDdataInfo(ddataInfo);
                return;
            }

            ddataMapper.insertDdataInfo(ddataInfo);
        }
    }

    private DdataInfo buildExperimentUploadDataInfo(
            DExperimentInfo experimentInfo,
            String relativePath,
            String storagePath)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(experimentInfo.getTargetId());
        ddataInfo.setTargetType(resolveExperimentTargetType(experimentInfo));
        ddataInfo.setDataName(extractFileName(relativePath));
        ddataInfo.setDataType(resolveExperimentDataType(relativePath));
        ddataInfo.setIsSimulation(Boolean.TRUE);
        ddataInfo.setSampleFrequency(1000);
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setCreateBy(NickNameUtil.getNickName());
        return ddataInfo;
    }

    private String buildExperimentStoragePath(String relativePath)
    {
        return normalizeDataFilePath(relativePath);
    }

    private String resolveExperimentTargetType(DExperimentInfo experimentInfo)
    {
        if (experimentInfo == null || StringUtils.isEmpty(experimentInfo.getTargetId()))
        {
            return null;
        }
        if (StringUtils.isNotEmpty(experimentInfo.getTargetType()))
        {
            return experimentInfo.getTargetType();
        }
        if (dTargetInfoMapper.selectDTargetInfoByTargetId(experimentInfo.getTargetId()) == null)
        {
            return null;
        }
        return dTargetInfoMapper.selectDTargetInfoByTargetId(experimentInfo.getTargetId()).getTargetType();
    }

    private String resolveExperimentDataType(String relativePath)
    {
        String extension = extractExtensionName(relativePath);
        return StringUtils.isEmpty(extension) ? "file" : extension;
    }

    private String extractFileName(String relativePath)
    {
        String normalizedPath = normalizeDataFilePath(relativePath);
        return normalizedPath.substring(normalizedPath.lastIndexOf('/') + 1);
    }

    private void assertExperimentExtension(String extension, String relativePath)
    {
        if (StringUtils.isEmpty(extension) || !EXPERIMENT_ALLOWED_EXTENSIONS.contains(extension))
        {
            log.warn("文件扩展名不支持: {} {}", extension, relativePath);
            throw new ServiceException("文件扩展名不支持: " + extension + " " + relativePath);
        }
    }

    //提取文件扩展名
    private String extractExtensionName(String path)
    {
        String suffix = extractSuffix(path);
        return StringUtils.isEmpty(suffix) ? "" : suffix.substring(1).toLowerCase(Locale.ROOT);
    }

    private String buildArchiveEntryPath(String archiveParentPath, String entryName)
    {
        String normalizedEntryPath = normalizeExperimentUploadPath(entryName);
        if ("/".equals(archiveParentPath))
        {
            return normalizedEntryPath;
        }
        return normalizeExperimentUploadPath(
                archiveParentPath + "/" + StringUtils.removeStart(normalizedEntryPath, "/"));
    }

    private boolean shouldSkipExperimentPath(String relativePath)
    {
        String normalizedPath = StringUtils.removeStart(normalizeDataFilePath(relativePath), "/");
        String[] segments = normalizedPath.split("/");
        for (String segment : segments)
        {
            String current = segment == null ? "" : segment.trim();
            if (current.isEmpty())
            {
                continue;
            }
            if ("__MACOSX".equalsIgnoreCase(current) || ".DS_Store".equalsIgnoreCase(current)
                    || "Thumbs.db".equalsIgnoreCase(current) || current.startsWith("._"))
            {
                return true;
            }
        }
        return false;
    }

    private String normalizeExperimentUploadPath(String rawPath)
    {
        if (StringUtils.isEmpty(rawPath))
        {
            throw new ServiceException("上传路径不能为空");
        }

        String candidate = rawPath.trim();
        if (candidate.matches("^[a-zA-Z]:[\\\\/].*"))
        {
            int separatorIndex = Math.max(candidate.lastIndexOf('/'), candidate.lastIndexOf('\\'));
            candidate = separatorIndex >= 0 ? candidate.substring(separatorIndex + 1) : candidate;
        }

        candidate = candidate.replace("\\", "/");
        while (candidate.startsWith("/"))
        {
            candidate = candidate.substring(1);
        }

        List<String> parts = new ArrayList<>();
        for (String segment : candidate.split("/"))
        {
            String current = segment == null ? "" : segment.trim();
            if (current.isEmpty() || ".".equals(current))
            {
                continue;
            }
            if ("..".equals(current))
            {
                throw new ServiceException("上传路径不能包含 .. 组件");
            }
            if (containsIllegalWindowsChar(current))
            {
                throw new ServiceException("上传路径不能包含非法字符: " + current);
            }
            parts.add(current);
        }

        if (parts.isEmpty())
        {
            throw new ServiceException("上传路径不能为空");
        }
        return "/" + String.join("/", parts);
    }

    private boolean containsIllegalWindowsChar(String name)
    {
        return name.contains(":") || name.contains("*") || name.contains("?") || name.contains("\"")
                || name.contains("<") || name.contains(">") || name.contains("|");
    }

    @Override
    public DdataInfo selectDdataInfoByDdataId(Integer id)
    {
        String cacheKey = CacheConstants.DATA_INFO_KEY + id;
        DdataInfo cachedDdataInfo = redisCache.getCacheObject(cacheKey);
        if (cachedDdataInfo != null)
        {
            return cachedDdataInfo;
        }

        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setId(id);
        List<DdataInfo> records = ddataMapper.selectDdataInfoList(ddataInfo);
        if (records == null || records.isEmpty())
        {
            throw new ServiceException("数据不存在");
        }

        DdataInfo result = records.get(0);
        result.setFullPath("./data" + BuildDataFilePath(result) + "/" + result.getDataName());
        redisCache.setCacheObject(cacheKey, result, 30, TimeUnit.MINUTES);
        return result;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int renameDataName(List<DdataInfo> ddataInfos){
        // check if any data name already has the project name and experiment name
        ddataInfos.forEach(item -> {
            if(RegexUtils.findFirst(item.getDataName(),"_" + item.getProjectName() + "_" +item.getExperimentName()) != null){
                return;
            }
            String baseName = extractBaseName("/" + item.getDataName());
            String extension = extractExtensionName("/" + item.getDataName());
            item.setDataName(baseName + "_" + item.getProjectName() + "_" + item.getExperimentName() + "." + extension);
        });
        try{
            ddataMapper.updateDdataInfos(ddataInfos);
            for(DdataInfo ddataInfo:ddataInfos){
                log.info("重命名数据文件: {}", ddataInfo);
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + ddataInfo.getId());
            }
        } catch (Exception e) {
            log.error("重命名数据文件失败", e);
            throw new ServiceException("重命名数据文件失败");
        }
        return 1;
    }

    private DdataInfo buildBusinessPathImportDataInfo(
            DdataInfo source,
            DExperimentInfo experimentInfo,
            String dataFilePath)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(StringUtils.isNotEmpty(source.getTargetId())
                ? normalizeOptionalText(source.getTargetId())
                : experimentInfo.getTargetId());
        ddataInfo.setTargetType(StringUtils.isNotEmpty(source.getTargetType())
                ? normalizeOptionalText(source.getTargetType())
                : resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(normalizeOptionalText(source.getTargetCategory()));
        ddataInfo.setDataName(normalizeOptionalText(source.getDataName()));
        ddataInfo.setDataType(normalizeOptionalText(source.getDataType()));
        ddataInfo.setDeviceId(normalizeOptionalText(source.getDeviceId()));
        ddataInfo.setDeviceInfo(normalizeOptionalText(source.getDeviceInfo()));
        ddataInfo.setSampleFrequency(source.getSampleFrequency() == null || source.getSampleFrequency() <= 0
                ? 1000
                : source.getSampleFrequency());
        ddataInfo.setWorkStatus(StringUtils.isNotEmpty(source.getWorkStatus())
                ? normalizeOptionalText(source.getWorkStatus())
                : "completed");
        ddataInfo.setExtAttr(source.getExtAttr());
        ddataInfo.setIsSimulation(source.getIsSimulation() == null ? Boolean.TRUE : source.getIsSimulation());
        ddataInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? normalizeOptionalText(source.getCreateBy())
                : NickNameUtil.getNickName());
        ddataInfo.setCreateTime(source.getCreateTime());
        return ddataInfo;
    }

    private DProjectInfo ensureTransportProject(String projectName)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectSameNameProject(projectName);
        if (projectInfo != null)
        {
            log.warn("项目已存在: {}", projectName);
            return projectInfo;
        }

        Path projectRoot = Paths.get(
                profile,
                StringUtils.removeStart("/" + projectName, "/")
        ).normalize();
        DProjectInfo newProjectInfo = new DProjectInfo();
        newProjectInfo.setProjectName(projectName);
        newProjectInfo.setCreateBy(NickNameUtil.getNickName());
        newProjectInfo.setPath("/" + projectName);
        if (Files.exists(projectRoot))
        {
            log.warn("创建项目目录: {}", projectRoot);
            dProjectInfoMapper.insertDProjectInfo(newProjectInfo);
        }
        else
        {
            projectInfoService.insertDProjectInfo(newProjectInfo);
        }

        DProjectInfo createdProjectInfo = dProjectInfoMapper.selectSameNameProject(projectName);
        if (createdProjectInfo == null)
        {
            log.warn("项目创建失败: {}", projectName);
            throw new ServiceException("项目创建失败");
        }
        return createdProjectInfo;
    }

    private DExperimentInfo ensureTransportExperiment(
            DProjectInfo projectInfo,
            String experimentName,
            DdataInfo source)
    {
        DExperimentInfo experimentInfo =
                dExperimentInfoMapper.selectSamePathExperiment(experimentName, projectInfo.getProjectId());
        if (experimentInfo != null)
        {
            return experimentInfo;
        }

        Path experimentRoot = Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/"),
                StringUtils.removeStart("/" + experimentName, "/")
        ).normalize();
        DExperimentInfo newExperimentInfo = new DExperimentInfo();
        newExperimentInfo.setExperimentId(UUID.randomUUID().toString());
        newExperimentInfo.setTargetId(source.getTargetId());
        newExperimentInfo.setExperimentName(experimentName);
        newExperimentInfo.setProjectId(projectInfo.getProjectId());
        newExperimentInfo.setStartTime(new Date());
        newExperimentInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? source.getCreateBy().trim()
                : NickNameUtil.getNickName());
        newExperimentInfo.setPath("/" + experimentName);
        if (Files.exists(experimentRoot))
        {
            log.warn("创建实验目录: {}", experimentRoot);
            dExperimentInfoMapper.insertDExperimentInfo(newExperimentInfo);
        }
        else
        {
            dExperimentInfoService.insertDExperimentInfo(newExperimentInfo);
        }

        DExperimentInfo createdExperimentInfo =
                dExperimentInfoMapper.selectSamePathExperiment(experimentName, projectInfo.getProjectId());
        if (createdExperimentInfo == null)
        {
            log.warn("实验创建失败: {}", experimentName);
            throw new ServiceException("实验创建失败");
        }
        return createdExperimentInfo;
    }

    private DdataInfo buildTransportDataInfo(
            DdataInfo source,
            DExperimentInfo experimentInfo,
            String dataFilePath,
            String sourceFileName)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(StringUtils.isNotEmpty(source.getTargetId())
                ? source.getTargetId().trim()
                : experimentInfo.getTargetId());
        ddataInfo.setTargetType(StringUtils.isNotEmpty(source.getTargetType())
                ? source.getTargetType().trim()
                : resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(source.getTargetCategory());
        ddataInfo.setDataName(StringUtils.isNotEmpty(source.getDataName())
                ? source.getDataName().trim()
                : sourceFileName);
        ddataInfo.setDataType(StringUtils.isNotEmpty(source.getDataType())
                ? source.getDataType().trim()
                : resolveExperimentDataType(dataFilePath));
        ddataInfo.setDeviceId(source.getDeviceId());
        ddataInfo.setDeviceInfo(source.getDeviceInfo());
        ddataInfo.setSampleFrequency(source.getSampleFrequency() == null || source.getSampleFrequency() <= 0
                ? 1000
                : source.getSampleFrequency());
        ddataInfo.setWorkStatus(StringUtils.isNotEmpty(source.getWorkStatus())
                ? source.getWorkStatus().trim()
                : "completed");
        ddataInfo.setExtAttr(source.getExtAttr());
        ddataInfo.setIsSimulation(source.getIsSimulation() == null ? Boolean.TRUE : source.getIsSimulation());
        ddataInfo.setCreateBy(StringUtils.isNotEmpty(source.getCreateBy())
                ? source.getCreateBy().trim()
                : NickNameUtil.getNickName());
        return ddataInfo;
    }


    private DdataInfo buildBusinessImportDataInfo(
            DdataInfo template,
            String relativePath,
            String storagePath,
            boolean allowCustomDataName)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(template.getExperimentId());
        ddataInfo.setTargetId(template.getTargetId());
        ddataInfo.setTargetType(resolveBusinessImportTargetType(template));
        ddataInfo.setTargetCategory(template.getTargetCategory());
        ddataInfo.setDataName(resolveBusinessImportDataName(template, relativePath, allowCustomDataName));
        ddataInfo.setDataType(resolveBusinessImportDataType(template, relativePath));
        ddataInfo.setIsSimulation(resolveBusinessImportSimulationFlag(template));
        ddataInfo.setSampleFrequency(1000);
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setExtAttr(template.getExtAttr());
        ddataInfo.setCreateBy(NickNameUtil.getNickName());
        return ddataInfo;
    }

    private String resolveBusinessImportDataName(DdataInfo template, String relativePath, boolean allowCustomDataName)
    {
        if (allowCustomDataName && StringUtils.isNotEmpty(template.getDataName()))
        {
            return template.getDataName().trim();
        }
        return extractFileName(relativePath);
    }

    private String resolveBusinessImportDataType(DdataInfo template, String relativePath)
    {
        if (StringUtils.isNotEmpty(template.getDataType()))
        {
            return template.getDataType().trim();
        }
        return resolveExperimentDataType(relativePath);
    }

    private Boolean resolveBusinessImportSimulationFlag(DdataInfo template)
    {
        return template.getIsSimulation() == null ? Boolean.TRUE : template.getIsSimulation();
    }



    private String resolveBusinessImportTargetType(DdataInfo ddataInfo)
    {
        if (StringUtils.isNotEmpty(ddataInfo.getTargetType()))
        {
            return ddataInfo.getTargetType();
        }
        if (StringUtils.isEmpty(ddataInfo.getTargetId()))
        {
            return null;
        }

        if (dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()) == null)
        {
            throw new ServiceException("\u76EE\u6807\u4E0D\u5B58\u5728");
        }
        return dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()).getTargetType();
    }

    private void bindStorageFileToBusinessData(MdFileStorage fileStorage, DdataInfo ddataInfo)
    {
        fileStorage.setBusinessType(MinioBusinessTypeEnum.DATA_RELATION.getCode());
        fileStorage.setBusinessId(String.valueOf(ddataInfo.getId()));
        fileStorage.setUploadStatus(FileStorageStatusEnum.BOUND.getCode());
        fileStorage.setRemark("BOUND md_data_relation#" + ddataInfo.getId());
        fileStorage.setUpdateBy(resolveStorageUpdateUser(ddataInfo));
        fileStorage.setUpdateTime(new Date());
        mdFileStorageMapper.updateMdFileStorage(fileStorage);
    }

    @Override
    public void uploadFiles(List<MultipartFile> files, String experimentId)
    {
        if (files == null || files.isEmpty())
        {
            return;
        }

        DExperimentInfo experimentInfo = requireExperiment(experimentId);
        for (MultipartFile file : files)
        {
            if (file == null)
            {
                continue;
            }

            String uploadPath = normalizeExperimentUploadPath(file.getOriginalFilename());
            if (shouldSkipExperimentPath(uploadPath))
            {
                continue;
            }

            String extension = extractExtensionName(uploadPath);
            if ("zip".equals(extension))
            {
                uploadZipArchive(file, experimentInfo, uploadPath);
                continue;
            }

            assertExperimentExtension(extension, uploadPath);
            try (InputStream inputStream = file.getInputStream())
            {
                storeExperimentFile(experimentInfo, uploadPath, inputStream);
            }
            catch (IOException e)
            {
                log.warn("上传文件失败: {}", uploadPath, e);
                throw new ServiceException("上传文件失败: " + e.getMessage());
            }
        }
    }

    /**
     *
     * @param experimentId
     * @param mdFileStorageList 存储的文件数据库集合
     * @param sourceFileNames 源文件名称
     * @param createBy
     * @param taskDataGroups
     */

    @Override
    public void syncSimulationResultFiles(
            String experimentId,
            List<MdFileStorage> mdFileStorageList,
            List<String> sourceFileNames,
            String createBy,
            String targetCategory, List<TaskDataGroup> taskDataGroups)
    {
        if (StringUtils.isEmpty(experimentId) || mdFileStorageList.isEmpty())
        {
            return;
        }
        int index = 0;

        DExperimentInfo experimentInfo = requireExperiment(experimentId);

        for (MdFileStorage mdFileStorage : mdFileStorageList)
        {
            if (mdFileStorage == null)
            {
                index++;
                continue;
            }
            DdataInfo ddataInfo = buildSimulationResultDataInfo(
                    experimentInfo,
                    sourceFileNames.get(index),
                    createBy,taskDataGroups.get(index));
            ddataInfo.setStorageFileId(mdFileStorage.getId());
            ddataMapper.insertDdataInfo(ddataInfo);
            log.info("dataInfoId = {}",ddataInfo.getId());
            //插入文件存储数据信息
            mdFileStorage.setBusinessId(String.valueOf(ddataInfo.getId()));
            mdFileStorage.setUploadStatus(FileStorageStatusEnum.BOUND.getCode());
            mdFileStorage.setRemark("BOUND md_data_relation#" + ddataInfo.getId());
            mdFileStorage.setUpdateBy(createBy);
            mdFileStorage.setUpdateTime(new Date());
            mdFileStorageMapper.updateMdFileStorage(mdFileStorage);
            index++;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer updateDdataInfo(DdataInfo ddataInfo)
    {
        if (ddataInfo == null || ddataInfo.getId() == null)
        {
            log.warn("数据ID不能为空");
            return 0;
        }

        DdataInfo oldDataInfo = selectDataInfoRecord(ddataInfo.getId());
        if (oldDataInfo == null)
        {
            log.warn("数据不存在: {}", ddataInfo.getId());
            return 0;
        }
        ddataInfo.setUpdateBy(NickNameUtil.getNickName());
        ddataInfo.setUpdateTime(new Date());
        redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + ddataInfo.getId());
        return ddataMapper.updateDdataInfo(ddataInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer deleteDdataInfos(List<Integer> ids)
    {
        List<Integer> deleteDataIds = new ArrayList<>();
        List<MdFileStorage> mdFileStorgeList = new ArrayList<>();
        for (Integer id : ids)
        {
            DdataInfo dataInfo = selectDataInfoRecord(id);
            if (dataInfo == null)
            {
                redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + id);
                continue;
            }
            deleteDataIds.add(id);
            List<MdFileStorage> storageList = mdFileStorageMapper.selectListByBussinessId(String.valueOf(id));
            if ((storageList == null || storageList.isEmpty()) && dataInfo.getStorageFileId() != null)
            {
                MdFileStorage mdFileStorage = mdFileStorageMapper.selectById(dataInfo.getStorageFileId());
                storageList = mdFileStorage == null ? new ArrayList<>() : new ArrayList<>(Collections.singletonList(mdFileStorage));
            }
            if (storageList != null)
            {
                storageList.stream()
                        .filter(Objects::nonNull)
                        .forEach(mdFileStorage -> {
                            mdFileStorage.setUploadStatus(FileStorageStatusEnum.DELETED.getCode());
                            mdFileStorage.setUpdateBy(NickNameUtil.getNickName());
                            mdFileStorage.setUpdateTime(new Date());
                            mdFileStorgeList.add(mdFileStorage);
                        });
            }
            redisCache.deleteObject(CacheConstants.DATA_INFO_KEY + id);
        }
        ddataMapper.deleteDdataInfos(deleteDataIds);
        if (!mdFileStorgeList.isEmpty())
        {
            mdFileStorageMapper.updateFileStorgeStatus(mdFileStorgeList);
        }
        return deleteDataIds.size();
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int backupDataById(Integer id){
        DdataInfo cachedDataInfo = redisCache.getCacheObject(CacheConstants.DATA_INFO_KEY + id);
        DdataInfo ddataInfo = cachedDataInfo != null ? cachedDataInfo : ddataMapper.selectDdataInfoById(id);
        if (ddataInfo == null || StringUtils.isEmpty(ddataInfo.getExperimentId())) {
            log.warn("备份失败，数据不存在或缺少必要信息，id={}", id);
            return 0;
        }
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        if (experimentInfo == null || experimentInfo.getProjectId() == null) {
            log.warn("备份失败，试验信息不存在，id={}, experimentId={}", id, ddataInfo.getExperimentId());
            return 0;
        }
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(experimentInfo.getProjectId());
        if(projectInfo == null){
            log.warn("备份失败，项目信息不存在，id={}, projectId={}", id, experimentInfo.getProjectId());
            return 0;
        }
        ddataInfo.setExperimentName(experimentInfo.getExperimentName());
        ddataInfo.setProjectId(projectInfo.getProjectId());
        ddataInfo.setProjectName(projectInfo.getProjectName());
            //已经备份后除非还原否则不可再备份
        if(isDataFileHasBackup(id)){
            log.warn("备份失败，该数据已经备份,id={}", id);
            return 0;
        }
        BackupData backupData = transferDataToBackupData(ddataInfo);
        if (backupData == null || backDataMapper.insertBackupData(backupData) <= 0) {
            log.warn("备份失败，备份记录入库失败，id={}", id);
            return 0;
        }
        return 1;
    }
    //数据文件是否备分
    private Boolean isDataFileHasBackup(Integer dataId){
        if(backDataMapper.selectBackupDataByDataId(dataId)!=null){
            return true;
        }
        return false;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String restoreDataFile(Integer BackUpDataId){
        try {
            BackupData backupData = backDataMapper.selectBackupDataById(BackUpDataId);
            if (backupData == null || Integer.valueOf(1).equals(backupData.getIsRestored())) {
                log.warn("备份记录不存在或已还原");
                return "备份记录不存在或已还原";
            }
            List<MdFileStorage> restoreStorageList = mdFileStorageMapper.selectListByBussinessId(backupData.getDataInfoId().toString());
            if(restoreStorageList == null || restoreStorageList.isEmpty()){
                return "文件无法恢复";
            }
            if(restoreStorageList.stream().anyMatch(item -> item == null || !FileStorageStatusEnum.DELETED.getCode().equals(item.getUploadStatus()))){
                return "文件尚未删除";
            }
            MdFileStorage mdFileStorage = restoreStorageList.get(0);
            if (StringUtils.isEmpty(backupData.getProjectName())
                    || StringUtils.isEmpty(backupData.getExperimentName())
                    || StringUtils.isEmpty(backupData.getExperimentId())) {
                log.warn("备份记录缺少必要信息");
                return "备份记录缺少必要信息";
            }

            DProjectInfo currentProjectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(backupData.getProjectId());
            if (currentProjectInfo == null) {
                //项目已被删除
                currentProjectInfo = dProjectInfoMapper.selectSameNameProject(backupData.getProjectName());
            }
            if (currentProjectInfo == null) {
                //项目被删除且不存在重名项目
                DProjectInfo dProjectInfo = new DProjectInfo();
                dProjectInfo.setProjectName(backupData.getProjectName());
                dProjectInfo.setCreateBy(NickNameUtil.getNickName());
                dProjectInfo.setPath("/" + backupData.getProjectName());
                projectInfoService.insertDProjectInfo(dProjectInfo);
                currentProjectInfo = dProjectInfo;
            }
            if (currentProjectInfo.getProjectId() == null) {
                return "项目恢复失败";
            }
            backupData.setProjectId(currentProjectInfo.getProjectId());

            DExperimentInfo currentExperimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(backupData.getExperimentId());
            if (currentExperimentInfo == null
                    || !Objects.equals(currentExperimentInfo.getProjectId(), currentProjectInfo.getProjectId())) {
                currentExperimentInfo = dExperimentInfoMapper.selectSamePathExperiment(
                        backupData.getExperimentName(),
                        currentProjectInfo.getProjectId()
                );
            }
            if (currentExperimentInfo == null) {
                DExperimentInfo dExperimentInfo = new DExperimentInfo();
                dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
                dExperimentInfo.setTargetId(backupData.getTargetId());
                dExperimentInfo.setExperimentName(backupData.getExperimentName());
                dExperimentInfo.setProjectId(currentProjectInfo.getProjectId());
                dExperimentInfo.setStartTime(new Date());
                dExperimentInfo.setCreateBy(NickNameUtil.getNickName());
                dExperimentInfo.setPath("/" + backupData.getExperimentName());
                String createResult = dExperimentInfoService.insertDExperimentInfo(dExperimentInfo);
                if (StringUtils.isNotEmpty(createResult)) {
                    return createResult;
                }
                currentExperimentInfo = dExperimentInfoMapper.selectSamePathExperiment(
                        backupData.getExperimentName(),
                        currentProjectInfo.getProjectId()
                );
            }
            if (currentExperimentInfo == null || currentExperimentInfo.getExperimentId() == null) {
                return "试验恢复失败";
            }

            backupData.setExperimentId(currentExperimentInfo.getExperimentId());
            backDataMapper.updateBackupData(backupData);

            if (ddataMapper.selectDdataInfoById(backupData.getDataInfoId()) != null) {
                return "数据已存在";
            }

            DdataInfo ddataInfo = new DdataInfo();
            ddataInfo.setProjectId(currentProjectInfo.getProjectId());
            ddataInfo.setProjectName(currentProjectInfo.getProjectName());
            ddataInfo.setExperimentId(currentExperimentInfo.getExperimentId());
            ddataInfo.setExperimentName(currentExperimentInfo.getExperimentName());
            ddataInfo.setStorageFileId(mdFileStorage.getId());
            ddataInfo.setTargetId(backupData.getTargetId());
            ddataInfo.setTargetType(backupData.getTargetType());
            ddataInfo.setTargetCategory(backupData.getTargetCategory());
            String restoredDataName = trimToNull(backupData.getDataName());

            ddataInfo.setDataName(restoredDataName);
            ddataInfo.setDataType(backupData.getDataType());
            ddataInfo.setDeviceId(backupData.getDeviceId());
            ddataInfo.setDeviceInfo(backupData.getDeviceInfo());
            ddataInfo.setSampleFrequency(backupData.getSampleFrequency());
            ddataInfo.setWorkStatus(backupData.getWorkStatus());
            ddataInfo.setExtAttr(backupData.getExtAttr());
            ddataInfo.setCreateBy(NickNameUtil.getNickName());
            if (backupData.getIsSimulation() != null) {
                ddataInfo.setIsSimulation(backupData.getIsSimulation() == 1);
            }
            ddataMapper.insertDdataInfo(ddataInfo);

            backupData.setRestoredDataInfoId(ddataInfo.getId());
            backupData.setRestoreTime(new Date());
            backupData.setRestoreBy(NickNameUtil.getNickName());
            backupData.setIsRestored(1);
            backDataMapper.updateBackupData(backupData);
            for (MdFileStorage storage : restoreStorageList) {
                storage.setBusinessId(ddataInfo.getId().toString());
                storage.setUpdateTime(new Date());
                storage.setUpdateBy(NickNameUtil.getNickName());
                storage.setUploadStatus(FileStorageStatusEnum.BOUND.getCode());
                mdFileStorageMapper.updateMdFileStorage(storage);
            }
            ddataMapper.updateStorageFileId(ddataInfo.getId(),mdFileStorage.getId());
            return null;
        } catch (Exception e) {
            log.warn("备份数据失败，backupId={}", BackUpDataId, e);
            throw e;
        }
    }

    @Override
    public List<BackupData> selectBackupDataList(BackupData backupData)
    {
        return backDataMapper.selectBackupDataList(backupData);
    }

    private BackupData transferDataToBackupData(DdataInfo ddataInfo) {
        if (ddataInfo == null) {
            return null;
        }

        BackupData backupData = new BackupData();
        backupData.setDataInfoId(ddataInfo.getId());

        backupData.setTargetId(ddataInfo.getTargetId());
        backupData.setTargetType(ddataInfo.getTargetType());
        backupData.setTargetCategory(ddataInfo.getTargetCategory());
        backupData.setExperimentId(ddataInfo.getExperimentId());
        backupData.setExperimentName(ddataInfo.getExperimentName());
        backupData.setProjectId(ddataInfo.getProjectId());
        backupData.setProjectName(ddataInfo.getProjectName());
        backupData.setDataName(ddataInfo.getDataName());
        backupData.setDataType(ddataInfo.getDataType());
        backupData.setDeviceId(ddataInfo.getDeviceId());
        backupData.setDeviceInfo(ddataInfo.getDeviceInfo());

        backupData.setSampleFrequency(ddataInfo.getSampleFrequency());
        backupData.setWorkStatus(ddataInfo.getWorkStatus());
        backupData.setExtAttr(ddataInfo.getExtAttr());
        backupData.setRemark(ddataInfo.getRemark());
        if (ddataInfo.getIsSimulation() != null) {
            backupData.setIsSimulation(Boolean.TRUE.equals(ddataInfo.getIsSimulation()) ? 1 : 0);
        }
        String name = NickNameUtil.getNickName();
        backupData.setDeleteBy(name);
        backupData.setDeleteTime(new Date());
        backupData.setCreateBy(name);
        backupData.setCreateTime(new Date());

        backupData.setIsRestored(0);

        return backupData;
    }

    @Override
    public List<Map<String, Object>> getMovePathTree()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        List<DProjectInfo> projects = dProjectInfoMapper.selectDProjectInfoList(new DProjectInfo());
        List<DExperimentInfo> experiments = dExperimentInfoMapper.selectDExperimentInfoList(new DExperimentInfo());

        Map<Long, List<DExperimentInfo>> experimentsByProject = experiments.stream()
                .filter(item -> item.getProjectId() != null)
                .collect(Collectors.groupingBy(DExperimentInfo::getProjectId));

        projects.sort(Comparator.comparing(item -> item.getProjectName() == null ? "" : item.getProjectName()));

        for (DProjectInfo project : projects)
        {
            Map<String, Object> projectNode = new HashMap<>();
            projectNode.put("id", "project-" + project.getProjectId());
            projectNode.put("label", project.getProjectName());
            projectNode.put("type", "project");
            projectNode.put("disabled", true);

            List<Map<String, Object>> experimentNodes = new ArrayList<>();
            List<DExperimentInfo> projectExperiments =
                    experimentsByProject.getOrDefault(project.getProjectId(), new ArrayList<>());
            projectExperiments.sort(
                    Comparator.comparing(item -> item.getExperimentName() == null ? "" : item.getExperimentName()));

            for (DExperimentInfo experiment : projectExperiments)
            {
                Map<String, Object> experimentNode = new HashMap<>();
                experimentNode.put("id", "experiment-" + experiment.getExperimentId());
                experimentNode.put("label", experiment.getExperimentName());
                experimentNode.put("type", "experiment");
                experimentNode.put("disabled", false);
                experimentNode.put("projectName", project.getProjectName());
                experimentNode.put("experimentName", experiment.getExperimentName());
                experimentNode.put("experimentId", experiment.getExperimentId());
                experimentNode.put("relativePath", "/");

                Path experimentRoot = buildExperimentRootPath(project, experiment);
                experimentNode.put(
                        "children",
                        listSubDirectories(
                                experimentRoot,
                                experimentRoot,
                                experiment.getExperimentId(),
                                project.getProjectName(),
                                experiment.getExperimentName()
                        )
                );
                experimentNodes.add(experimentNode);
            }

            projectNode.put("children", experimentNodes);
            result.add(projectNode);
        }
        return result;
    }

    private String BuildDataFilePath(DdataInfo ddataInfo)
    {
        DExperimentInfo experimentInfo = requireExperiment(ddataInfo.getExperimentId());
        String projectPath = requireProject(experimentInfo.getProjectId()).getPath();
        return projectPath + experimentInfo.getPath();
    }

    private DdataInfo selectDataInfoRecord(Integer id)
    {
        DdataInfo query = new DdataInfo();
        query.setId(id);
        List<DdataInfo> records = ddataMapper.selectDdataInfoList(query);
        return records == null || records.isEmpty() ? null : records.get(0);
    }

    private DExperimentInfo requireExperiment(String experimentId)
    {
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (experimentInfo == null)
        {
            throw new ServiceException("实验不存在");
        }
        return experimentInfo;
    }

    private DProjectInfo requireProject(Long projectId)
    {
        DProjectInfo projectInfo = dProjectInfoMapper.selectDProjectInfoByProjectId(projectId);
        if (projectInfo == null)
        {
            throw new ServiceException("项目不存在");
        }
        return projectInfo;
    }

    private List<Path> buildLockPaths(Path... paths)
    {
        List<Path> result = new ArrayList<>();
        if (paths == null)
        {
            return result;
        }
        for (Path path : paths)
        {
            if (path != null)
            {
                result.add(path);
            }
        }
        return result;
    }

    private List<Integer> buildSingleIdList(Integer id)
    {
        List<Integer> ids = new ArrayList<>();
        ids.add(id);
        return ids;
    }

    private String buildImportedDataFilePath(String originalFilename)
    {
        String normalizedOriginalPath = normalizeDataFilePath("/" + originalFilename);
        String baseName = extractBaseName(normalizedOriginalPath);
        String suffix = extractSuffix(normalizedOriginalPath);
        return buildDataFilePath("/", baseName ,suffix);
    }

    private String resolveAvailableExperimentStoragePath(String experimentId, Path experimentRoot, String storagePath)
    {
        String normalizedStoragePath = normalizeDataFilePath(storagePath);
        String directory = extractDirectory(normalizedStoragePath);
        String baseName = extractBaseName(normalizedStoragePath);
        String suffix = extractSuffix(normalizedStoragePath);
        String candidatePath = normalizedStoragePath;
        int suffixIndex = 1;

        while (hasExperimentStoragePathConflict(experimentId, experimentRoot, candidatePath))
        {
            candidatePath = buildDataFilePath(directory, baseName + "(" + suffixIndex + ")", suffix);
            suffixIndex++;
        }
        return candidatePath;
    }

    private boolean hasExperimentStoragePathConflict(String experimentId, Path experimentRoot, String storagePath)
    {
        String normalizedStoragePath = normalizeDataFilePath(storagePath);
        return ddataMapper.selectSameNameFile(experimentId, normalizedStoragePath) != null
                || Files.exists(resolveAbsoluteDataPath(experimentRoot, normalizedStoragePath));
    }

    private DdataInfo buildSimulationResultDataInfo(
            DExperimentInfo experimentInfo,
            String sourceFileName,
            String createBy,TaskDataGroup taskDataGroup)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(experimentInfo.getExperimentId());
        ddataInfo.setTargetId(experimentInfo.getTargetId());
        ddataInfo.setTargetType(resolveExperimentTargetType(experimentInfo));
        ddataInfo.setTargetCategory(taskDataGroup.getGroupName());
        ddataInfo.setDataName(sourceFileName);
        ddataInfo.setDataType(taskDataGroup.getGroupName());
        ddataInfo.setSampleFrequency(taskDataGroup.getFrequencyHz().intValueExact());
        ddataInfo.setDeviceId(null);
        ddataInfo.setDeviceInfo(null);
        ddataInfo.setWorkStatus("completed");
        ddataInfo.setIsSimulation(taskDataGroup.getIsSimulation());
        ddataInfo.setCreateBy(resolveSimulationCreateBy(createBy, experimentInfo));
        return ddataInfo;
    }

    private void mergeExistingDataInfo(DdataInfo ddataInfo, DdataInfo oldInfo)
    {
        ddataInfo.setId(oldInfo.getId());
        if (ddataInfo.getTargetId() == null)
        {
            ddataInfo.setTargetId(oldInfo.getTargetId());
        }
        if (ddataInfo.getTargetType() == null)
        {
            ddataInfo.setTargetType(oldInfo.getTargetType());
        }
        if (ddataInfo.getTargetCategory() == null)
        {
            ddataInfo.setTargetCategory(oldInfo.getTargetCategory());
        }
        if (ddataInfo.getSampleFrequency() == null)
        {
            ddataInfo.setSampleFrequency(oldInfo.getSampleFrequency());
        }
        if (ddataInfo.getDeviceId() == null)
        {
            ddataInfo.setDeviceId(oldInfo.getDeviceId());
        }
        if (ddataInfo.getDeviceInfo() == null)
        {
            ddataInfo.setDeviceInfo(oldInfo.getDeviceInfo());
        }
        if (ddataInfo.getWorkStatus() == null)
        {
            ddataInfo.setWorkStatus(oldInfo.getWorkStatus());
        }
        if (ddataInfo.getExtAttr() == null)
        {
            ddataInfo.setExtAttr(oldInfo.getExtAttr());
        }
        if (ddataInfo.getIsSimulation() == null)
        {
            ddataInfo.setIsSimulation(oldInfo.getIsSimulation());
        }
    }

    private Integer resolveSimulationSampleFrequency(Integer sampleFrequency)
    {
        if (sampleFrequency == null || sampleFrequency <= 0)
        {
            return 1000;
        }
        return sampleFrequency;
    }

    private String resolveSimulationCreateBy(String createBy, DExperimentInfo experimentInfo)
    {
        if (StringUtils.isNotEmpty(createBy))
        {
            return createBy.trim();
        }
        if (experimentInfo != null && StringUtils.isNotEmpty(experimentInfo.getCreateBy()))
        {
            return experimentInfo.getCreateBy().trim();
        }
        return "system";
    }

    private String resolveStorageUpdateUser(DdataInfo ddataInfo)
    {
        if (ddataInfo != null && StringUtils.isNotEmpty(ddataInfo.getCreateBy()))
        {
            return ddataInfo.getCreateBy().trim();
        }
        return NickNameUtil.getNickName();
    }

    private String normalizeOptionalText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeFileName(String fileName, String fallbackDataPath)
    {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isEmpty())
        {
            normalized = extractBaseName(fallbackDataPath);
        }
        if (!normalized.matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            log.info("文件名包含无效字符: {}", normalized);
            throw new ServiceException("文件名包含无效字符");
        }
        return normalized;
    }

    private String normalizeDataFilePath(String dataFilePath)
    {
        if (StringUtils.isEmpty(dataFilePath))
        {
            return "/";
        }

        String normalized = dataFilePath.trim().replace("\\", "/");
        if (!normalized.startsWith("/"))
        {
            normalized = "/" + normalized;
        }
        while (normalized.contains("//"))
        {
            normalized = normalized.replace("//", "/");
        }
        if (normalized.contains(".."))
        {
            throw new ServiceException("鏂囦欢璺緞鏃犳晥");
        }
        return normalized;
    }

    private String extractSuffix(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private String extractBaseName(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String extractDirectory(String dataFilePath)
    {
        String normalized = normalizeDataFilePath(dataFilePath);
        int index = normalized.lastIndexOf('/');
        if (index <= 0)
        {
            return "/";
        }
        return normalized.substring(0, index);
    }

    private String buildDataFilePath(String directory, String fileName, String suffix)
    {
        String normalizedDir = normalizeDataFilePath(StringUtils.isEmpty(directory) ? "/" : directory);
        if (normalizedDir.length() > 1 && normalizedDir.endsWith("/"))
        {
            normalizedDir = normalizedDir.substring(0, normalizedDir.length() - 1);
        }
        String safeSuffix = suffix == null ? "" : suffix;
        return "/".equals(normalizedDir)
                ? "/" + fileName + safeSuffix
                : normalizedDir + "/" + fileName + safeSuffix;
    }

    private Path buildProjectRootPath(DProjectInfo projectInfo)
    {
        return Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/")
        ).normalize();
    }

    private Path buildExperimentRootPath(DProjectInfo projectInfo, DExperimentInfo experimentInfo)
    {
        return Paths.get(
                profile,
                StringUtils.removeStart(projectInfo.getPath(), "/"),
                StringUtils.removeStart(experimentInfo.getPath(), "/")
        ).normalize();
    }

    private Path resolveAbsoluteDataPath(Path experimentRoot, String dataFilePath)
    {
        String relativePath = StringUtils.removeStart(normalizeDataFilePath(dataFilePath), "/");
        Path resolved = experimentRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(experimentRoot))
        {
            throw new ServiceException("鏂囦欢璺緞鏃犳晥");
        }
        return resolved;
    }

    private List<Map<String, Object>> listSubDirectories(
            Path experimentRoot,
            Path currentPath,
            String experimentId,
            String projectName,
            String experimentName
    )
    {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (Files.notExists(currentPath) || !Files.isDirectory(currentPath))
        {
            return nodes;
        }

        try (Stream<Path> stream = Files.list(currentPath))
        {
            List<Path> childDirs = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(item -> item.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            for (Path directory : childDirs)
            {
                String relativePath = "/" + experimentRoot.relativize(directory).toString().replace("\\", "/");

                Map<String, Object> node = new HashMap<>();
                node.put("id", "dir-" + experimentId + ":" + relativePath);
                node.put("label", directory.getFileName().toString());
                node.put("type", "dir");
                node.put("disabled", false);
                node.put("experimentId", experimentId);
                node.put("experimentName", experimentName);
                node.put("projectName", projectName);
                node.put("relativePath", relativePath);
                node.put("children", listSubDirectories(experimentRoot, directory, experimentId, projectName, experimentName));
                nodes.add(node);
            }
        }
        catch (IOException e)
        {
            log.warn("读取目录失败: {}", currentPath, e);
        }
        return nodes;
    }
}
