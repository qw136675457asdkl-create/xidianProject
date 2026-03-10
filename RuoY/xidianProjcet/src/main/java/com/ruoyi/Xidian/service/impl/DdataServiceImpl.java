package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DExperimentInfo;
import com.ruoyi.Xidian.domain.DdataInfo;
import com.ruoyi.Xidian.mapper.DExperimentInfoMapper;
import com.ruoyi.Xidian.mapper.DProjectInfoMapper;
import com.ruoyi.Xidian.mapper.DTargetInfoMapper;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.service.IDdataService;
import com.ruoyi.common.annotation.DataSource;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.enums.DataSourceType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class DdataServiceImpl implements IDdataService {
    private static final Logger log = LoggerFactory.getLogger(DdataServiceImpl.class);
    @Autowired
    private DdataMapper ddataMapper;
    @Autowired
    private DProjectInfoMapper dProjectInfoMapper;
    @Autowired
    private DExperimentInfoMapper dExperimentInfoMapper;
    @Autowired
    private DTargetInfoMapper dTargetInfoMapper;

    private final String profile = RuoYiConfig.getProfile();
    private final String backupDir = "backupdata";
    private final String backUPdir = RuoYiConfig.getBackupDir();

    @Override
    public List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo) {
        return ddataMapper.selectDdataInfoList(ddataInfo);
    }

    @Override
    public DdataInfo selectDdataInfoByDdataId(Integer id) {
        DdataInfo ddataInfo = new DdataInfo();
        ddataInfo.setId(id);
        return ddataMapper.selectDdataInfoList(ddataInfo).get(0);
    }

    @Override
    public Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file) {
        //TODO 完善数据
        ddataInfo.setWorkStatus("完成");
        ddataInfo.setSampleFrequency(1000);
        //上传数据文件
        try {
            String filePath = BuildDataFilePath(ddataInfo);

            String fileName = FileUploadUtils.upload(filePath, file);
        } catch (IOException e) {
            throw new ServiceException("上传数据文件失败：" + e.getMessage());
        }
        ddataInfo.setCreateBy(SecurityUtils.getUsername());
        ddataInfo.setDataFilePath("/"+ddataInfo.getDataName());
        ddataInfo.setTargetType(dTargetInfoMapper.selectDTargetInfoByTargetId(ddataInfo.getTargetId()).getTargetType());
        return ddataMapper.insertDdataInfo(ddataInfo);
    }

    @Override
    public Integer updateDdataInfo(DdataInfo ddataInfo) {
        DdataInfo ddataInfo1 =new DdataInfo();
        ddataInfo1.setId(ddataInfo.getId());
        if(!ddataInfo.getDataFilePath().startsWith("/")){
            throw new ServiceException("数据文件路径必须以/开头");
        }
        if(!ddataInfo.getFileName().matches("^[a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+$")){
            log.info("数据文件路径：{}", ddataInfo.getFileName());
            throw new ServiceException("数据文件路径只能包含字母、数字、下划线、短横线和中文字符");
        }
        String oldFilePath = ddataMapper.selectDdataInfoList(ddataInfo1).get(0).getDataFilePath();
        String prefix = profile + BuildDataFilePath(ddataInfo);
        String suffix = oldFilePath.substring(oldFilePath.lastIndexOf("."));
        if(!oldFilePath.equals("/" + ddataInfo.getFileName() + suffix)){
            //检查新路径是否已存在
            String FullPath = prefix + "/" + ddataInfo.getFileName() + suffix;
            Path newPath = Paths.get(FullPath);
            if(Files.exists(newPath)){
                throw new ServiceException("新路径已存在，请重新输入");
            }
            try{
                Files.move(Paths.get(prefix + oldFilePath), newPath);
            } catch (IOException e) {
                throw new ServiceException("移动数据文件失败：" + e.getMessage());
            }
            //更新数据库路径
            ddataInfo.setDataFilePath("/" + ddataInfo.getFileName() + suffix);
        }
        return ddataMapper.updateDdataInfo(ddataInfo);
    }

    @Override
    public Integer deleteDdataInfos(List<Integer> ids) {
        DdataInfo ddataInfo = new DdataInfo();
        for(Integer id : ids) {
            ddataInfo.setId(id);
            DdataInfo dataInfo1 = ddataMapper.selectDdataInfoList(ddataInfo).get(0);

            // 相对路径部分 (例如: \项目一\试验\a_user_xxx.csv)
            String relativePath = BuildDataFilePath(dataInfo1) + dataInfo1.getDataFilePath();

            Path sourcePath = Paths.get(profile, relativePath);
            // 构建目标文件路径
            Path targetPath = Paths.get(backUPdir, relativePath);

            // 数据备份
            try {
                Path targetParentDir = targetPath.getParent();
                if (targetParentDir != null && !Files.exists(targetParentDir)) {
                    Files.createDirectories(targetParentDir);
                }

                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new ServiceException("备份数据文件失败：" + sourcePath + " -> " + targetPath + "，原因：" + e.getMessage());
            }
            try {
                Files.deleteIfExists(sourcePath);
            } catch (IOException e) {
                throw new ServiceException("删除数据文件失败：" + sourcePath + "，原因：" + e.getMessage());
            }
        }

        // 数据库记录删除
        return ddataMapper.deleteDdataInfos(ids);
    }
    private String BuildDataFilePath(DdataInfo ddataInfo){
        DExperimentInfo experimentInfo = dExperimentInfoMapper.selectDExperimentInfoByExperimentId(ddataInfo.getExperimentId());
        String ProjectPath = dProjectInfoMapper.selectDProjectInfoByProjectId(experimentInfo.getProjectId()).getPath();
        return ProjectPath + experimentInfo.getPath();
    }
}
