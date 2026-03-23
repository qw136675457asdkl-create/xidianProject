package com.ruoyi.Xidian.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.DTargetInfo;
import com.ruoyi.Xidian.domain.TreeTableVo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDTargetInfoService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.common.utils.uuid.UUID;

@RestController
@RequestMapping("/data/info")
public class DExperimentInfoController extends BaseController
{
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;

    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @Autowired
    private IDTargetInfoService dTargetInfoService;

    @PreAuthorize("@ss.hasPermi('data:info:list')")
    @GetMapping("/list")
    public AjaxResult list(TreeTableVo treeTableVo)
    {
        List<TreeTableVo> treeTableVos = dExperimentInfoService.selectDExperimentInfoTree(treeTableVo);
        return success(treeTableVos);
    }

    @PreAuthorize("@ss.hasPermi('data:info:export')")
    @Log(title = "导出试验信息", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DExperimentInfo dExperimentInfo)
    {
        List<DExperimentInfo> list = dExperimentInfoService.selectDExperimentInfoList(dExperimentInfo);
        ExcelUtil<DExperimentInfo> util = new ExcelUtil<DExperimentInfo>(DExperimentInfo.class);
        util.exportExcel(response, list, "试验信息主数据");
    }

    @PreAuthorize("@ss.hasPermi('data:info:query')")
    @GetMapping(value = {"/", "/{infoId}"})
    public AjaxResult getInfo(@PathVariable(value = "infoId", required = false) String infoId, @RequestParam String type)
    {
        AjaxResult ajax = AjaxResult.success();
        List<DProjectInfo> dProjectInfos = dProjectInfoService.selectAllDProjectInfo();
        List<DTargetInfo> dTargetInfos = dTargetInfoService.selectDTargetInfoList(null);
        if ("project".equals(type))
        {
            return success(dProjectInfoService.selectDProjectInfoByProjectId(Long.valueOf(infoId)));
        }
        ajax.put("projects", dProjectInfos);
        ajax.put("targetTypes", dTargetInfos);
        if (infoId != null)
        {
            ajax.put(AjaxResult.DATA_TAG, dExperimentInfoService.selectDExperimentInfoByExperimentId(infoId));
        }
        return ajax;
    }

    @PreAuthorize("@ss.hasPermi('data:info:add')")
    @Log(title = "添加项目或试验信息", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody TreeTableVo treeTableVo)
    {
        if ("project".equals(treeTableVo.getType()))
        {
            DProjectInfo dProjectInfo = new DProjectInfo();
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setCreateBy(SecurityUtils.getUsername());
            dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
            return toAjax(dProjectInfoService.insertDProjectInfo(dProjectInfo));
        }

        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        dExperimentInfo.setExperimentId(UUID.randomUUID().toString());
        dExperimentInfo.setExperimentName(treeTableVo.getName());
        dExperimentInfo.setCreateBy(SecurityUtils.getUsername());
        dExperimentInfo.setContentDesc(treeTableVo.getContentDesc());
        dExperimentInfo.setLocation(treeTableVo.getLocation());
        dExperimentInfo.setTargetId(treeTableVo.getTargetId());
        dExperimentInfo.setTargetType(treeTableVo.getTargetType());
        dExperimentInfo.setProjectId(treeTableVo.getParentId());
        dExperimentInfo.setStartTime(treeTableVo.getStartTime());
        dExperimentInfo.setPath(treeTableVo.getPath());
        return toAjax(dExperimentInfoService.insertDExperimentInfo(dExperimentInfo));
    }

    @PreAuthorize("@ss.hasPermi('data:info:edit')")
    @Log(title = "修改项目或试验信息", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody TreeTableVo treeTableVo)
    {
        return updateInfoByType(treeTableVo.getId(), treeTableVo.getType(), treeTableVo);
    }

    @PreAuthorize("@ss.hasPermi('data:info:edit')")
    @Log(title = "修改项目或试验信息", businessType = BusinessType.UPDATE)
    @PutMapping("/{infoId}")
    public AjaxResult editById(@PathVariable String infoId, @RequestParam String type, @RequestBody TreeTableVo treeTableVo)
    {
        return updateInfoByType(infoId, type, treeTableVo);
    }

    @PreAuthorize("@ss.hasPermi('data:info:remove')")
    @Log(title = "删除项目或试验信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{infoId}")
    public AjaxResult removeById(@PathVariable String infoId, @RequestParam String type)
    {
        deleteInfoByType(infoId, type);
        return AjaxResult.success();
    }

    @PreAuthorize("@ss.hasPermi('data:info:remove')")
    @Log(title = "删除项目或试验信息", businessType = BusinessType.DELETE)
    @DeleteMapping("/{experimentIds}/project/{projectIds}")
    public AjaxResult remove(@PathVariable String[] experimentIds, @PathVariable Long[] projectIds)
    {
        String message = "";
        if (projectIds.length > 0 || experimentIds.length > 0)
        {
            try
            {
                dExperimentInfoService.deleteDExperimentInfoByExperimentIds(experimentIds);
            }
            catch (Exception e)
            {
                message += e.getMessage();
            }
            try
            {
                dProjectInfoService.deleteDProjectInfoByProjectIds(projectIds);
            }
            catch (Exception e)
            {
                message += e.getMessage();
            }
            if (message.length() > 0 && !message.trim().isEmpty())
            {
                return AjaxResult.error(message);
            }
            return AjaxResult.success(message);
        }
        return AjaxResult.error("请选择要删除的数据");
    }

    @GetMapping("/experimentInfos")
    public TableDataInfo getExperimentInfos(DExperimentInfo dExperimentInfo)
    {
        startPage();
        List<DExperimentInfo> dExperimentInfos = dExperimentInfoService.selectDExperimentInfoList(dExperimentInfo);
        return getDataTable(dExperimentInfos);
    }

    private AjaxResult updateInfoByType(String infoId, String type, TreeTableVo treeTableVo)
    {
        treeTableVo.setId(infoId);
        treeTableVo.setType(type);
        if ("project".equals(type))
        {
            DProjectInfo dProjectInfo = new DProjectInfo();
            dProjectInfo.setProjectId(Long.valueOf(treeTableVo.getId()));
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setCreateTime(treeTableVo.getCreateTime());
            dProjectInfo.setProjectContentDesc(treeTableVo.getContentDesc());
            dProjectInfo.setPath(treeTableVo.getPath());
            return toAjax(dProjectInfoService.updateDProjectInfo(dProjectInfo));
        }
        if (!"experiment".equals(type))
        {
            throw new ServiceException("Unsupported info type");
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
        dExperimentInfo.setPath(treeTableVo.getPath());
        return toAjax(dExperimentInfoService.updateDExperimentInfo(dExperimentInfo));
    }

    private void deleteInfoByType(String infoId, String type)
    {
        if (infoId == null || infoId.trim().isEmpty())
        {
            throw new ServiceException("Info id can not be empty");
        }
        if ("project".equals(type))
        {
            dProjectInfoService.deleteDProjectInfoByProjectId(Long.valueOf(infoId));
            return;
        }
        if ("experiment".equals(type))
        {
            dExperimentInfoService.deleteDExperimentInfoByExperimentId(infoId);
            return;
        }
        throw new ServiceException("Unsupported info type");
    }

    @GetMapping("/path/{experimentId}")
    public AjaxResult getExperimentPathById(@PathVariable String experimentId)
    {
        if (experimentId == null || experimentId.trim().isEmpty())
        {
            throw new ServiceException("试验id不能为空");
        }
        return AjaxResult.success(dExperimentInfoService.getExperimentPath(experimentId));
    }
}
