package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.domain.TreeTable;
import com.ruoyi.Xidian.domain.UploadedFileInfo;
import com.ruoyi.Xidian.domain.VO.TreeTableVo;
import com.ruoyi.Xidian.service.FileStorageService;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDTargetInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.Xidian.utils.NickNameUtil;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.uuid.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/data/info")
public class DExperimentInfoController extends BaseController
{
    private static final String FOLDER_UPLOAD_MODE_WHOLE = "whole";

    @Autowired
    private IDExperimentInfoService dExperimentInfoService;

    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @Autowired
    private IDTargetInfoService dTargetInfoService;

    @Autowired
    private IDdataService ddataService;

    @Autowired
    private FileStorageService fileStorageService;

    //@PreAuthorize("@ss.hasPermi('data:info:list')")
    @GetMapping("/tree")
    public AjaxResult tree(TreeTable treeTable)
    {
        if (treeTable == null)
        {
            treeTable = new TreeTable();
        }
        try
        {
            List<TreeTableVo> treeTables = dExperimentInfoService.selectExperimentInfoTree(treeTable);
            return success(treeTables);
        }
        catch (Exception e)
        {
            throw buildSafeException("查询试验信息树失败", e);
        }
    }

    //@PreAuthorize("@ss.hasPermi('data:info:query')")
    @GetMapping(value = {"/", "/{infoId}"})
    @Log(title = "查看项目或试验信息", businessType = BusinessType.OTHER)
    public AjaxResult getInfo(@PathVariable(value = "infoId", required = false) String infoId, @RequestParam String type)
    {
        validateInfoType(type);
        if ("project".equals(type))
        {
            if (infoId == null || infoId.trim().isEmpty())
            {
                throw new ServiceException("项目id不能为空");
            }
            try
            {
                return success(dProjectInfoService.selectDProjectInfoByProjectId(Long.valueOf(infoId)));
            }
            catch (Exception e)
            {
                throw buildSafeException("查询项目信息失败", e);
            }
        }

        AjaxResult ajax = AjaxResult.success();
        try
        {
            ajax.put("projects", dProjectInfoService.selectAllDProjectInfo());
            ajax.put("targetTypes", dTargetInfoService.selectDTargetInfoList(null));
            if (infoId != null && !infoId.trim().isEmpty())
            {
                ajax.put(AjaxResult.DATA_TAG, dExperimentInfoService.selectDExperimentInfoByExperimentId(infoId));
            }
            return ajax;
        }
        catch (Exception e)
        {
            throw buildSafeException("查询试验信息失败", e);
        }
    }

    @PreAuthorize("@ss.hasPermi('data:info:addProject')")
    @Log(title = "新增项目信息", businessType = BusinessType.INSERT)
    @PostMapping("/project")
    public AjaxResult addProject(@RequestBody TreeTable treeTableVo)
    {
        validateInfoType(treeTableVo.getType());
        if ("project".equals(treeTableVo.getType()))
        {
            DProjectInfo dProjectInfo = new DProjectInfo();
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setCreateBy(NickNameUtil.getNickName());
            dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
            return toAjax(dProjectInfoService.insertDProjectInfo(dProjectInfo));
        }else{
            return AjaxResult.error("新增项目信息失败");
        }
    }
    @PreAuthorize("@ss.hasPermi('data:info:addExperiment')")
    @Log(title = "新增试验信息", businessType = BusinessType.INSERT)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/experiment")
    public AjaxResult addExperiment(
            @ModelAttribute TreeTable treeTableVo,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "relativePaths", required = false) List<String> relativePaths,
            @RequestParam(value = "folderUploadMode", required = false) String folderUploadMode,
            @RequestParam(value = "folderName", required = false) String folderName)
    {
        validateInfoType(treeTableVo.getType());
        AjaxResult ajax = AjaxResult.success();
        if("experiment".equals(treeTableVo.getType())){
            DExperimentInfo dExperimentInfo = new DExperimentInfo();
            dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
            dExperimentInfo.setExperimentName(treeTableVo.getName());
            dExperimentInfo.setCreateBy(NickNameUtil.getNickName());
            dExperimentInfo.setContentDesc(treeTableVo.getContentDesc());
            dExperimentInfo.setLocation(treeTableVo.getLocation());
            dExperimentInfo.setTargetId(treeTableVo.getTargetId());
            dExperimentInfo.setTargetType(treeTableVo.getTargetType());
            dExperimentInfo.setProjectId(treeTableVo.getParentId());
            dExperimentInfo.setStartTime(treeTableVo.getStartTime());
            dExperimentInfo.setPath(treeTableVo.getPath());
            try
            {
                String insertResult = dExperimentInfoService.insertDExperimentInfo(dExperimentInfo);
                if (insertResult != null && !insertResult.trim().isEmpty())
                {
                    return AjaxResult.error(insertResult);
                }
                ajax.put(AjaxResult.DATA_TAG, insertResult);
                // Reuse the same MinIO upload and data-relation import flow as business data.
                Integer importedCount = importExperimentDataFiles(files, relativePaths, dExperimentInfo, folderUploadMode, folderName);
                ajax.put("importedCount", importedCount);
            }
            catch (Exception e)
            {
                throw buildSafeException("新增试验信息失败", e);
            }
        }else{
            return AjaxResult.error("新增试验信息失败");
        }
        return ajax;
    }

    private Integer importExperimentDataFiles(
            List<MultipartFile> files,
            List<String> relativePaths,
            DExperimentInfo dExperimentInfo,
            String folderUploadMode,
            String folderName)
    {
        if (files == null || files.isEmpty())
        {
            return 0;
        }

        List<MultipartFile> validFiles = new ArrayList<>();
        List<String> validRelativePaths = new ArrayList<>();
        for (int index = 0; index < files.size(); index++)
        {
            MultipartFile file = files.get(index);
            if (file == null || file.isEmpty())
            {
                continue;
            }
            validFiles.add(file);
            validRelativePaths.add(relativePaths != null && index < relativePaths.size() ? relativePaths.get(index) : null);
        }

        if (validFiles.isEmpty())
        {
            throw new ServiceException("请选择有效文件");
        }

        Long userId = SecurityUtils.getUserId();
        boolean wholeFolderUpload = isWholeFolderUpload(folderUploadMode);
        String folderUploadId = wholeFolderUpload ? UUID.randomUUID().toString().replace("-", "") : null;
        List<UploadedFileInfo> uploadedFileInfoList = new ArrayList<>();
        for (int index = 0; index < validFiles.size(); index++)
        {
            MultipartFile multipartFile = validFiles.get(index);
            String uploadFilename = resolveUploadFilename(multipartFile, validRelativePaths.get(index));
            UploadedFileInfo uploadedFileInfo = new UploadedFileInfo();
            try
            {
                String objectName = wholeFolderUpload
                        ? fileStorageService.uploadFolderFile(multipartFile, userId, folderName, uploadFilename, folderUploadId)
                        : fileStorageService.upload(multipartFile, userId);
                uploadedFileInfo.setObjectName(objectName);
                uploadedFileInfo.setOriginalFilename(uploadFilename);
                uploadedFileInfo.setContentType(multipartFile.getContentType());
                uploadedFileInfo.setSize(multipartFile.getSize());
                uploadedFileInfoList.add(uploadedFileInfo);
            }
            catch (RuntimeException e)
            {
                log.error("试验数据文件上传失败: {}", uploadFilename, e);
            }
        }

        if (uploadedFileInfoList.isEmpty())
        {
            throw new ServiceException("文件全部上传失败");
        }

        DdataInfo importDataInfo = buildExperimentImportDataInfo(dExperimentInfo);
        return wholeFolderUpload
                ? ddataService.insertFolderDdataInfoByObjectNames(importDataInfo, uploadedFileInfoList, folderName)
                : ddataService.insertDdataInfosByObjectNames(importDataInfo, uploadedFileInfoList);
    }

    private boolean isWholeFolderUpload(String folderUploadMode)
    {
        return FOLDER_UPLOAD_MODE_WHOLE.equalsIgnoreCase(folderUploadMode == null ? "" : folderUploadMode.trim());
    }

    private DdataInfo buildExperimentImportDataInfo(DExperimentInfo dExperimentInfo)
    {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setExperimentId(dExperimentInfo.getExperimentId());
        ddataInfo.setTargetId(dExperimentInfo.getTargetId());
        ddataInfo.setTargetType(dExperimentInfo.getTargetType());
        ddataInfo.setIsSimulation(Boolean.TRUE);
        return ddataInfo;
    }

    private String resolveUploadFilename(MultipartFile multipartFile, String relativePath)
    {
        if (relativePath != null && !relativePath.trim().isEmpty())
        {
            return relativePath.trim();
        }
        return multipartFile.getOriginalFilename();
    }

    @PreAuthorize("@ss.hasPermi('data:info:edit')")
    @Log(title = "项目或试验信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TreeTable treeTableVo)
    {
        return updateInfoByType(treeTableVo.getId(), treeTableVo.getType(), treeTableVo);
    }

    @PreAuthorize("@ss.hasPermi('data:info:edit')")
    @Log(title = "项目或试验信息", businessType = BusinessType.UPDATE)
    @PutMapping("/{infoId}")
    public AjaxResult editById(@PathVariable String infoId, @RequestParam String type, @RequestBody TreeTable treeTableVo)
    {
        return updateInfoByType(infoId, type, treeTableVo);
    }

    @PreAuthorize("@ss.hasPermi('data:info:remove')")
    @Log(title = "项目或试验信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoId}")
    public AjaxResult removeById(@PathVariable String infoId, @RequestParam String type)
    {
        deleteInfoByType(infoId, type);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('data:info:remove')")
    @Log(title = "项目或试验信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{experimentIds}/project/{projectIds}")
    public AjaxResult remove(@PathVariable String[] experimentIds, @PathVariable Long[] projectIds)
    {
        List<String> errors = new ArrayList<>();
        boolean hasExperimentIds = experimentIds != null && experimentIds.length > 0;
        boolean hasProjectIds = projectIds != null && projectIds.length > 0;
        if (!hasProjectIds && !hasExperimentIds)
        {
            return AjaxResult.error("请选择要删除的数据");
        }

        if (hasExperimentIds)
        {
            try
            {
                dExperimentInfoService.deleteDExperimentInfoByExperimentIds(experimentIds);
            }
            catch (Exception e)
            {
                errors.add(resolveExceptionMessage(e, "删除试验信息失败"));
            }
        }

        if (hasProjectIds)
        {
            try
            {
                dProjectInfoService.deleteDProjectInfoByProjectIds(projectIds);
            }
            catch (Exception e)
            {
                errors.add(resolveExceptionMessage(e, "删除项目信息失败"));
            }
        }

        if (!errors.isEmpty())
        {
            return AjaxResult.error(joinErrorMessages(errors));
        }
        return AjaxResult.success();
    }

    @GetMapping("/experimentInfos")
    public TableDataInfo getExperimentInfos(DExperimentInfo dExperimentInfo)
    {
        startPage();
        try
        {
            List<DExperimentInfo> dExperimentInfos = dExperimentInfoService.selectDExperimentInfoList(dExperimentInfo);
            return getDataTable(dExperimentInfos);
        }
        catch (Exception e)
        {
            throw buildSafeException("查询试验信息失败", e);
        }
    }

    private AjaxResult updateInfoByType(String infoId, String type, TreeTable treeTableVo)
    {
        if (infoId == null || infoId.trim().isEmpty())
        {
            throw new ServiceException("试验信息id不能为空");
        }
        validateInfoType(type);
        treeTableVo.setId(infoId);
        treeTableVo.setType(type);

        if ("project".equals(type))
        {
            try
            {
                DProjectInfo dProjectInfo = new DProjectInfo();
                dProjectInfo.setProjectId(Long.valueOf(treeTableVo.getId()));
                dProjectInfo.setProjectName(treeTableVo.getName());
                dProjectInfo.setCreateTime(treeTableVo.getCreateTime());
                dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
                dProjectInfo.setPath("/" + treeTableVo.getName());
                return toAjax(dProjectInfoService.updateDProjectInfo(dProjectInfo));
            }
            catch (Exception e)
            {
                throw buildSafeException("修改项目信息失败", e);
            }
        }

        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        dExperimentInfo.setExperimentId(treeTableVo.getId());
        dExperimentInfo.setExperimentName(treeTableVo.getName());
        dExperimentInfo.setTargetId(treeTableVo.getTargetId());
        dExperimentInfo.setProjectId(treeTableVo.getParentId());
        dExperimentInfo.setLocation(treeTableVo.getLocation());
        dExperimentInfo.setCreateTime(treeTableVo.getCreateTime());
        dExperimentInfo.setContentDesc(treeTableVo.getContentDesc());
        dExperimentInfo.setStartTime(treeTableVo.getStartTime());
        dExperimentInfo.setPath("/" + treeTableVo.getName());
        try
        {
            return toAjax(dExperimentInfoService.updateDExperimentInfo(dExperimentInfo));
        }
        catch (Exception e)
        {
            throw buildSafeException("修改试验信息失败", e);
        }
    }

    private void deleteInfoByType(String infoId, String type)
    {
        if (infoId == null || infoId.trim().isEmpty())
        {
            throw new ServiceException("试验信息id不能为空");
        }
        validateInfoType(type);
        if ("project".equals(type))
        {
            try
            {
                dProjectInfoService.deleteDProjectInfoByProjectId(Long.valueOf(infoId));
            }
            catch (Exception e)
            {
                throw buildSafeException("删除项目失败", e);
            }
            return;
        }

        try
        {
            dExperimentInfoService.deleteDExperimentInfoByExperimentId(infoId);
        }
        catch (Exception e)
        {
            throw buildSafeException("删除试验失败", e);
        }
    }

    private void validateInfoType(String type)
    {
        if (!"project".equals(type) && !"experiment".equals(type))
        {
            throw new ServiceException("试验信息类型错误");
        }
    }

    private ServiceException buildSafeException(String message, Exception e)
    {
        ServiceException serviceException = extractServiceException(e);
        if (serviceException != null)
        {
            return serviceException;
        }
        serviceException = new ServiceException(message);
        serviceException.setDetailMessage(e == null ? null : e.getMessage());
        serviceException.initCause(e);
        return serviceException;
    }

    private ServiceException extractServiceException(Throwable throwable)
    {
        Throwable current = throwable;
        while (current != null)
        {
            if (current instanceof ServiceException)
            {
                return (ServiceException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private String resolveExceptionMessage(Exception e, String defaultMessage)
    {
        ServiceException serviceException = extractServiceException(e);
        if (serviceException != null && serviceException.getMessage() != null && !serviceException.getMessage().trim().isEmpty())
        {
            return normalizeErrorMessage(serviceException.getMessage());
        }
        if (e != null && e.getMessage() != null && !e.getMessage().trim().isEmpty())
        {
            return normalizeErrorMessage(e.getMessage());
        }
        return defaultMessage;
    }

    private String joinErrorMessages(List<String> errors)
    {
        StringBuilder builder = new StringBuilder();
        for (String error : errors)
        {
            String normalized = normalizeErrorMessage(error);
            if (normalized.isEmpty())
            {
                continue;
            }
            if (builder.length() > 0)
            {
                builder.append("；");
            }
            builder.append(normalized);
        }
        return builder.length() > 0 ? builder.toString() : "操作失败";
    }

    private String normalizeErrorMessage(String message)
    {
        if (message == null)
        {
            return "";
        }
        return message.replace("\r\n", "；").replace("\n", "；").trim();
    }

    @GetMapping("/path/{experimentId}")
    @Log(title = "查看试验路径", businessType = BusinessType.OTHER)
    public AjaxResult getExperimentPathById(@PathVariable String experimentId)
    {
        if (experimentId == null || experimentId.trim().isEmpty())
        {
            throw new ServiceException("试验id不能为空");
        }
        try
        {
            String path = dExperimentInfoService.getExperimentPath(experimentId);
            AjaxResult ajaxResult = AjaxResult.success();
            ajaxResult.put("data", path);
            return ajaxResult;
        }
        catch (Exception e)
        {
            throw buildSafeException("获取试验路径失败", e);
        }
    }
}
