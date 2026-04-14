package com.ruoyi.Xidian.mapper;

import com.ruoyi.Xidian.domain.DdataInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DdataMapper {
    /**
     * 查询数据列表
     * @param ddataInfo
     * @return
     */
    List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo);

    Integer insertDdataInfo(DdataInfo ddataInfo);

    Integer updateDdataInfo(DdataInfo ddataInfo);

    Integer deleteDdataInfos(List<Integer> ids);
    // 查询同一文件下相同文件名的文件
    DdataInfo selectSameNameFile(@Param("experimentId") String experimentId, @Param("dataFilePath") String dataFilePath);

    int updateDdataInfos(@Param("list")List<DdataInfo> ddataInfos);
}
