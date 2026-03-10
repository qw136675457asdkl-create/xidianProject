package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.domain.DdataInfo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IDdataService {
    /**
     * 查询数据列表
     * @param ddataInfo
     * @return
     */
    List<DdataInfo> selectDdataInfoList(DdataInfo ddataInfo);

    DdataInfo selectDdataInfoByDdataId(Integer id);

    Integer insertDdataInfo(DdataInfo ddataInfo, MultipartFile file);
    Integer updateDdataInfo(DdataInfo ddataInfo);

    Integer deleteDdataInfos(List<Integer> ids);
}
