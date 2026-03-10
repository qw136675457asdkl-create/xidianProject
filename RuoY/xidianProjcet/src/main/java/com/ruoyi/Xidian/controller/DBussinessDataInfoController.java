package com.ruoyi.Xidian.controller;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.service.IDProjectInfoService;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/data/bussiness")
public class DBussinessDataInfoController extends BaseController {
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private IDdataService ddataService;
    @Autowired
    private IDProjectInfoService dProjectInfoService;

    @GetMapping("/experimentInfoTree")
    public AjaxResult getDExperimentInfoTree()
    {
        return AjaxResult.success(dExperimentInfoService.getExperimentInfoTree());
    }

    @GetMapping("/datalist")
    public TableDataInfo getDDataInfoList(DdataInfo ddataInfo)
    {
        startPage();
        return getDataTable(ddataService.selectDdataInfoList(ddataInfo));
    }
    @GetMapping("/{id}")
    public AjaxResult getDDataInfoByDdataId(@PathVariable Integer id)
    {
        DdataInfo ddataInfo = ddataService.selectDdataInfoByDdataId(id);
        ddataInfo.setFileName(ddataInfo.getDataFilePath().substring(1, ddataInfo.getDataFilePath().lastIndexOf(".")));
        return AjaxResult.success(ddataInfo);
    }

    @PostMapping("/insert")
    @Log(title = "导入业务数据", businessType = BusinessType.INSERT)
    public AjaxResult insertDDataInfo(@ModelAttribute DdataInfo ddataInfo, @RequestParam("file") MultipartFile file)
    {
        return AjaxResult.success(ddataService.insertDdataInfo(ddataInfo, file));
    }

    @PutMapping("/update")
    @Log(title = "更新业务数据信息", businessType = BusinessType.UPDATE)
    public AjaxResult updateDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        return AjaxResult.success(ddataService.updateDdataInfo(ddataInfo));
    }

    @DeleteMapping("/delete")
    @Log(title = "删除业务数据信息", businessType = BusinessType.DELETE)
    public AjaxResult deleteDdataInfos(@RequestBody List<Integer> ids){
        return AjaxResult.success(ddataService.deleteDdataInfos(ids));
    }
    @PostMapping("/preview")
    public AjaxResult previewDDataInfo(@RequestBody DdataInfo ddataInfo)
    {
        String ExperimentId = ddataInfo.getExperimentId();
        DExperimentInfo dExperimentInfo = dExperimentInfoService.selectDExperimentInfoByExperimentId(ExperimentId);
        String AncestPath = dProjectInfoService.selectDProjectInfoByProjectId(dExperimentInfo.getProjectId()).getPath();
        return success(FileUtils.previewExcel("/home/hyy1208/data" + AncestPath + dExperimentInfo.getPath() + "/" + ddataInfo.getDataFilePath()));
    }
}
