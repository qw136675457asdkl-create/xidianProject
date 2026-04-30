package com.ruoyi.system.service.impl;

import com.ruoyi.system.domain.dto.MachineLearningEnvConfigDTO;
import com.ruoyi.system.service.IMachineLearningEnvConfigService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 机器学习环境配置 Service 实现（内存保存，用于演示/联调）
 */
@Service
public class MachineLearningEnvConfigServiceImpl implements IMachineLearningEnvConfigService
{
    private static final AtomicReference<MachineLearningEnvConfigDTO> STORE = new AtomicReference<>(defaultConfig());

    @Override
    public int save(MachineLearningEnvConfigDTO dto)
    {
        STORE.set(dto);
        return 1;
    }

    @Override
    public MachineLearningEnvConfigDTO get()
    {
        return STORE.get();
    }

    @Override
    public int reset()
    {
        STORE.set(defaultConfig());
        return 1;
    }

    private static MachineLearningEnvConfigDTO defaultConfig()
    {
        MachineLearningEnvConfigDTO dto = new MachineLearningEnvConfigDTO();
        dto.setField119("/usr/local/python313/bin/python3.13");
        dto.setField104("Python 3.13");
        dto.setField105(Arrays.asList("配置完成后自动安装常用机器学习库（numpy、pandas、scikit-learn等）"));
        dto.setField107(1);
        dto.setField108(Arrays.asList("使用独立虚拟环境（venv/conda）"));
        dto.setField109(null);
        dto.setField111(Arrays.asList("若路径不存在，自动创建虚拟环境"));
        dto.setField112(Arrays.asList("锁定核心库版本，避免版本冲突"));
        dto.setField113(Collections.emptyList());
        dto.setField114(Arrays.asList("保存配置后，自动验证Python环境是否可用"));
        dto.setField115(Arrays.asList("生成环境配置日志文件（便于排查问题）"));
        dto.setField124("/usr/local/MATLAB/R2024A/bin/matlab");
        dto.setField125("R2024A");
        dto.setField126(Arrays.asList("启用Matlab Engine API for Python（支持Python调用Matlab函数）"));
        dto.setField127(Collections.emptyList());
        dto.setField128(Arrays.asList("关闭软件时自动清理Matlab临时缓存文件"));
        dto.setField129(Arrays.asList("自动添加Matlab路径到系统环境变量（无需手动配置）"));
        dto.setField131(null);
        return dto;
    }
}

