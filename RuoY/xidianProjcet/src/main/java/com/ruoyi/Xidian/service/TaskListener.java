package com.ruoyi.Xidian.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.Xidian.config.MinioProperties;
import com.ruoyi.Xidian.config.SimulationTaskStreamProperties;
import com.ruoyi.Xidian.domain.Coordinate;
import com.ruoyi.Xidian.domain.DTO.TaskToPy;
import com.ruoyi.Xidian.domain.MdFileStorage;
import com.ruoyi.Xidian.domain.Task;
import com.ruoyi.Xidian.domain.TaskDataGroup;
import com.ruoyi.Xidian.domain.TaskDataMetric;
import com.ruoyi.Xidian.domain.enums.FileStorageStatusEnum;
import com.ruoyi.Xidian.domain.enums.MinioBusinessTypeEnum;
import com.ruoyi.Xidian.domain.enums.TaskStatusEnum;
import com.ruoyi.Xidian.mapper.DdataMapper;
import com.ruoyi.Xidian.mapper.MdFileStorageMapper;
import com.ruoyi.Xidian.mapper.TaskDataGroupMapper;
import com.ruoyi.Xidian.mapper.TaskDataMetricMapper;
import com.ruoyi.Xidian.mapper.TaskMapper;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.framework.web.service.WebSocketServer;
import com.ruoyi.system.service.ISysUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.ServerException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TaskListener implements SmartLifecycle
{
    private static final Logger log = LoggerFactory.getLogger(TaskListener.class);

    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private TaskDataGroupMapper taskDataGroupMapper;
    @Autowired
    private TaskDataMetricMapper taskDataMetricMapper;
    @Autowired
    private DdataMapper ddataMapper;
    @Autowired
    private IDExperimentInfoService dExperimentInfoService;
    @Autowired
    private PythonSimulationService pythonSimulationService;
    @Autowired
    private IDdataService iDdataService;
    @Autowired
    private WebSocketServer webSocketServer;
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private MdFileStorageMapper mdFileStorageMapper;
    @Autowired
    private SimulationTaskStreamQueue simulationTaskStreamQueue;
    @Autowired
    private SimulationTaskStreamProperties streamProperties;
    @Autowired
    private FileStorageService fileStorageService;
    @Autowired
    private MinioProperties minioProperties;
    @Autowired
    private RedisCache redisCache;
    @Resource(name = "threadPoolTaskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Resource(name = "scheduledExecutorService")
    private ScheduledExecutorService scheduledExecutorService;

    @Value("${server.port:8081}")
    private String serverPort;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Future<?>> workerFutures = new CopyOnWriteArrayList<>();
    private volatile ScheduledFuture<?> retryDispatcherFuture;
    private volatile String instanceId = "unknown";

    @Override
    public void start()
    {
        if (!running.compareAndSet(false, true))
        {
            return;
        }

        instanceId = resolveInstanceId();
        simulationTaskStreamQueue.initialize();
        startTaskWorkers();
        startRetryDispatcher();
        log.info("Simulation task stream consumer started, instanceId={}, consumerGroup={}, consumerCount={}",
                instanceId,
                streamProperties.getConsumerGroup(),
                streamProperties.getConsumerCount());
    }

    @Override
    public void stop()
    {
        if (!running.compareAndSet(true, false))
        {
            return;
        }

        ScheduledFuture<?> currentRetryDispatcherFuture = retryDispatcherFuture;
        if (currentRetryDispatcherFuture != null)
        {
            currentRetryDispatcherFuture.cancel(true);
            retryDispatcherFuture = null;
        }

        for (Future<?> workerFuture : workerFutures)
        {
            workerFuture.cancel(true);
        }
        workerFutures.clear();
        log.info("Simulation task stream consumer stopped, instanceId={}", instanceId);
    }

    @Override
    public void stop(Runnable callback)
    {
        try
        {
            stop();
        }
        finally
        {
            callback.run();
        }
    }

    @Override
    public boolean isRunning()
    {
        return running.get();
    }

    @Override
    public boolean isAutoStartup()
    {
        return true;
    }

    @Override
    public int getPhase()
    {
        return Integer.MAX_VALUE;
    }

    private void startTaskWorkers()
    {
        for (int workerIndex = 1; workerIndex <= streamProperties.getConsumerCount(); workerIndex++)
        {
            String consumerName = buildConsumerName(workerIndex);
            workerFutures.add(threadPoolTaskExecutor.submit(() -> consumeLoop(consumerName)));
        }
    }

    private void startRetryDispatcher()
    {
        retryDispatcherFuture = scheduledExecutorService.scheduleWithFixedDelay(
                this::dispatchRetryTasksSafely,
                streamProperties.getRetryDispatchIntervalMillis(),
                streamProperties.getRetryDispatchIntervalMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void consumeLoop(String consumerName)
    {
        log.info("Simulation task worker started, consumerName={}", consumerName);
        while (running.get() && !Thread.currentThread().isInterrupted())
        {
            try
            {
                List<MapRecord<String, Object, Object>> records = simulationTaskStreamQueue.readPendingTaskRecords(consumerName, 1);
                if (records.isEmpty())
                {
                    records = simulationTaskStreamQueue.readNewTaskRecords(consumerName, 1);
                }
                if (records.isEmpty())
                {
                    continue;
                }

                for (MapRecord<String, Object, Object> record : records)
                {
                    handleTaskRecord(consumerName, record);
                }
            }
            catch (Exception exception)
            {
                if (!running.get() || Thread.currentThread().isInterrupted())
                {
                    break;
                }
                log.error("Simulation task worker loop failed, consumerName={}", consumerName, exception);
                sleepQuietly(streamProperties.getWorkerErrorBackoffMillis());
            }
        }
        log.info("Simulation task worker stopped, consumerName={}", consumerName);
    }

    private void handleTaskRecord(String consumerName, MapRecord<String, Object, Object> record)
    {
        Task task;
        int retryCount;
        try
        {
            task = simulationTaskStreamQueue.readTask(record);
            retryCount = simulationTaskStreamQueue.readRetryCount(record);
        }
        catch (Exception exception)
        {
            log.error("Failed to deserialize simulation task stream message, recordId={}, consumerName={}",
                    record.getId(), consumerName, exception);
            acknowledgeAndDeleteTaskRecord(record);
            return;
        }

        log.info("Received simulation task stream message, taskId={}, recordId={}, consumerName={}, retryCount={}",
                task == null ? null : task.getId(),
                record.getId(),
                consumerName,
                retryCount);

        if (task == null || task.getId() == null)
        {
            log.warn("Skip simulation task stream message because task payload is empty, recordId={}, consumerName={}",
                    record.getId(), consumerName);
            acknowledgeAndDeleteTaskRecord(record);
            return;
        }

        Task currentTask = taskMapper.selectById(task.getId());
        task.setCreateUserId(currentTask.getCreateUserID());
        if (currentTask == null || TaskStatusEnum.SUCCESS.toString().equals(currentTask.getStatus()))
        {
            log.info("Task already completed or missing, ack directly, taskId={}, currentStatus={}, recordId={}",
                    task.getId(),
                    currentTask == null ? null : currentTask.getStatus(),
                    record.getId());
            acknowledgeAndDeleteTaskRecord(record);
            return;
        }
        try
        {
            processTask(task);
            acknowledgeAndDeleteTaskRecord(record);
            log.info("Task processing completed and acknowledged, taskId={}, recordId={}", task.getId(), record.getId());
        }
        catch (Exception exception)
        {
            List<String> generatedFileNames = new ArrayList<>();
            try
            {
                String requestId = resolveRequestId(task);
                JsonNode taskListResponse = pythonSimulationService.listSimulations();
                String pythonTaskId = null;
                for (JsonNode taskNode : taskListResponse.path("tasks"))
                {
                    if (requestId.equals(taskNode.path("request_id").asText(null)))
                    {
                        pythonTaskId = taskNode.path("task_id").asText(null);
                        break;
                    }
                }

                if (StringUtils.isNotEmpty(pythonTaskId))
                {
                    JsonNode taskResponse = pythonSimulationService.getSimulation(pythonTaskId);
                    List<String> generatedFilePaths = new ArrayList<>();
                    JsonNode filesNode = taskResponse.path("files");
                    java.util.Iterator<Map.Entry<String, JsonNode>> fileIterator = filesNode.fields();
                    while (fileIterator.hasNext())
                    {
                        Map.Entry<String, JsonNode> fileEntry = fileIterator.next();
                        String filePath = fileEntry.getValue().asText(null);
                        if (StringUtils.isEmpty(filePath))
                        {
                            continue;
                        }
                        if ("directory".equals(fileEntry.getKey()))
                        {
                            continue;
                        }
                        generatedFilePaths.add(filePath);
                        String fileName = Paths.get(filePath).getFileName().toString();
                        if (!generatedFileNames.contains(fileName))
                        {
                            generatedFileNames.add(fileName);
                        }
                    }

                    String directory = getFilePath(taskResponse, "directory");
                    if (StringUtils.isEmpty(directory))
                    {
                        directory = taskResponse.path("generated_files_directory").asText(null);
                    }
                    for (String filePath : generatedFilePaths)
                    {
                        try
                        {
                            Files.deleteIfExists(Paths.get(filePath));
                        }
                        catch (Exception cleanupException)
                        {
                            log.warn("Failed to delete failed simulation output file, taskId={}, filePath={}",
                                    task.getId(), filePath, cleanupException);
                        }
                    }
                    if (StringUtils.isNotEmpty(directory) && Files.exists(Paths.get(directory)))
                    {
                        try (Stream<Path> cleanupStream = Files.walk(Paths.get(directory)))
                        {
                            cleanupStream.sorted(Comparator.reverseOrder())
                                    .forEach(cleanupPath -> {
                                        try
                                        {
                                            Files.deleteIfExists(cleanupPath);
                                        }
                                        catch (Exception cleanupException)
                                        {
                                            log.warn("Failed to delete failed simulation output path, taskId={}, path={}",
                                                    task.getId(), cleanupPath, cleanupException);
                                        }
                                    });
                        }
                    }
                }
            }
            catch (Exception cleanupException)
            {
                log.warn("Failed to cleanup failed simulation output files, taskId={}", task.getId(), cleanupException);
            }

            try
            {
                if (!generatedFileNames.isEmpty())
                {
                    String cleanupCreateBy = task.getCreateBy();
                    if (StringUtils.isEmpty(cleanupCreateBy) && currentTask != null)
                    {
                        cleanupCreateBy = currentTask.getCreateBy();
                    }
                    Date cleanupCreateTime = task.getCreateTime();
                    if (cleanupCreateTime == null && currentTask != null)
                    {
                        cleanupCreateTime = currentTask.getCreateTime();
                    }

                    List<MdFileStorage> storageRows = mdFileStorageMapper.selectFailedSimulationStorage(
                            MinioBusinessTypeEnum.DATA_RELATION.getCode(),
                            minioProperties.getBucket(),
                            generatedFileNames,
                            task.getExperimentId(),
                            task.getCreateUserID(),
                            cleanupCreateBy,
                            cleanupCreateTime);
                    List<Long> storageIds = new ArrayList<>();
                    List<String> objectNames = new ArrayList<>();
                    for (MdFileStorage storageRow : storageRows)
                    {
                        if (storageRow == null)
                        {
                            continue;
                        }
                        Long storageId = storageRow.getId();
                        if (storageId != null && !storageIds.contains(storageId))
                        {
                            storageIds.add(storageId);
                        }
                        String objectName = storageRow.getObjectName();
                        if (StringUtils.isNotEmpty(objectName) && !objectNames.contains(objectName))
                        {
                            objectNames.add(objectName);
                        }
                    }

                    for (String objectName : objectNames)
                    {
                        try
                        {
                            fileStorageService.delete(objectName);
                        }
                        catch (Exception cleanupException)
                        {
                            log.warn("Failed to delete failed simulation MinIO object, taskId={}, objectName={}",
                                    task.getId(), objectName, cleanupException);
                        }
                    }

                    if (!storageIds.isEmpty())
                    {
                        List<Integer> dataIds = ddataMapper.selectIdsByStorageFileIds(storageIds);
                        for (Integer dataId : dataIds)
                        {
                            if (dataId != null)
                            {
                                redisCache.deleteObject(com.ruoyi.common.constant.CacheConstants.DATA_INFO_KEY + dataId);
                            }
                        }
                        if (!dataIds.isEmpty())
                        {
                            ddataMapper.deleteDdataInfos(dataIds);
                        }
                        mdFileStorageMapper.deleteByIds(storageIds);
                    }
                }
            }
            catch (Exception cleanupException)
            {
                log.warn("Failed to cleanup failed simulation database records, taskId={}", task.getId(), cleanupException);
            }

            log.error("Simulation task failed, taskId={}", task.getId(), exception);
            onTaskProcessingFailure(task, retryCount, record, exception);
            if (retryCount >= streamProperties.getMaxRetryCount())
            {
                try
                {
                    taskDataMetricMapper.deleteByTaskId(task.getId());
                    taskDataGroupMapper.deleteByTaskId(task.getId());
                    //taskMapper.deleteById(task.getId());
                }
                catch (Exception cleanupException)
                {
                    log.warn("Failed to delete final failed simulation task records, taskId={}",
                            task.getId(), cleanupException);
                }
            }
        }
    }

    private void processTask(Task task) throws IOException
    {
        log.info("Start processing simulation task, taskId={}, taskCode={}, experimentId={}",
                task.getId(), task.getTaskCode(), task.getExperimentId());
        task.setStatus(TaskStatusEnum.RUNNING.toString());
        task.setUpdateTime(new Date());
        taskMapper.update(task);
        log.info("Task marked as RUNNING, taskId={}", task.getId());

        List<TaskDataGroup> persistedTaskDataGroups = taskDataGroupMapper.selectByTaskId(task.getId());
        fillMetrics(persistedTaskDataGroups);
        task.setDataGroups(persistedTaskDataGroups);
        log.info("Loaded task data groups, taskId={}, groupCount={}",
                task.getId(), task.getDataGroups() == null ? 0 : task.getDataGroups().size());

        TaskToPy taskToPy = buildPythonRequest(task, resolvePythonRequestGroups(task, persistedTaskDataGroups));
        log.info("Built python request, taskId={}, requestId={}", task.getId(), taskToPy.getRequestId());

        JsonNode taskResponse = pythonSimulationService.submitAndWait(taskToPy);
        log.info("Python simulation completed, taskId={}, response={}", task.getId(), taskResponse);

        String directory = getFilePath(taskResponse, "directory");
        log.info("Python output directory resolved, taskId={}, directory={}", task.getId(), directory);

        List<String> fileLists = new ArrayList<>();
        try (Stream<Path> stream = Files.list(Paths.get(directory)))
        {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> fileLists.add(path.getFileName().toString()));
        }
        SysUser user = sysUserService.selectUserById(task.getCreateUserID());
        List<MdFileStorage> mdFileStorageList = new ArrayList<>();
        MdFileStorage mdFileStorage = new MdFileStorage();
        mdFileStorage.setBucketName(minioProperties.getBucket());
        mdFileStorage.setUploadUserId(user.getUserId());
        mdFileStorage.setBusinessType(MinioBusinessTypeEnum.DATA_RELATION.getCode());
        mdFileStorage.setCreateBy(task.getCreateBy());
        mdFileStorage.setStorageProvider("MINIO");
        mdFileStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        mdFileStorage.setUploadUserId(user.getUserId());
        for (String sourceName : fileLists)
        {
            String sourceFile = directory + "/" + sourceName;
            mdFileStorage.setOriginalFileName(sourceName);
            String objectName = fileStorageService.uploadLocalFile(sourceFile, user.getUserId());
            mdFileStorage.setObjectName(objectName);
            Path path = Paths.get(sourceFile);
            mdFileStorage.setFileSize(Files.size(path));
            mdFileStorage.setContentType(Files.probeContentType(path));
            mdFileStorage.setFileExt(sourceName.substring(sourceName.lastIndexOf(".")));
            mdFileStorage.setCreateTime(new Date());
            mdFileStorageMapper.insertMdFileStorage(mdFileStorage);
            mdFileStorageList.add(mdFileStorage);
            log.info("Copy simulation file, taskId={}, sourceFile={},objectName={}", task.getId(), sourceFile,objectName);
            deleteFile(sourceFile);
        }
        deleteFile(directory);
        log.info("Simulation files copied and source directory removed, taskId={}, copiedCount={}",
                task.getId(), mdFileStorageList.size());
        List<TaskDataGroup> taskDataGroupList = new ArrayList<>();
        for(TaskDataGroup taskDataGroup:task.getDataGroups()){
            if(taskDataGroup.getEnabled() == true){
                taskDataGroupList.add(taskDataGroup);
            }
        }

        iDdataService.syncSimulationResultFiles(
                task.getExperimentId(),
                mdFileStorageList,
                fileLists,
                task.getCreateBy(),
                task.getDataCategorySummary(),taskDataGroupList);
        log.info("Simulation result files synced to database, taskId={}", task.getId());

        task.setStatus(TaskStatusEnum.SUCCESS.toString());
        task.setUpdateTime(new Date());
        taskMapper.update(task);
        log.info("Task marked as SUCCESS, taskId={}", task.getId());

        notifyTaskSummaryIfCompleted(task.getId());
    }

    private void onTaskProcessingFailure(
            Task task,
            int retryCount,
            MapRecord<String, Object, Object> record,
            Exception exception
    )
    {
        log.error("Simulation task processing failed, taskId={}, recordId={}, retryCount={}",
                task == null ? null : task.getId(),
                record == null ? null : record.getId(),
                retryCount,
                exception);

        if (retryCount < streamProperties.getMaxRetryCount())
        {
            int nextRetryCount = retryCount + 1;
            simulationTaskStreamQueue.scheduleRetry(task, nextRetryCount, exception.getMessage());
            acknowledgeAndDeleteTaskRecord(record);
            log.warn("Simulation task scheduled to retry later, taskId={}, recordId={}, nextRetryCount={}",
                    task == null ? null : task.getId(),
                    record == null ? null : record.getId(),
                    nextRetryCount);
            return;
        }

        task.setStatus(TaskStatusEnum.FAILED.toString());
        task.setUpdateTime(new Date());
        notifyTaskSummaryIfCompleted(task.getId());
        taskMapper.update(task);
        taskDataGroupMapper.deleteByTaskId(task.getId());
        simulationTaskStreamQueue.moveToFinalQueue(task, retryCount, exception.getMessage());
        acknowledgeAndDeleteTaskRecord(record);
        log.error("Simulation task reached max retry count and moved to final stream, taskId={}, recordId={}, retryCount={}",
                task.getId(),
                record.getId(),
                retryCount);
    }

    private void dispatchRetryTasksSafely()
    {
        if (!running.get())
        {
            return;
        }

        if (!simulationTaskStreamQueue.tryAcquireRetryDispatchLock(instanceId))
        {
            return;
        }

        try
        {
            long now = System.currentTimeMillis();
            List<MapRecord<String, Object, Object>> retryRecords =
                    simulationTaskStreamQueue.readRetryRecords(streamProperties.getRetryDispatchBatchSize());

            for (MapRecord<String, Object, Object> retryRecord : retryRecords)
            {
                long nextRetryAt;
                try
                {
                    nextRetryAt = simulationTaskStreamQueue.readNextRetryAt(retryRecord);
                }
                catch (Exception exception)
                {
                    log.error("Invalid retry stream message, deleting record, retryRecordId={}", retryRecord.getId(), exception);
                    simulationTaskStreamQueue.deleteRetryRecord(retryRecord.getId());
                    continue;
                }

                if (nextRetryAt > now)
                {
                    break;
                }

                simulationTaskStreamQueue.moveRetryRecordToTaskStream(retryRecord);
                log.info("Retry task moved back to task stream, retryRecordId={}, nextRetryAt={}",
                        retryRecord.getId(), nextRetryAt);
            }
        }
        catch (Exception exception)
        {
            if (running.get())
            {
                log.error("Simulation task retry dispatcher failed, instanceId={}", instanceId, exception);
            }
        }
        finally
        {
            simulationTaskStreamQueue.releaseRetryDispatchLock(instanceId);
        }
    }

    private void acknowledgeAndDeleteTaskRecord(MapRecord<String, Object, Object> record)
    {
        RecordId recordId = record.getId();
        simulationTaskStreamQueue.acknowledgeTaskRecord(recordId);
        try
        {
            simulationTaskStreamQueue.deleteTaskRecord(recordId);
        }
        catch (Exception exception)
        {
            log.warn("Failed to delete acknowledged task stream record, recordId={}", recordId, exception);
        }
    }

    private String buildConsumerName(int workerIndex)
    {
        return instanceId + "-simulation-worker-" + workerIndex;
    }

    private String resolveInstanceId()
    {
        try
        {
            return InetAddress.getLocalHost().getHostName() + "-" + serverPort;
        }
        catch (Exception ignored)
        {
            return "localhost-" + serverPort;
        }
    }

    private void sleepQuietly(long millis)
    {
        if (millis <= 0)
        {
            return;
        }
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException ignored)
        {
            Thread.currentThread().interrupt();
        }
    }

    private TaskToPy buildPythonRequest(Task task, List<TaskDataGroup> taskDataGroups)
    {
        TaskToPy taskToPy = new TaskToPy();
        taskToPy.setRequestId(resolveRequestId(task));
        taskToPy.setBasic(buildBasicConfig(task));
        taskToPy.setDatasets(buildDatasetConfigs(taskDataGroups));
        return taskToPy;
    }

    private List<TaskDataGroup> resolvePythonRequestGroups(Task task, List<TaskDataGroup> persistedTaskDataGroups)
    {
        List<TaskDataGroup> requestDataGroups = task == null ? null : task.getRequestDataGroups();
        if (requestDataGroups == null || requestDataGroups.isEmpty())
        {
            return persistedTaskDataGroups;
        }
        return requestDataGroups;
    }

    private TaskToPy.BasicConfig buildBasicConfig(Task task)
    {
        TaskToPy.BasicConfig basicConfig = new TaskToPy.BasicConfig();
        basicConfig.setMotionModel(resolveHostTrajectoryType(task.getMotionModel()));
        basicConfig.setStartCoords(requireCoordinate(task.getStartCoordinate(), "start coordinate"));
        basicConfig.setEndCoords(requireCoordinate(task.getEndCoordinate(), "end coordinate"));
        return basicConfig;
    }

    private Map<String, TaskToPy.DatasetConfig> buildDatasetConfigs(List<TaskDataGroup> taskDataGroups)
    {
        List<TaskDataGroup> groups = requireTaskDataGroups(taskDataGroups);
        List<TaskDataGroup> sortedGroups = new ArrayList<>(groups);
        sortedGroups.sort(Comparator
                .comparing(TaskListener::resolveSortNoOrderValue)
                .thenComparing(TaskDataGroup::getId, Comparator.nullsLast(Long::compareTo)));
        Map<String, TaskToPy.DatasetConfig> datasets = new LinkedHashMap<>();
        for (TaskDataGroup taskDataGroup : sortedGroups)
        {
            String datasetKey = resolveDatasetKey(taskDataGroup);
            TaskToPy.DatasetConfig previous = datasets.put(datasetKey, buildDatasetConfig(taskDataGroup, datasetKey));
            if (previous != null)
            {
                throw new ServiceException("duplicate dataset key: " + datasetKey);
            }
        }
        return datasets;
    }

    private static Integer resolveSortNoOrderValue(TaskDataGroup taskDataGroup)
    {
        if (taskDataGroup == null || taskDataGroup.getSortNo() == null)
        {
            return Integer.MAX_VALUE;
        }
        return taskDataGroup.getSortNo();
    }

    private TaskToPy.DatasetConfig buildDatasetConfig(TaskDataGroup taskDataGroup, String datasetKey)
    {
        TaskToPy.DatasetConfig datasetConfig = new TaskToPy.DatasetConfig();
        boolean enabled = !Boolean.FALSE.equals(taskDataGroup.getEnabled());
        datasetConfig.setEnabled(enabled);
        if (enabled)
        {
            datasetConfig.setFilename(buildDatasetFilename(taskDataGroup));
            datasetConfig.setFlightStartDatetime(requireDate(taskDataGroup.getStartTimeMs(), datasetKey + " start time"));
            datasetConfig.setFlightEndDatetime(requireDate(taskDataGroup.getEndTimeMs(), datasetKey + " end time"));
            datasetConfig.setSampleRateHz(requireValue(taskDataGroup.getFrequencyHz(), datasetKey + " sample rate"));
            applyTargetNum(datasetConfig, datasetKey, taskDataGroup.getTargetNum());
            datasetConfig.setVariables(buildVariableConfigs(taskDataGroup.getMetrics()));
            return datasetConfig;
        }

        datasetConfig.setFilename(buildOptionalDatasetFilename(taskDataGroup));
        if (taskDataGroup.getStartTimeMs() != null)
        {
            datasetConfig.setFlightStartDatetime(new Date(taskDataGroup.getStartTimeMs()));
        }
        if (taskDataGroup.getEndTimeMs() != null)
        {
            datasetConfig.setFlightEndDatetime(new Date(taskDataGroup.getEndTimeMs()));
        }
        if (taskDataGroup.getFrequencyHz() != null)
        {
            datasetConfig.setSampleRateHz(taskDataGroup.getFrequencyHz());
        }
        applyTargetNum(datasetConfig, datasetKey, taskDataGroup.getTargetNum());
        datasetConfig.setVariables(buildVariableConfigs(taskDataGroup.getMetrics()));
        return datasetConfig;
    }

    private Map<String, TaskToPy.VariableConfig> buildVariableConfigs(List<TaskDataMetric> metrics)
    {
        Map<String, TaskToPy.VariableConfig> variables = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty())
        {
            return variables;
        }

        for (TaskDataMetric metric : metrics)
        {
            String fieldName = trimToNull(metric.getFieldName());
            if (fieldName == null)
            {
                continue;
            }

            TaskToPy.VariableConfig variableConfig = new TaskToPy.VariableConfig();
            variableConfig.setDataType(metric.getDataType());
            variableConfig.setRecommendedValue(metric.getRecommendedValue());
            variableConfig.setFluctuationRange(metric.getFluctuationRange());
            variableConfig.setDescription(metric.getDescription());
            variableConfig.setSortNo(metric.getSortNo());
            variables.put(fieldName, variableConfig);
        }
        return variables;
    }

    private void fillMetrics(List<TaskDataGroup> groups)
    {
        if (groups == null || groups.isEmpty())
        {
            return;
        }

        List<Long> groupIds = groups.stream()
                .map(TaskDataGroup::getId)
                .filter(id -> id != null)
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

    private List<TaskDataGroup> requireTaskDataGroups(List<TaskDataGroup> taskDataGroups)
    {
        if (taskDataGroups == null || taskDataGroups.isEmpty())
        {
            throw new ServiceException("task data groups are required");
        }
        return taskDataGroups;
    }

    private String resolveRequestId(Task task)
    {
        if (StringUtils.isNotEmpty(task.getTaskCode()))
        {
            return task.getTaskCode().trim();
        }
        if (task.getId() != null)
        {
            return "task-" + task.getId();
        }
        throw new ServiceException("task code is required");
    }

    private String resolveDatasetKey(TaskDataGroup taskDataGroup)
    {
        if (StringUtils.isNotEmpty(taskDataGroup.getGroupName()))
        {
            return taskDataGroup.getGroupName().trim();
        }
        return requireText(taskDataGroup.getDataName(), "dataset key");
    }

    private String buildDatasetFilename(TaskDataGroup taskDataGroup)
    {
        String dataName = requireText(taskDataGroup.getDataName(), "dataset data name");
        String outputType = requireText(taskDataGroup.getOutputType(), "dataset output type");
        return buildDatasetFilename(dataName, outputType);
    }

    private String buildOptionalDatasetFilename(TaskDataGroup taskDataGroup)
    {
        String dataName = trimToNull(taskDataGroup.getDataName());
        String outputType = trimToNull(taskDataGroup.getOutputType());
        if (dataName == null || outputType == null)
        {
            return null;
        }
        return buildDatasetFilename(dataName, outputType);
    }

    private String buildDatasetFilename(String dataName, String outputType)
    {
        String normalizedOutputType = outputType.startsWith(".")
                ? outputType.substring(1).trim()
                : outputType.trim();
        String lowerCaseFileName = dataName.toLowerCase(Locale.ROOT);
        String lowerCaseSuffix = "." + normalizedOutputType.toLowerCase(Locale.ROOT);
        if (lowerCaseFileName.endsWith(lowerCaseSuffix))
        {
            return dataName;
        }
        return dataName + "." + normalizedOutputType;
    }

    private void applyTargetNum(TaskToPy.DatasetConfig datasetConfig, String datasetKey, Integer targetNum)
    {
        if (targetNum == null)
        {
            return;
        }

        switch (datasetKey.toLowerCase(Locale.ROOT))
        {
            case "radar_track":
                datasetConfig.setEnemyNum(targetNum);
                break;
            case "ads_b":
            case "adsb":
                datasetConfig.setFriendlyNum(targetNum);
                break;
            default:
                datasetConfig.setTargetNum(targetNum);
                break;
        }
    }

    private String requireText(String value, String fieldName)
    {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null)
        {
            throw new ServiceException(fieldName + " is required");
        }
        return normalizedValue;
    }

    private String trimToNull(String value)
    {
        if (StringUtils.isEmpty(value))
        {
            return null;
        }
        String normalizedValue = value.trim();
        return StringUtils.isEmpty(normalizedValue) ? null : normalizedValue;
    }

    private Coordinate requireCoordinate(Coordinate coordinate, String fieldName)
    {
        if (coordinate == null)
        {
            throw new ServiceException(fieldName + " is required");
        }
        return coordinate;
    }

    private Date requireDate(Long epochMillis, String fieldName)
    {
        if (epochMillis == null)
        {
            throw new ServiceException(fieldName + " is required");
        }
        return new Date(epochMillis);
    }

    private <T> T requireValue(T value, String fieldName)
    {
        if (value == null)
        {
            throw new ServiceException(fieldName + " is required");
        }
        return value;
    }

    private String resolveHostTrajectoryType(String motionModel)
    {
        if (StringUtils.isEmpty(motionModel))
        {
            return "cubic";
        }

        switch (motionModel.trim())
        {
            case "straight":
            case "STRAIGHT":
            case "\u76f4\u7ebf\u6a21\u578b":
            case "LINEAR":
                return "straight";
            case "quadratic":
            case "QUADRATIC":
            case "\u4e8c\u6b21\u66f2\u7ebf":
            case "QUADRATIC_CURVE":
                return "quadratic";
            case "cubic":
            case "CUBIC":
            case "\u4e09\u6b21\u66f2\u7ebf":
            case "CUBIC_CURVE":
                return "cubic";
            case "two_segment":
            case "TWO_SEGMENT":
            case "\u6298\u7ebf\u6a21\u578b":
            case "\u4e8c\u6298\u7ebf":
            case "POLYLINE_2":
                return "two_segment";
            case "three_segment":
            case "THREE_SEGMENT":
            case "\u4e09\u6298\u7ebf":
            case "POLYLINE_3":
                return "three_segment";
            case "\u76d8\u65cb\u6a21\u578b":
            case "\u673a\u52a8\u6a21\u578b":
            case "\u968f\u673a\u66f2\u7ebf":
            case "RANDOM_CURVE":
            default:
                return "cubic";
        }
    }

    private void notifyTaskSummaryIfCompleted(Long taskId)
    {
        if (taskId == null)
        {
            return;
        }

        log.info("Notify task summary check started, taskId={}", taskId);
        Task task = taskMapper.selectById(taskId);
        if (task == null || StringUtils.isEmpty(task.getCreateBy()))
        {
            log.info("Skip task summary notification because task or creator is missing, taskId={}", taskId);
            return;
        }

        SysUser user = sysUserService.selectUserById(task.getCreateUserID());
        if (user == null || user.getUserId() == null)
        {
            log.info("Skip task summary notification because user is missing, taskId={}, createBy={}", taskId, task.getCreateBy());
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "simulation_task_summary");
        payload.put("taskId", taskId);
        payload.put("taskName", task.getTaskName());
        payload.put("status", TaskStatusEnum.SUCCESS.toString());
        payload.put("message", String.format(
                "\u4efb\u52a1\"%s\"\u5df2\u5b8c\u6210",
                task.getTaskName()));

        try
        {
            webSocketServer.sendText(user.getUserId(), objectMapper.writeValueAsString(payload));
            log.info("Task summary notification sent, taskId={}, userId={}", taskId, user.getUserId());
        }
        catch (Exception ignored)
        {
            log.warn("Task summary notification failed, taskId={}, userId={}", taskId, user.getUserId());
        }
    }

    private String getFilePath(JsonNode taskResponse, String fileKey)
    {
        JsonNode filesNode = taskResponse.path("files");
        return filesNode.path(fileKey).asText(null);
    }

    private String copyFile(String sourcePath, String targetDir, String sourceName) throws IOException
    {
        Path source = Paths.get(sourcePath);
        Path target = Paths.get(targetDir);
        try
        {
            if (!Files.exists(source))
            {
                throw new ServerException("source file does not exist");
            }
            if (!Files.exists(target))
            {
                Files.createDirectories(target);
            }
            String suffix = sourceName.substring(sourceName.lastIndexOf("."));
            sourceName = sourceName.substring(0, sourceName.lastIndexOf("."));
            Path targetFile = target.resolve(sourceName + suffix);
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return targetFile.getFileName().toString();
        }
        catch (Exception exception)
        {
            throw new ServerException("failed to import file");
        }
    }

    private void deleteFile(String path) throws IOException
    {
        Path path1 = Paths.get(path);
        try
        {
            Files.deleteIfExists(path1);
        }
        catch (Exception exception)
        {
            throw new ServerException("failed to delete file");
        }
    }
}
