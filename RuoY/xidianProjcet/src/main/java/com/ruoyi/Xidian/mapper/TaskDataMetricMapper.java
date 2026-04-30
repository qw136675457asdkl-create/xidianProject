package com.ruoyi.Xidian.mapper;

import com.ruoyi.Xidian.domain.TaskDataMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskDataMetricMapper {
    int batchInsert(@Param("list") List<TaskDataMetric> list);

    List<TaskDataMetric> selectByTaskDataGroupIds(@Param("groupIds") List<Long> groupIds);

    void deleteByTaskDataGroupIds(@Param("groupIds") List<Long> groupIds);

    void deleteByTaskId(Long taskId);
}
