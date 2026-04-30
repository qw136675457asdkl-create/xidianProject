package com.ruoyi.Xidian.mapper;

import com.ruoyi.Xidian.domain.MdFileStorage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface MdFileStorageMapper {
    int insertMdFileStorage(MdFileStorage fileStorage);

    int updateMdFileStorage(MdFileStorage fileStorage);

    MdFileStorage selectById(@Param("id") Long id);

    MdFileStorage selectByBucketAndObjectName(@Param("bucketName") String bucketName,
                                              @Param("objectName") String objectName);

    int updateFileStorgeStatus(@Param("list") List<MdFileStorage> mdFileStorageList);

    MdFileStorage selectByBussinessId(@Param("dataInfoId") String dataInfoId);

    List<MdFileStorage> selectListByBussinessId(@Param("dataInfoId") String dataInfoId);

    List<MdFileStorage> selectFailedSimulationStorage(
            @Param("businessType") String businessType,
            @Param("bucketName") String bucketName,
            @Param("fileNames") List<String> fileNames,
            @Param("experimentId") String experimentId,
            @Param("uploadUserId") Long uploadUserId,
            @Param("createBy") String createBy,
            @Param("createTime") Date createTime);

    int deleteByIds(@Param("ids") List<Long> ids);
}
