package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DProjectInfo;
import com.ruoyi.Xidian.domain.TreeTableVo;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.service.IDExperimentInfoService;
import com.ruoyi.Xidian.support.PathLockManager;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.CacheConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class DExperimentInfoServiceImpl implements IDExperimentInfoService
{
    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;

    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private PathLockManager pathLockManager;

    private final String profile = RuoYiConfig.getProfile() + "/data";

    @Override
    public List<TreeTableVo> selectDExperimentInfoTree(TreeTableVo treeTableVo)
    {
        List<TreeTableVo> treeTables = new ArrayList<>();
        Map<Long, Integer> hashtable = new HashMap<>();
        int index = 0;

        if (treeTableVo.getId() == null || !treeTableVo.getId().matches(".*[a-zA-Z-].*"))
        {
            DProjectInfo dProjectInfo = new DProjectInfo();
            if (StringUtils.isNotEmpty(treeTableVo.getId()))
            {
                dProjectInfo.setProjectId(Long.valueOf(treeTableVo.getId()));
            }
            dProjectInfo.setProjectName(treeTableVo.getName());
            dProjectInfo.setParams(treeTableVo.getParams());
            dProjectInfo.setCreateBy(treeTableVo.getCreateBy());
            List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(dProjectInfo);

            for (DProjectInfo projectInfo : dProjectInfos)
            {
                TreeTableVo vo = new TreeTableVo();
                vo.setId(projectInfo.getProjectId().toString());
                vo.setName(projectInfo.getProjectName());
                vo.setCreateBy(projectInfo.getCreateBy());
                vo.setCreateTime(projectInfo.getCreateTime());
                vo.setContentDesc(projectInfo.getProjectContentDesc());
                vo.setPath(projectInfo.getPath());
                vo.setParentId(0L);
                vo.setAncestors("0");
                vo.setType("project");
                hashtable.put(projectInfo.getProjectId(), index);
                index++;
                treeTables.add(vo);
            }
        }

        DExperimentInfo query = new DExperimentInfo();
        query.setExperimentId(treeTableVo.getId());
        query.setExperimentName(treeTableVo.getName());
        query.setParams(treeTableVo.getParams());
        query.setCreateBy(treeTableVo.getCreateBy());
        List<DExperimentInfo> experimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(query);

        for (DExperimentInfo experimentInfo : experimentInfos)
        {
            TreeTableVo vo = new TreeTableVo();
            vo.setId(experimentInfo.getExperimentId());
            vo.setName(experimentInfo.getExperimentName());
            vo.setTargetId(experimentInfo.getTargetId());
            vo.setStartTime(experimentInfo.getStartTime());
            vo.setTargetType(experimentInfo.getTargetType());
            vo.setCreateBy(experimentInfo.getCreateBy());
            vo.setCreateTime(experimentInfo.getCreateTime());
            vo.setLocation(experimentInfo.getLocation());
            vo.setContentDesc(experimentInfo.getContentDesc());
            vo.setPath(experimentInfo.getPath());
            vo.setParentId(experimentInfo.getProjectId());
            vo.setAncestors("0," + experimentInfo.getProjectId());
            vo.setType("experiment");

            int parentIndex = hashtable.getOrDefault(vo.getParentId(), -1);
            if (parentIndex != -1)
            {
                ((List<TreeTableVo>) treeTables.get(parentIndex).getChildren()).add(vo);
            }
            else
            {
                treeTables.add(vo);
            }
        }
        return treeTables;
    }

    @Override
    public DExperimentInfo selectDExperimentInfoByExperimentId(String experimentId)
    {
        if (redisCache.getCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId) != null)
        {
            return redisCache.getCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
        }
        DExperimentInfo dExperimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (dExperimentInfo != null)
        {
            redisCache.setCacheObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId, dExperimentInfo);
        }
        return dExperimentInfo;
    }

    @Override
    public List<DProjectInfo> buildDProjectInfoTree()
    {
        List<DProjectInfo> dProjectInfos = dProjectInfoMapper.selectDProjectInfoList(new DProjectInfo());
        DExperimentInfo dExperimentInfo = new DExperimentInfo();
        for (DProjectInfo dProjectInfo : dProjectInfos)
        {
            dExperimentInfo.setProjectId(dProjectInfo.getProjectId());
            List<DExperimentInfo> dExperimentInfos = dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
            for (DExperimentInfo item : dExperimentInfos)
            {
                item.setParentId(dProjectInfo.getProjectId());
                item.setParentName(dProjectInfo.getProjectName());
            }
            dProjectInfo.setChildren(dExperimentInfos);
        }
        return dProjectInfos;
    }

    @Override
    public List<DExperimentInfo> selectDExperimentInfoList(DExperimentInfo dExperimentInfo)
    {
        return dExperimentInfoMapper.selectDExperimentInfoList(dExperimentInfo);
    }

    @Override
    public int insertDExperimentInfo(DExperimentInfo dExperimentInfo)
    {
        if (dExperimentInfo.getExperimentName() == null
                || !dExperimentInfo.getExperimentName().matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("试验名称无效");
        }

        DProjectInfo projectInfo = requireProject(dExperimentInfo.getProjectId());
        String pathStr = dExperimentInfo.getExperimentName();
        Path projectRoot = buildProjectRootPath(projectInfo.getPath());

        while (true)
        {
            Path experimentPath = buildExperimentRootPath(projectInfo.getPath(), "/" + pathStr);
            try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                    buildPaths(projectRoot),
                    buildPaths(experimentPath)))
            {
                if (Files.exists(experimentPath))
                {
                    pathStr = dExperimentInfo.getExperimentName() + UUID.randomUUID().toString().substring(0, 5);
                    continue;
                }

                try
                {
                    Files.createDirectories(experimentPath);
                }
                catch (IOException e)
                {
                    throw new ServiceException("创建试验目录失败: " + e.getMessage());
                }

                dExperimentInfo.setPath("/" + pathStr);
                return dExperimentInfoMapper.insertDExperimentInfo(dExperimentInfo);
            }
        }
    }

    @Override
    public int updateDExperimentInfo(DExperimentInfo dExperimentInfo)
    {
        dExperimentInfo.setUpdateTime(DateUtils.getNowDate());

        if (!dExperimentInfo.getPath().startsWith("/"))
        {
            throw new ServiceException("Invalid experiment path");
        }
        if (!dExperimentInfo.getPath().substring(1).matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$"))
        {
            throw new ServiceException("Invalid experiment path");
        }

        String experimentId = dExperimentInfo.getExperimentId();
        DExperimentInfo oldExperimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (oldExperimentInfo == null)
        {
            throw new ServiceException("Experiment not found");
        }

        DProjectInfo oldProjectInfo = requireProject(oldExperimentInfo.getProjectId());
        DProjectInfo newProjectInfo = requireProject(dExperimentInfo.getProjectId());
        Path oldProjectRoot = buildProjectRootPath(oldProjectInfo.getPath());
        Path newProjectRoot = buildProjectRootPath(newProjectInfo.getPath());
        Path oldPath = buildExperimentRootPath(oldProjectInfo.getPath(), oldExperimentInfo.getPath());
        Path newPath = buildExperimentRootPath(newProjectInfo.getPath(), dExperimentInfo.getPath());

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildPaths(oldProjectRoot, newProjectRoot),
                buildPaths(oldPath, newPath)))
        {
            if (Files.exists(newPath) && !newPath.equals(oldPath))
            {
                throw new ServiceException("目标试验路径已存在");
            }

            try
            {
                if (!newPath.equals(oldPath))
                {
                    Files.move(oldPath, newPath);
                }
            }
            catch (IOException e)
            {
                throw new ServiceException("更新试验目录失败: " + e.getMessage());
            }
        }

        redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
        return dExperimentInfoMapper.updateDExperimentInfo(dExperimentInfo);
    }

    @Override
    public int deleteDExperimentInfoByExperimentIds(String[] experimentIds)
    {
        if (experimentIds == null || experimentIds.length == 0)
        {
            return 0;
        }
        if ("0".equals(experimentIds[0]))
        {
            return 1;
        }

        List<DExperimentInfo> experimentInfos =
                dExperimentInfoMapper.selectDExperimentInfoByExperimentIds(Arrays.asList(experimentIds));
        if (experimentInfos.size() != experimentIds.length)
        {
            throw new ServiceException("试验信息不存在");
        }

        List<String> deleteExperimentIds = new ArrayList<>();
        StringBuilder errorMsg = new StringBuilder();

        for (DExperimentInfo experimentInfo : experimentInfos)
        {
            Long projectId = experimentInfo.getProjectId();
            if (projectId == null)
            {
                errorMsg.append("项目不存在: ").append(experimentInfo.getExperimentId()).append("\n");
                continue;
            }

            DProjectInfo projectInfo = requireProject(projectId);
            Path projectRoot = buildProjectRootPath(projectInfo.getPath());
            Path experimentPath = buildExperimentRootPath(projectInfo.getPath(), experimentInfo.getPath());

            try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                    buildPaths(projectRoot),
                    buildPaths(experimentPath)))
            {
                try
                {
                    if (isDirectoryNotEmpty(experimentPath))
                    {
                        errorMsg.append("试验目录不为空: ")
                                .append(experimentPath)
                                .append("\n");
                        continue;
                    }
                }
                catch (IOException e)
                {
                    errorMsg.append("检查试验目录失败: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                try
                {
                    Files.deleteIfExists(experimentPath);
                }
                catch (IOException e)
                {
                    errorMsg.append("删除试验目录失败: ")
                            .append(e.getMessage())
                            .append("\n");
                    continue;
                }

                dExperimentInfoMapper.deleteDExperimentInfoByExperimentId(experimentInfo.getExperimentId());
                deleteExperimentIds.add(experimentInfo.getExperimentId());
                redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentInfo.getExperimentId());
            }
        }

        if (errorMsg.length() > 0 && deleteExperimentIds.size() != experimentIds.length)
        {
            throw new ServiceException(errorMsg.toString());
        }
        return 1;
    }

    @Override
    public int deleteDExperimentInfoByExperimentId(String experimentId)
    {
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        if (experimentInfo == null)
        {
            throw new ServiceException("试验信息不存在");
        }

        DProjectInfo projectInfo = requireProject(experimentInfo.getProjectId());
        Path projectRoot = buildProjectRootPath(projectInfo.getPath());
        Path experimentPath = buildExperimentRootPath(projectInfo.getPath(), experimentInfo.getPath());

        try (PathLockManager.LockHandle ignored = pathLockManager.lock(
                buildPaths(projectRoot),
                buildPaths(experimentPath)))
        {
            try
            {
                if (isDirectoryNotEmpty(experimentPath))
                {
                    throw new ServiceException("试验目录不为空");
                }
            }
            catch (IOException e)
            {
                throw new ServiceException("检查试验目录失败: " + e.getMessage());
            }

            try
            {
                Files.deleteIfExists(experimentPath);
            }
            catch (IOException e)
            {
                throw new ServiceException("删除试验目录失败: " + e.getMessage());
            }

            redisCache.deleteObject(CacheConstants.EXPERIMENT_INFO_KEY + experimentId);
            return dExperimentInfoMapper.deleteDExperimentInfoByExperimentId(experimentId);
        }
    }

    @Override
    public List<TreeTableVo> getExperimentInfoTree()
    {
        return selectDExperimentInfoTree(new TreeTableVo());
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

    private Path buildProjectRootPath(String projectPath)
    {
        return Paths.get(profile, StringUtils.removeStart(projectPath, "/")).normalize();
    }

    private Path buildExperimentRootPath(String projectPath, String experimentPath)
    {
        return Paths.get(
                profile,
                StringUtils.removeStart(projectPath, "/"),
                StringUtils.removeStart(experimentPath, "/")
        ).normalize();
    }

    private List<Path> buildPaths(Path... paths)
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

    private boolean isDirectoryNotEmpty(Path directory) throws IOException
    {
        if (Files.notExists(directory))
        {
            return false;
        }
        try (Stream<Path> stream = Files.list(directory))
        {
            return stream.findAny().isPresent();
        }
    }
    public String getExperimentPath(String experimentId){
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(experimentId);
        String experimentPath = experimentInfo.getPath();
        String projectPath = requireProject(experimentInfo.getProjectId()).getPath();
        return profile + projectPath + experimentPath;
    }
}
