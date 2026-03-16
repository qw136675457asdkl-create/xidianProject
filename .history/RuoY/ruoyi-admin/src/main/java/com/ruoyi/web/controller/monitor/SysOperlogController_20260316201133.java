package com.ruoyi.web.controller.monitor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.ruoyi.common.config.RuoYiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.system.domain.SysOperLog;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysOperLogService;
import com.ruoyi.web.controller.monitor.support.LogDocumentExportUtil;

/**
 * 操作日志记录
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/monitor/operlog")
public class SysOperlogController extends BaseController
{
    private static final String OPER_LOG_MAX_STORAGE_CONFIG_KEY = "sys.operlog.maxStorageMb";

    @Autowired
    private ISysOperLogService operLogService;

    @Autowired
    private ISysConfigService configService;

    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysOperLog operLog)
    {
        startPage();
        List<SysOperLog> list = operLogService.selectOperLogList(operLog);
        return getDataTable(list);
    }

    /**
     * 日志存储空间使用情况
     */
    @PreAuthorize("@ss.hasPermi('monitor:operlog:list')")
    @GetMapping("/storage")
    public AjaxResult storage()
    {
        double usedMb = operLogService.getAuditLogTableSize();
        double maxMb = resolveMaxStorageMb();
        double remainingMb = Math.max(0D, maxMb - usedMb);
        double usagePercent = maxMb <= 0D ? 0D : (usedMb * 100D / maxMb);
        boolean overflow = usedMb > maxMb;

        return AjaxResult.success()
                .put("usedMb", round2(usedMb))
                .put("maxMb", round2(maxMb))
                .put("remainingMb", round2(remainingMb))
                .put("usagePercent", round2(Math.min(usagePercent, 100D)))
                .put("overflow", overflow)
                .put("maxConfigKey", OPER_LOG_MAX_STORAGE_CONFIG_KEY);
    }

    @Log(title = "将操作日志导出为Excel", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, Long[] operIds)
    {
        if (operIds == null || operIds.length == 0) {
            throw new RuntimeException("导出的ID不能为空");
        }
        List<SysOperLog> list = operLogService.selectOperLogByIds(operIds);
        ExcelUtil<SysOperLog> util = new ExcelUtil<SysOperLog>(SysOperLog.class);
        util.exportExcel(response, list, "操作日志");
    }

    @Log(title = "将操作日志导出为Word文档", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @PostMapping("/export/word")
    public void exportWord(HttpServletResponse response, Long[] operIds) throws Exception
    {
        if (operIds == null || operIds.length == 0) {
            throw new RuntimeException("导出的ID不能为空");
        }
        List<SysOperLog> list = operLogService.selectOperLogByIds(operIds);
        LogDocumentExportUtil.exportOperLogWord(response, list);
    }

    @Log(title = "将操作日志导出为PDF文档", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:export')")
    @PostMapping("/export/pdf")
    public void exportPdf(HttpServletResponse response, Long[] operIds) throws Exception
    {
        if (operIds == null || operIds.length == 0) {
            throw new RuntimeException("导出的ID不能为空");
        }
        List<SysOperLog> list = operLogService.selectOperLogByIds(operIds);
        LogDocumentExportUtil.exportOperLogPdf(response, list);
    }

    @Log(title = "删除操作日志", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @DeleteMapping("/{operIds}")
    public AjaxResult remove(@PathVariable Long[] operIds)
    {
        return toAjax(operLogService.deleteOperLogByIds(operIds));
    }

    @Log(title = "清空操作日志", businessType = BusinessType.CLEAN)
    @PreAuthorize("@ss.hasPermi('monitor:operlog:remove')")
    @DeleteMapping("/clean")
    public AjaxResult clean()
    {
        operLogService.cleanOperLog();
        return success();
    }

    private double resolveMaxStorageMb()
    {
        String value = configService.selectConfigByKey(OPER_LOG_MAX_STORAGE_CONFIG_KEY);
        if (StringUtils.isEmpty(value))
        {
            return RuoYiConfig.getThresholdMb();
        }
        try
        {
            double mb = Double.parseDouble(value.trim());
            return mb > 0D ? mb : RuoYiConfig.getThresholdMb();
        }
        catch (Exception e)
        {
            return RuoYiConfig.getThresholdMb();
        }
    }

    private double round2(double value)
    {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}