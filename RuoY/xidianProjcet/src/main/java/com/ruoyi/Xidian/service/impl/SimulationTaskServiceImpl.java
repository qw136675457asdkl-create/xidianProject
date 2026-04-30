package com.ruoyi.Xidian.service.impl;

import com.ruoyi.Xidian.domain.DTO.TaskCreateRequest;
import com.ruoyi.Xidian.domain.DTO.TaskDataGroupDTO;
import com.ruoyi.Xidian.domain.DTO.TaskDataItemDTO;
import com.ruoyi.Xidian.domain.DTO.TaskDataMetricDTO;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.TaskDataMetric;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskDataMetricMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.Xidian.service.SimulationTaskService;
import com.ruoyi.Xidian.service.SimulationTaskStreamQueue;
import com.ruoyi.Xidian.utils.NickNameUtil;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.stream.Collectors;

@Service
public class SimulationTaskServiceImpl implements SimulationTaskService
{
    private static final Logger log = LoggerFactory.getLogger(SimulationTaskServiceImpl.class);

    private final TaskMapper taskMapper;
    private final TaskDataGroupMapper taskDataGroupMapper;
    private final TaskDataMetricMapper taskDataMetricMapper;
    private final DExperimentInfoServiceImpl dExperimentInfoService;
    private final SimulationTaskStreamQueue simulationTaskStreamQueue;
    private final DdataMapper dataMapper;

    public SimulationTaskServiceImpl(
            TaskMapper taskMapper,
            TaskDataGroupMapper taskDataGroupMapper,
            TaskDataMetricMapper taskDataMetricMapper,
            DExperimentInfoServiceImpl dExperimentInfoService,
            SimulationTaskStreamQueue simulationTaskStreamQueue,
            DdataMapper dataMapper
    )
    {
        this.taskMapper = taskMapper;
        this.taskDataGroupMapper = taskDataGroupMapper;
        this.taskDataMetricMapper = taskDataMetricMapper;
        this.dExperimentInfoService = dExperimentInfoService;
        this.simulationTaskStreamQueue = simulationTaskStreamQueue;
        this.dataMapper = dataMapper;
    }

    @Override
    public List<Task> selectList()
    {
        return taskMapper.selectList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Task insert(TaskCreateRequest request)
    {
        log.info("Start creating simulation task, taskName={}, experimentId={}, testId={}",
                request == null ? null : request.getTaskName(),
                request == null ? null : request.getExperimentId(),
                request == null ? null : request.getTestId());
        if (!hasSubTaskConfig(request))
        {
            throw new ServiceException("at least one sub task is required");
        }

        Date now = new Date();
        Task task = buildTask(request, now);
        List<TaskDataGroup> requestDataGroups = buildRequestDataGroups(request);
        task.setRequestDataGroups(requestDataGroups);
        log.info("Built main task entity, taskName={}, experimentId={}, taskCode={}",
                task.getTaskName(), task.getExperimentId(), task.getTaskCode());
        taskMapper.insert(task);
        log.info("Main task inserted, taskId={}", task.getId());
        if (task.getId() == null)
        {
            throw new ServiceException("failed to create task id");
        }

        List<TaskDataGroup> subTasks = buildSubTasks(task.getId(), request);
        log.info("Built sub tasks, taskId={}, subTaskCount={}", task.getId(), subTasks.size());
        for (TaskDataGroup group : subTasks)
        {
            if (!Boolean.TRUE.equals(group.getEnabled()))
            {
                continue;
            }
            log.info("Validating sub task before insert, taskId={}, groupName={}, dataName={}, outputType={}, targetNum={}",
                    task.getId(), group.getGroupName(), group.getDataName(), group.getOutputType(), group.getTargetNum());
            if (group.getTargetNum() != null
                    && group.getTargetNum() < 3
                    && (Objects.equals(group.getGroupName(), "radar_track") || Objects.equals(group.getGroupName(), "ads_b")))
            {
                log.warn("Target number validation failed, taskId={}, groupName={}, dataName={}, targetNum={}",
                        task.getId(), group.getGroupName(), group.getDataName(), group.getTargetNum());
                taskMapper.deleteById(task.getId());
                throw new ServiceException("target number must be at least 3");
            }
        }

        List<TaskDataGroup> enabledSubTasks = filterEnabledSubTasks(subTasks);
        insertSubTasksWithMetrics(enabledSubTasks);
        log.info("Enabled sub tasks inserted, taskId={}, totalSubTaskCount={}, enabledSubTaskCount={}",
                task.getId(), subTasks.size(), enabledSubTasks.size());
        task.setDataGroups(enabledSubTasks);
        enqueueAfterCommit(task);
        return task;
    }

    @Override
    public List<Task> selectTaskList(Task task)
    {
        return taskMapper.selectTaskList(task);
    }

    @Override
    public Task selectById(Long id)
    {
        Task task = taskMapper.selectById(id);
        if (task == null)
        {
            throw new ServiceException("task does not exist");
        }
        List<TaskDataGroup> dataGroups = taskDataGroupMapper.selectByTaskIdAndEnabled(id);
        fillMetrics(dataGroups);
        task.setDataGroups(dataGroups);
        for(TaskDataGroup taskDataGroup: task.getDataGroups()){
            taskDataGroup.setStatus(task.getStatus());
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long id)
    {
        taskDataMetricMapper.deleteByTaskId(id);
        taskDataGroupMapper.deleteByTaskId(id);
        taskMapper.deleteById(id);
    }

    private Task buildTask(TaskCreateRequest request, Date now)
    {
        Task task = new Task();
        task.setTaskCode(UUID.randomUUID().toString());
        task.setTaskName(request.getTaskName());
        task.setProjectId(request.getProjectId() == null ? null : request.getProjectId().intValue());
        task.setExperimentId(resolveExperimentId(request));
        task.setCarrierType(request.getCarrierType());
        task.setMotionModel(request.getMotionModel());
        task.setStartCoordinate(request.getStartCoordinate());
        task.setEndCoordinate(request.getEndCoordinate());
        task.setStatus(TaskStatusEnum.DRAFT.name());
        task.setCreateBy(NickNameUtil.getNickName());
        task.setCreateTime(now);
        task.setUpdateTime(now);
        task.setPath(dExperimentInfoService.getExperimentRelativePath(task.getExperimentId()));
        task.setDataCategorySummary(buildDataCategorySummary(request));
        task.setCreateUserId(SecurityUtils.getUserId());
        return task;
    }

    private List<TaskDataGroup> buildSubTasks(Long taskId, TaskCreateRequest request)
    {
        List<TaskDataGroup> subTasks = new ArrayList<>();
        for (TaskDataGroupDTO groupDTO : defaultIfNull(request.getDataGroups()))
        {
            List<TaskDataItemDTO> items = defaultIfNull(groupDTO.getItems());
            if (items.isEmpty())
            {
                subTasks.add(buildTaskDataGroup(taskId, groupDTO, null));
                continue;
            }
            for (TaskDataItemDTO itemDTO : items)
            {
                subTasks.add(buildTaskDataGroup(taskId, groupDTO, itemDTO));
            }
        }
        return subTasks;
    }

    private List<TaskDataGroup> buildRequestDataGroups(TaskCreateRequest request)
    {
        List<TaskDataGroup> requestDataGroups = new ArrayList<>();
        for (TaskDataGroupDTO groupDTO : defaultIfNull(request.getDataGroups()))
        {
            List<TaskDataItemDTO> items = defaultIfNull(groupDTO.getItems());
            if (items.isEmpty())
            {
                requestDataGroups.add(buildTaskDataGroup(null, groupDTO, null));
                continue;
            }
            for (TaskDataItemDTO itemDTO : items)
            {
                requestDataGroups.add(buildTaskDataGroup(null, groupDTO, itemDTO));
            }
        }
        return requestDataGroups;
    }

    private TaskDataGroup buildTaskDataGroup(Long taskId, TaskDataGroupDTO groupDTO, TaskDataItemDTO itemDTO)
    {
        TaskDataGroup group = new TaskDataGroup();
        group.setTaskId(taskId);
        group.setGroupName(groupDTO.getGroupName());
        group.setSortNo(groupDTO.getSortNo());
        group.setEnabled(Boolean.TRUE.equals(groupDTO.getEnabled()));
        group.setStatus(TaskStatusEnum.DRAFT.name());
        if (itemDTO == null)
        {
            group.setDataName(groupDTO.getGroupName());
            group.setIsSimulation(Boolean.TRUE);
            group.setMetrics(buildMetrics(groupDTO, null));
            return group;
        }
        group.setDataName(StringUtils.isNotEmpty(itemDTO.getDataName()) ? itemDTO.getDataName() : groupDTO.getGroupName());
        group.setRequestId(itemDTO.getRequestId());
        group.setOutputType(itemDTO.getOutputType());
        group.setDataSourceType(itemDTO.getDataSourceType());
        group.setSourceFileName(itemDTO.getSourceFileName());
        group.setStartTimeMs(itemDTO.getStartTimeMs());
        group.setEndTimeMs(itemDTO.getEndTimeMs());
        group.setFrequencyHz(itemDTO.getFrequencyHz());
        group.setTargetNum(itemDTO.getTargetNum());
        group.setIsSimulation(resolveSimulationFlag(itemDTO.getDataSourceType()));
        group.setMetrics(buildMetrics(groupDTO, itemDTO));
        return group;
    }

    private void insertSubTasksWithMetrics(List<TaskDataGroup> subTasks)
    {
        for (TaskDataGroup group : subTasks)
        {
            taskDataGroupMapper.insert(group);
            persistMetrics(group);
        }
    }

    private List<TaskDataGroup> filterEnabledSubTasks(List<TaskDataGroup> subTasks)
    {
        return defaultIfNull(subTasks).stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .collect(Collectors.toList());
    }

    private void persistMetrics(TaskDataGroup group)
    {
        List<TaskDataMetric> metrics = group == null ? null : group.getMetrics();
        if (metrics == null || metrics.isEmpty())
        {
            return;
        }

        for (TaskDataMetric metric : metrics)
        {
            metric.setTaskDataGroupId(group.getId());
        }
        taskDataMetricMapper.batchInsert(metrics);
    }

    private void enqueueAfterCommit(Task task)
    {
        if (TransactionSynchronizationManager.isSynchronizationActive())
        {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    enqueueTask(task);
                }
            });
            return;
        }

        enqueueTask(task);
    }

    private void enqueueTask(Task task)
    {
        simulationTaskStreamQueue.enqueue(task);
        log.info("Simulation task sent to redis stream, taskId={}, streamKey={}",
                task.getId(), simulationTaskStreamQueue.getTaskStreamKey());
    }

    private Boolean resolveSimulationFlag(String dataSourceType)
    {
        return !Objects.equals("existing", StringUtils.isEmpty(dataSourceType) ? null : dataSourceType.trim().toLowerCase());
    }

    private List<TaskDataMetric> buildMetrics(TaskDataGroupDTO groupDTO, TaskDataItemDTO itemDTO)
    {
        Map<String, TaskDataMetric> metrics = new LinkedHashMap<>();
        if (groupDTO.getVariables() != null)
        {
            groupDTO.getVariables().forEach((fieldName, metricDTO) -> addMetric(metrics, metricDTO, fieldName));
        }
        if (itemDTO == null)
        {
            return new ArrayList<>(metrics.values());
        }
        for (TaskDataMetricDTO metricDTO : defaultIfNull(itemDTO.getMetrics()))
        {
            addMetric(metrics, metricDTO, metricDTO.getFieldName());
        }
        if (itemDTO.getVariables() != null)
        {
            itemDTO.getVariables().forEach((fieldName, metricDTO) -> addMetric(metrics, metricDTO, fieldName));
        }
        return new ArrayList<>(metrics.values());
    }

    private void addMetric(Map<String, TaskDataMetric> metrics, TaskDataMetricDTO metricDTO, String fallbackFieldName)
    {
        if (metricDTO == null)
        {
            return;
        }
        String fieldName = StringUtils.isNotEmpty(metricDTO.getFieldName())
                ? metricDTO.getFieldName()
                : fallbackFieldName;
        if (StringUtils.isEmpty(fieldName))
        {
            return;
        }

        TaskDataMetric metric = new TaskDataMetric();
        metric.setFieldName(fieldName.trim());
        metric.setDataType(metricDTO.getDataType());
        metric.setRecommendedValue(metricDTO.getRecommendedValue());
        metric.setFluctuationRange(metricDTO.getFluctuationRange());
        metric.setDescription(metricDTO.getDescription());
        metric.setSortNo(metricDTO.getSortNo());
        metrics.put(metric.getFieldName(), metric);
    }

    private void fillMetrics(List<TaskDataGroup> groups)
    {
        if (groups == null || groups.isEmpty())
        {
            return;
        }

        List<Long> groupIds = groups.stream()
                .map(TaskDataGroup::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (groupIds.isEmpty())
        {
            return;
        }

        Map<Long, List<TaskDataMetric>> metricMap = taskDataMetricMapper.selectByTaskDataGroupIds(groupIds)
                .stream()
                .collect(Collectors.groupingBy(
                        TaskDataMetric::getTaskDataGroupId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (TaskDataGroup group : groups)
        {
            group.setMetrics(metricMap.getOrDefault(group.getId(), Collections.emptyList()));
        }
    }

    private String buildDataCategorySummary(TaskCreateRequest request)
    {
        return defaultIfNull(request.getDataGroups()).stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .map(TaskDataGroupDTO::getGroupName)
                .filter(StringUtils::isNotEmpty)
                .distinct()
                .collect(Collectors.joining("、"));
    }

    private String resolveExperimentId(TaskCreateRequest request)
    {
        if (StringUtils.isNotEmpty(request.getExperimentId()))
        {
            return request.getExperimentId();
        }
        return request.getTestId() == null ? null : String.valueOf(request.getTestId());
    }

    private boolean hasSubTaskConfig(TaskCreateRequest request)
    {
        return defaultIfNull(request.getDataGroups()).stream()
                .filter(group -> Boolean.TRUE.equals(group.getEnabled()))
                .anyMatch(group -> !defaultIfNull(group.getItems()).isEmpty());
    }

    private <T> List<T> defaultIfNull(List<T> values)
    {
        return values == null ? List.of() : values;
    }
}
