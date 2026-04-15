package com.ruoyi.Xidian.mapper;

import com.ruoyi.Xidian.domain.BackupData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

//备份还原数据
@Mapper
public interface BackDataMapper {
    /**
     * 查询备份还原数据详情
     * @param id 主键ID
     * @return 备份还原数据
     */
    public BackupData selectBackupDataById(Integer id);

    /**
     * 查询备份还原数据列表
     * @param backupData 备份还原数据
     * @return 备份还原数据集合
     */
    public List<BackupData> selectBackupDataList(BackupData backupData);

    /**
     * 新增备份还原数据
     * @param backupData 备份还原数据
     * @return 结果
     */
    public int insertBackupData(BackupData backupData);

    /**
     * 修改备份还原数据
     * @param backupData 备份还原数据
     * @return 结果
     */
    public int updateBackupData(BackupData backupData);

    /**
     * 删除备份还原数据
     * @param id 主键ID
     * @return 结果
     */
    public int deleteBackupDataById(Integer id);

    /**
     * 批量删除备份还原数据
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteBackupDataByIds(Integer[] ids);

    public BackupData selectBackupDataByDataId(@Param("dataInfoId") Integer dataId);
}
