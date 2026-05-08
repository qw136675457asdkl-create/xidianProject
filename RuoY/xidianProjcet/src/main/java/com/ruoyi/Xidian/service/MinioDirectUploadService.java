package com.ruoyi.Xidian.service;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ruoyi.Xidian.config.MinioProperties;
import com.ruoyi.Xidian.domain.DTO.FolderUploadInitRequest;
import com.ruoyi.Xidian.domain.DTO.FolderUploadInitResponse;
import com.ruoyi.Xidian.domain.DTO.MinioUploadCompleteRequest;
import com.ruoyi.Xidian.domain.DTO.MinioUploadCompleteResponse;
import com.ruoyi.Xidian.domain.DTO.MinioUploadInitRequest;
import com.ruoyi.Xidian.domain.DTO.MinioUploadInitResponse;
import com.ruoyi.Xidian.domain.DTO.MinioUploadStatusResponse;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadCompleteRequest;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadCompleteResponse;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadInitRequest;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadInitResponse;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadPartCompleteRequest;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadPartUrlRequest;
import com.ruoyi.Xidian.domain.DTO.MultipartUploadPartUrlResponse;
import com.ruoyi.Xidian.domain.MdFileStorage;
import com.ruoyi.Xidian.domain.enums.FileStorageProviderEnum;
import com.ruoyi.Xidian.domain.enums.FileStorageStatusEnum;
import com.ruoyi.Xidian.domain.enums.MinioBusinessTypeEnum;
import com.ruoyi.Xidian.mapper.MdFileStorageMapper;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import io.minio.BucketExistsArgs;
import io.minio.CreateMultipartUploadResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class MinioDirectUploadService {
    private static final int DEFAULT_EXPIRE_SECONDS = 600;
    private static final int MAX_EXPIRE_SECONDS = 7 * 24 * 60 * 60;
    private static final String DEFAULT_OBJECT_PREFIX = "direct_upload";
    private static final DateTimeFormatter DATE_PATH_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter FOLDER_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> OBJECT_NOT_FOUND_CODES = Set.of("NoSuchKey", "NoSuchObject", "NoSuchBucket", "NotFound");
    private static final long DEFAULT_MULTIPART_SIZE = 32L * 1024 * 1024;
    private static final long MIN_MULTIPART_SIZE = 5L * 1024 * 1024;
    private static final long MAX_PART_COUNT = 10000L;
    // Multipart state lives in Redis so abandoned uploads can expire automatically.
    private static final long MULTIPART_SESSION_TTL_HOURS = 24L;
    private static final String MULTIPART_SESSION_KEY_PREFIX = "minio:multipart:session:";
    private static final String MULTIPART_PARTS_KEY_PREFIX = "minio:multipart:parts:";
    private static final String MULTIPART_STATUS_SUCCESS = "SUCCESS";

    private final MinioClient minioClient;
    private final MultipartAsyncSupport multipartSupport;
    private final MinioProperties minioProperties;
    private final MdFileStorageMapper mdFileStorageMapper;
    private final RedisCache redisCache;

    public MinioDirectUploadService(MinioClient minioClient,
                                    MinioProperties minioProperties,
                                    MdFileStorageMapper mdFileStorageMapper,
                                    RedisCache redisCache) {
        this.minioClient = minioClient;
        this.multipartSupport = new MultipartAsyncSupport(resolveAsyncClient(minioClient));
        this.minioProperties = minioProperties;
        this.mdFileStorageMapper = mdFileStorageMapper;
        this.redisCache = redisCache;
    }

    @Transactional(rollbackFor = Exception.class)
    public MinioUploadInitResponse createPresignedUpload(MinioUploadInitRequest request) {
        Long userId = requireCurrentUserId();
        String username = resolveCurrentUsername();
        String bucket = getRequiredBucket();
        ensureBucketExists(bucket);

        String businessType = firstNonBlank(trimToNull(request.getBusinessType()), MinioBusinessTypeEnum.DATA_RELATION.getCode());
        String businessId = trimToNull(request.getBusinessId());
        String originalFileName = resolveInitOriginalFileName(request);
        String objectName = buildObjectName(userId, request, businessType);
        StatObjectResponse statObjectResponse = statObjectIfExists(bucket, objectName);

        if (isFolderUploadRequest(request)) {
            return buildInitResponse(
                    bucket,
                    objectName,
                    statObjectResponse == null ? createPresignedPutUrl(bucket, objectName) : null,
                    originalFileName,
                    trimToNull(request.getContentType()),
                    request.getFileSize(),
                    businessType,
                    businessId,
                    statObjectResponse != null,
                    statObjectResponse == null,
                    statObjectResponse == null ? FileStorageStatusEnum.INIT.getCode() : FileStorageStatusEnum.UPLOADED.getCode(),
                    statObjectResponse == null ? null : statObjectResponse.etag()
            );
        }

        MdFileStorage existingStorage = mdFileStorageMapper.selectByBucketAndObjectName(bucket, objectName);
        if (existingStorage != null && statObjectResponse != null) {
            return buildInitResponse(
                    bucket,
                    objectName,
                    null,
                    existingStorage.getOriginalFileName(),
                    existingStorage.getContentType(),
                    existingStorage.getFileSize(),
                    existingStorage.getBusinessType(),
                    existingStorage.getBusinessId(),
                    true,
                    false,
                    FileStorageStatusEnum.UPLOADED.getCode(),
                    statObjectResponse.etag()
            );
        }

        mdFileStorageMapper.insertMdFileStorage(buildInitFileStorage(
                bucket,
                objectName,
                originalFileName,
                trimToNull(request.getContentType()),
                request.getFileSize(),
                businessType,
                businessId,
                userId,
                username
        ));

        return buildInitResponse(
                bucket,
                objectName,
                statObjectResponse == null ? createPresignedPutUrl(bucket, objectName) : null,
                originalFileName,
                trimToNull(request.getContentType()),
                request.getFileSize(),
                businessType,
                businessId,
                statObjectResponse != null,
                statObjectResponse == null,
                statObjectResponse == null ? FileStorageStatusEnum.INIT.getCode() : FileStorageStatusEnum.UPLOADED.getCode(),
                statObjectResponse == null ? null : statObjectResponse.etag()
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public FolderUploadInitResponse initializeFolderUpload(FolderUploadInitRequest request) {
        Long userId = requireCurrentUserId();
        String username = resolveCurrentUsername();
        String bucket = getRequiredBucket();
        ensureBucketExists(bucket);

        String folderName = resolveFolderName(request.getFolderName());
        String folderObjectName = buildFolderRootObjectName(
                userId,
                folderName,
                request.getFolderUploadId(),
                request.getFolderUploadDate()
        );

        MdFileStorage currentFolderStorage = mdFileStorageMapper.selectByBucketAndObjectName(bucket, folderObjectName);
        if (currentFolderStorage != null) {
            validateFolderStorageOwner(currentFolderStorage, userId);
            if (StringUtils.hasText(trimToNull(currentFolderStorage.getBusinessId()))) {
                return buildFolderInitResponse(currentFolderStorage, true, "Folder already exists.");
            }
            refreshFolderStorageForResume(currentFolderStorage, request.getTotalSize(), username);
            return buildFolderInitResponse(currentFolderStorage, false, "Folder upload initialized.");
        }

        List<MdFileStorage> sameNameFolderStorageList = mdFileStorageMapper.selectFolderStorageListByName(
                bucket,
                folderName,
                userId
        );
        if (sameNameFolderStorageList != null && !sameNameFolderStorageList.isEmpty()) {
            MdFileStorage latestFolderStorage = sameNameFolderStorageList.get(0);
            if (StringUtils.hasText(trimToNull(latestFolderStorage.getBusinessId()))) {
                return buildFolderInitResponse(latestFolderStorage, true, "Folder already exists.");
            }
            return buildFolderInitResponse(latestFolderStorage, true, "Folder upload already exists.");
        }

        MdFileStorage folderStorage = buildFolderStorage(
                bucket,
                folderObjectName,
                folderName,
                request.getTotalSize(),
                userId,
                username
        );
        mdFileStorageMapper.insertMdFileStorage(folderStorage);
        return buildFolderInitResponse(folderStorage, false, "Folder upload initialized.");
    }

    @Transactional(rollbackFor = Exception.class)
    public MinioUploadCompleteResponse completeUpload(MinioUploadCompleteRequest request) {
        Long userId = requireCurrentUserId();
        String bucket = getRequiredBucket();
        String objectName = normalizeObjectName(request.getObjectName());
        validateObjectAccess(userId, objectName);

        StatObjectResponse statObjectResponse = statObjectIfExists(bucket, objectName);
        if (statObjectResponse == null) {
            throw new ServiceException("Uploaded object was not found.");
        }

        markStorageUploaded(bucket, objectName, statObjectResponse.etag());

        MinioUploadCompleteResponse response = new MinioUploadCompleteResponse();
        response.setBucket(bucket);
        response.setObjectName(objectName);
        response.setExists(true);
        response.setStatus(FileStorageStatusEnum.UPLOADED.getCode());
        response.setSize(statObjectResponse.size());
        response.setEtag(statObjectResponse.etag());
        return response;
    }

    public MinioUploadStatusResponse queryUploadStatus(String objectName) {
        Long userId = requireCurrentUserId();
        String bucket = getRequiredBucket();
        String normalizedObjectName = normalizeObjectName(objectName);
        validateObjectAccess(userId, normalizedObjectName);

        StatObjectResponse statObjectResponse = statObjectIfExists(bucket, normalizedObjectName);
        MinioUploadStatusResponse response = new MinioUploadStatusResponse();
        response.setBucket(bucket);
        response.setObjectName(normalizedObjectName);
        response.setExists(statObjectResponse != null);
        response.setStatus(statObjectResponse != null ? FileStorageStatusEnum.UPLOADED.getCode() : FileStorageStatusEnum.FAILED.getCode());
        if (statObjectResponse != null) {
            response.setSize(statObjectResponse.size());
            response.setEtag(statObjectResponse.etag());
        }
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public MultipartUploadInitResponse initMultipartUpload(MultipartUploadInitRequest request) {
        Long userId = requireCurrentUserId();
        String username = resolveCurrentUsername();
        String bucket = getRequiredBucket();
        ensureBucketExists(bucket);

        long fileSize = request.getFileSize() == null ? 0L : request.getFileSize();
        if (fileSize <= 0) {
            throw new ServiceException("fileSize must be greater than 0");
        }

        String businessType = firstNonBlank(trimToNull(request.getBusinessType()), MinioBusinessTypeEnum.DATA_RELATION.getCode());
        String businessId = trimToNull(request.getBusinessId());
        String objectName = buildDefaultObjectName(userId, request.getFileName(), businessType);
        String originalFileName = extractFileName(request.getFileName());
        String contentType = trimToNull(request.getContentType());
        long partSize = calcMultipartPartSize(fileSize);
        int totalParts = calcMultipartPartCount(fileSize, partSize);

        CreateMultipartUploadResponse uploadResponse = multipartSupport.createMultipartUpload(bucket, objectName, contentType);

        MdFileStorage fileStorage = buildInitFileStorage(
                bucket,
                objectName,
                originalFileName,
                contentType,
                fileSize,
                businessType,
                businessId,
                userId,
                username
        );
        mdFileStorageMapper.insertMdFileStorage(fileStorage);
        String uploadId = String.valueOf(fileStorage.getId());

        Map<String, Object> sessionState = buildMultipartSessionState(
                fileStorage.getId(),
                bucket,
                objectName,
                fileSize,
                uploadResponse.result().uploadId(),
                partSize,
                totalParts
        );
        cacheMultipartSessionState(fileStorage.getId(), sessionState);
        log.info("Initialized multipart upload session: storageFileId={}, objectName={}, totalParts={}, partSize={}",
                fileStorage.getId(), objectName, totalParts, partSize);

        MultipartUploadInitResponse response = new MultipartUploadInitResponse();
        response.setUploadId(uploadId);
        response.setBucket(bucket);
        response.setObjectName(objectName);
        response.setOriginalFileName(originalFileName);
        response.setContentType(contentType);
        response.setFileSize(fileSize);
        response.setPartSize(partSize);
        response.setTotalParts(totalParts);
        return response;
    }

    public MultipartUploadPartUrlResponse createMultipartPartUploadUrl(MultipartUploadPartUrlRequest request) {
        MultipartUploadContext context = getRequiredMultipartUploadContext(request.getUploadId());
        validatePartNumber(request.getPartNumber(), readRequiredInt(context.getSessionState(), "totalParts"));

        MultipartUploadPartUrlResponse response = new MultipartUploadPartUrlResponse();
        response.setUploadId(String.valueOf(context.getStorageFileId()));
        response.setPartNumber(request.getPartNumber());
        response.setUploadMethod(Method.PUT.name());
        response.setExpireSeconds(resolveExpireSeconds());
        response.setUploadUrl(createPresignedPartUploadUrl(
                context.getStorage().getBucketName(),
                context.getStorage().getObjectName(),
                readRequiredString(context.getSessionState(), "minioUploadId"),
                request.getPartNumber()
        ));
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordMultipartUploadPart(MultipartUploadPartCompleteRequest request) {
        MultipartUploadContext context = getRequiredMultipartUploadContext(request.getUploadId());
        validatePartNumber(request.getPartNumber(), readRequiredInt(context.getSessionState(), "totalParts"));

        String etag = trimToNull(request.getEtag());
        if (etag == null) {
            throw new ServiceException("etag is required");
        }

        redisCache.setCacheMapValue(
                buildMultipartPartsKey(context.getStorageFileId()),
                String.valueOf(request.getPartNumber()),
                etag
        );
        refreshMultipartSessionTtl(context.getStorageFileId());
        log.info("Recorded multipart upload part: storageFileId={}, partNumber={}",
                context.getStorageFileId(), request.getPartNumber());
    }

    @Transactional(rollbackFor = Exception.class)
    public MultipartUploadCompleteResponse completeMultipartUpload(MultipartUploadCompleteRequest request) {
        MultipartUploadContext context = getRequiredMultipartUploadContext(request.getUploadId());
        int totalParts = readRequiredInt(context.getSessionState(), "totalParts");
        Map<String, String> partState = redisCache.getCacheMap(buildMultipartPartsKey(context.getStorageFileId()));
        if (partState == null || partState.size() != totalParts) {
            throw new ServiceException("Multipart upload is not complete.");
        }

        // MinIO requires the completed part list to be ordered by part number.
        List<Part> completedParts = new ArrayList<>(totalParts);
        for (int partNumber = 1; partNumber <= totalParts; partNumber++) {
            String etag = trimToNull(partState.get(String.valueOf(partNumber)));
            if (etag == null) {
                throw new ServiceException("Multipart upload is not complete.");
            }
            completedParts.add(new Part(partNumber, etag));
        }

        ObjectWriteResponse completeResponse = multipartSupport.completeMultipartUpload(
                context.getStorage().getBucketName(),
                context.getStorage().getObjectName(),
                readRequiredString(context.getSessionState(), "minioUploadId"),
                completedParts.toArray(new Part[0])
        );

        String username = resolveCurrentUsername();
        Date now = new Date();
        MdFileStorage storage = context.getStorage();
        storage.setUploadStatus(FileStorageStatusEnum.UPLOADED.getCode());
        storage.setEtag(completeResponse.etag());
        storage.setCompletedTime(now);
        storage.setUpdateBy(username);
        storage.setUpdateTime(now);
        mdFileStorageMapper.updateMdFileStorage(storage);
        clearMultipartSessionState(context.getStorageFileId());
        log.info("Completed multipart upload: storageFileId={}, objectName={}, totalParts={}",
                context.getStorageFileId(), storage.getObjectName(), totalParts);

        MultipartUploadCompleteResponse response = new MultipartUploadCompleteResponse();
        response.setUploadId(String.valueOf(context.getStorageFileId()));
        response.setBucket(storage.getBucketName());
        response.setObjectName(storage.getObjectName());
        response.setEtag(completeResponse.etag());
        response.setStatus(MULTIPART_STATUS_SUCCESS);
        response.setStorageFileId(storage.getId());
        return response;
    }

    private Long requireCurrentUserId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("Current user is required");
        }
        return userId;
    }

    private Map<String, Object> buildMultipartSessionState(Long storageFileId,
                                                           String bucket,
                                                           String objectName,
                                                           Long fileSize,
                                                           String minioUploadId,
                                                           Long partSize,
                                                           Integer totalParts) {
        Map<String, Object> sessionState = new LinkedHashMap<>();
        sessionState.put("storageFileId", storageFileId);
        sessionState.put("bucketName", bucket);
        sessionState.put("objectName", objectName);
        sessionState.put("fileSize", fileSize);
        sessionState.put("minioUploadId", minioUploadId);
        sessionState.put("partSize", partSize);
        sessionState.put("totalParts", totalParts);
        return sessionState;
    }

    private void cacheMultipartSessionState(Long storageFileId, Map<String, Object> sessionState) {
        String sessionKey = buildMultipartSessionKey(storageFileId);
        redisCache.setCacheMap(sessionKey, sessionState);
        redisCache.expire(sessionKey, MULTIPART_SESSION_TTL_HOURS, TimeUnit.HOURS);
    }

    private MultipartUploadContext getRequiredMultipartUploadContext(String uploadId) {
        Long storageFileId = parseUploadId(uploadId);
        MdFileStorage storage = mdFileStorageMapper.selectById(storageFileId);
        if (storage == null) {
            throw new ServiceException("Upload storage record does not exist.");
        }
        validateMultipartStorageAccess(storage);

        Map<String, Object> sessionState = redisCache.getCacheMap(buildMultipartSessionKey(storageFileId));
        if (sessionState == null || sessionState.isEmpty()) {
            throw new ServiceException("Upload session has expired.");
        }

        refreshMultipartSessionTtl(storageFileId);
        return new MultipartUploadContext(storageFileId, storage, sessionState);
    }

    private Long parseUploadId(String uploadId) {
        String normalizedUploadId = trimToNull(uploadId);
        if (normalizedUploadId == null) {
            throw new ServiceException("uploadId is required");
        }
        try {
            return Long.valueOf(normalizedUploadId);
        } catch (NumberFormatException ex) {
            throw new ServiceException("uploadId is invalid");
        }
    }

    private void validateMultipartStorageAccess(MdFileStorage storage) {
        Long currentUserId = requireCurrentUserId();
        if (storage.getUploadUserId() != null && !storage.getUploadUserId().equals(currentUserId)) {
            throw new ServiceException("You do not have permission to access this upload session.");
        }
    }

    private void validatePartNumber(Integer partNumber, Integer totalParts) {
        if (partNumber == null || partNumber <= 0) {
            throw new ServiceException("partNumber is invalid");
        }
        if (totalParts != null && partNumber > totalParts) {
            throw new ServiceException("partNumber is out of range");
        }
    }

    private String buildMultipartSessionKey(Long storageFileId) {
        return MULTIPART_SESSION_KEY_PREFIX + storageFileId;
    }

    private String buildMultipartPartsKey(Long storageFileId) {
        return MULTIPART_PARTS_KEY_PREFIX + storageFileId;
    }

    private void refreshMultipartSessionTtl(Long storageFileId) {
        redisCache.expire(buildMultipartSessionKey(storageFileId), MULTIPART_SESSION_TTL_HOURS, TimeUnit.HOURS);
        redisCache.expire(buildMultipartPartsKey(storageFileId), MULTIPART_SESSION_TTL_HOURS, TimeUnit.HOURS);
    }

    private void clearMultipartSessionState(Long storageFileId) {
        redisCache.deleteObject(buildMultipartSessionKey(storageFileId));
        redisCache.deleteObject(buildMultipartPartsKey(storageFileId));
    }

    private String readRequiredString(Map<String, Object> source, String key) {
        String value = trimToNull(source == null ? null : String.valueOf(source.get(key)));
        if (value == null || "null".equalsIgnoreCase(value)) {
            throw new ServiceException("Upload session is invalid.");
        }
        return value;
    }

    private int readRequiredInt(Map<String, Object> source, String key) {
        Object value = source == null ? null : source.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        String text = trimToNull(value == null ? null : String.valueOf(value));
        if (text == null) {
            throw new ServiceException("Upload session is invalid.");
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new ServiceException("Upload session is invalid.");
        }
    }

    private static final class MultipartUploadContext {
        private final Long storageFileId;
        private final MdFileStorage storage;
        private final Map<String, Object> sessionState;

        private MultipartUploadContext(Long storageFileId, MdFileStorage storage, Map<String, Object> sessionState) {
            this.storageFileId = storageFileId;
            this.storage = storage;
            this.sessionState = sessionState;
        }

        private Long getStorageFileId() {
            return storageFileId;
        }

        private MdFileStorage getStorage() {
            return storage;
        }

        private Map<String, Object> getSessionState() {
            return sessionState;
        }
    }

    private void markStorageUploaded(String bucket, String objectName, String etag) {
        MdFileStorage fileStorage = mdFileStorageMapper.selectByBucketAndObjectName(bucket, objectName);
        if (fileStorage == null) {
            return;
        }

        Date now = new Date();
        fileStorage.setUploadStatus(FileStorageStatusEnum.UPLOADED.getCode());
        fileStorage.setEtag(etag);
        fileStorage.setCompletedTime(now);
        fileStorage.setUpdateBy(resolveCurrentUsername());
        fileStorage.setUpdateTime(now);
        mdFileStorageMapper.updateMdFileStorage(fileStorage);
    }

    private String createPresignedPutUrl(String bucket, String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(resolveExpireSeconds())
                            .build()
            );
        } catch (Exception ex) {
            throw new ServiceException("Failed to create MinIO upload URL: " + ex.getMessage());
        }
    }

    private String createPresignedPartUploadUrl(String bucket,
                                                String objectName,
                                                String minioUploadId,
                                                Integer partNumber) {
        try {
            Map<String, String> queryParams = new LinkedHashMap<>();
            queryParams.put("partNumber", String.valueOf(partNumber));
            queryParams.put("uploadId", minioUploadId);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectName)
                            .expiry(resolveExpireSeconds())
                            .extraQueryParams(queryParams)
                            .build()
            );
        } catch (Exception ex) {
            throw new ServiceException("Failed to create MinIO part upload URL: " + ex.getMessage());
        }
    }

    private void ensureBucketExists(String bucket) {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
            }
        } catch (Exception ex) {
            throw new ServiceException("Failed to initialize MinIO bucket: " + ex.getMessage());
        }
    }

    private StatObjectResponse statObjectIfExists(String bucket, String objectName) {
        try {
            return minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .build()
            );
        } catch (ErrorResponseException ex) {
            if (ex.errorResponse() != null && OBJECT_NOT_FOUND_CODES.contains(ex.errorResponse().code())) {
                return null;
            }
            throw new ServiceException("Failed to query MinIO object status: " + ex.getMessage());
        } catch (Exception ex) {
            throw new ServiceException("Failed to query MinIO object status: " + ex.getMessage());
        }
    }

    private String buildObjectName(Long userId, MinioUploadInitRequest request, String businessType) {
        if (isFolderUploadRequest(request)) {
            return buildFolderObjectName(userId, request);
        }
        return buildDefaultObjectName(userId, request.getFileName(), businessType);
    }

    private String buildDefaultObjectName(Long userId, String fileName, String businessType) {
        String prefix = resolveObjectPrefix();
        String businessSegment = normalizeBusinessSegment(businessType);
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        String extension = extractExtension(fileName);
        return prefix + "/" + businessSegment + "/" + datePath + "/user-" + userId + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension;
    }

    private String buildFolderObjectName(Long userId, MinioUploadInitRequest request) {
        String folderName = normalizeObjectNameSegment(request.getFolderName());
        String relativePath = normalizeFolderRelativePath(
                firstNonBlank(trimToNull(request.getRelativePath()), trimToNull(request.getFileName())),
                folderName
        );
        if (!StringUtils.hasText(folderName)
                || !StringUtils.hasText(trimToNull(request.getFolderUploadId()))
                || !StringUtils.hasText(relativePath)) {
            throw new ServiceException("Invalid folder upload parameters");
        }
        return buildFolderRootObjectName(userId, folderName, request.getFolderUploadId(), request.getFolderUploadDate())
                + "/" + relativePath;
    }

    private String buildFolderRootObjectName(Long userId,
                                             String folderName,
                                             String folderUploadId,
                                             String folderUploadDate) {
        String safeFolderName = normalizeObjectNameSegment(folderName);
        String safeFolderUploadId = normalizeObjectNameSegment(folderUploadId);
        String uploadDate = resolveFolderUploadDate(folderUploadDate);
        if (!StringUtils.hasText(safeFolderName) || !StringUtils.hasText(safeFolderUploadId)) {
            throw new ServiceException("Invalid folder upload parameters");
        }
        return resolveObjectPrefix() + "/" + uploadDate + "/user-" + userId + "/" + safeFolderName + "-" + safeFolderUploadId;
    }

    private void validateObjectAccess(Long userId, String objectName) {
        String prefix = resolveObjectPrefix() + "/";
        String userSegment = "/user-" + userId + "/";
        if (!objectName.startsWith(prefix) || !objectName.contains(userSegment)) {
            throw new ServiceException("You do not have permission to access this upload object.");
        }
    }

    private MdFileStorage buildInitFileStorage(String bucket,
                                               String objectName,
                                               String originalFileName,
                                               String contentType,
                                               Long fileSize,
                                               String businessType,
                                               String businessId,
                                               Long userId,
                                               String username) {
        Date now = new Date();
        MdFileStorage fileStorage = new MdFileStorage();
        fileStorage.setBusinessType(businessType);
        fileStorage.setBusinessId(businessId);
        fileStorage.setStorageProvider(FileStorageProviderEnum.MINIO.getCode());
        fileStorage.setBucketName(bucket);
        fileStorage.setObjectName(objectName);
        fileStorage.setOriginalFileName(originalFileName);
        fileStorage.setFileExt(resolveFileExtension(originalFileName, objectName));
        fileStorage.setContentType(contentType);
        fileStorage.setFileSize(fileSize);
        fileStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        fileStorage.setUploadUserId(userId);
        fileStorage.setUploadUserName(username);
        fileStorage.setIsFolder(Boolean.FALSE);
        fileStorage.setCreateBy(username);
        fileStorage.setCreateTime(now);
        fileStorage.setUpdateBy(username);
        fileStorage.setUpdateTime(now);
        return fileStorage;
    }

    private MdFileStorage buildFolderStorage(String bucket,
                                             String folderObjectName,
                                             String folderName,
                                             Long totalSize,
                                             Long userId,
                                             String username) {
        Date now = new Date();
        MdFileStorage fileStorage = new MdFileStorage();
        fileStorage.setBusinessType(MinioBusinessTypeEnum.DATA_RELATION.getCode());
        fileStorage.setStorageProvider(FileStorageProviderEnum.MINIO.getCode());
        fileStorage.setBucketName(bucket);
        fileStorage.setObjectName(folderObjectName);
        fileStorage.setOriginalFileName(folderName);
        fileStorage.setFileExt("");
        fileStorage.setContentType("application/x-directory");
        fileStorage.setFileSize(totalSize);
        fileStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        fileStorage.setUploadUserId(userId);
        fileStorage.setUploadUserName(username);
        fileStorage.setIsFolder(Boolean.TRUE);
        fileStorage.setCreateBy(username);
        fileStorage.setCreateTime(now);
        fileStorage.setUpdateBy(username);
        fileStorage.setUpdateTime(now);
        return fileStorage;
    }

    private void refreshFolderStorageForResume(MdFileStorage folderStorage, Long totalSize, String username) {
        folderStorage.setBusinessType(MinioBusinessTypeEnum.DATA_RELATION.getCode());
        folderStorage.setOriginalFileName(resolveFolderName(folderStorage.getOriginalFileName()));
        folderStorage.setFileExt("");
        folderStorage.setContentType("application/x-directory");
        folderStorage.setFileSize(totalSize);
        folderStorage.setUploadStatus(FileStorageStatusEnum.INIT.getCode());
        folderStorage.setIsFolder(Boolean.TRUE);
        folderStorage.setUpdateBy(username);
        folderStorage.setUpdateTime(new Date());
        mdFileStorageMapper.updateMdFileStorage(folderStorage);
    }

    private void validateFolderStorageOwner(MdFileStorage folderStorage, Long userId) {
        if (folderStorage.getUploadUserId() != null && !folderStorage.getUploadUserId().equals(userId)) {
            throw new ServiceException("You do not have permission to access this folder upload.");
        }
    }

    private FolderUploadInitResponse buildFolderInitResponse(MdFileStorage folderStorage,
                                                             boolean folderExists,
                                                             String message) {
        FolderUploadInitResponse response = new FolderUploadInitResponse();
        response.setFolderStorageId(folderStorage == null ? null : folderStorage.getId());
        response.setBucket(folderStorage == null ? null : folderStorage.getBucketName());
        response.setFolderObjectName(folderStorage == null ? null : folderStorage.getObjectName());
        response.setFolderName(folderStorage == null ? null : folderStorage.getOriginalFileName());
        response.setFolderExists(folderExists);
        response.setMessage(message);
        return response;
    }

    private MinioUploadInitResponse buildInitResponse(String bucket,
                                                      String objectName,
                                                      String uploadUrl,
                                                      String originalFileName,
                                                      String contentType,
                                                      Long fileSize,
                                                      String businessType,
                                                      String businessId,
                                                      boolean exists,
                                                      boolean needUpload,
                                                      String status,
                                                      String etag) {
        MinioUploadInitResponse response = new MinioUploadInitResponse();
        response.setBucket(bucket);
        response.setObjectName(objectName);
        response.setUploadUrl(uploadUrl);
        response.setUploadMethod(Method.PUT.name());
        response.setExpireSeconds(resolveExpireSeconds());
        response.setOriginalFileName(originalFileName);
        response.setContentType(contentType);
        response.setFileSize(fileSize);
        response.setBusinessType(businessType);
        response.setBusinessId(businessId);
        response.setExists(exists);
        response.setNeedUpload(needUpload);
        response.setStatus(status);
        response.setEtag(etag);
        return response;
    }

    private String resolveCurrentUsername() {
        return trimToNull(SecurityUtils.getUsername());
    }

    private String getRequiredBucket() {
        String bucket = trimToNull(minioProperties.getBucket());
        if (bucket == null) {
            throw new ServiceException("MinIO bucket is not configured");
        }
        return bucket;
    }

    private int resolveExpireSeconds() {
        Integer configured = minioProperties.getUploadUrlExpirySeconds();
        int expireSeconds = configured == null ? DEFAULT_EXPIRE_SECONDS : configured;
        if (expireSeconds <= 0 || expireSeconds > MAX_EXPIRE_SECONDS) {
            throw new ServiceException("Invalid MinIO presigned URL expiration");
        }
        return expireSeconds;
    }

    private String resolveObjectPrefix() {
        String prefix = trimToNull(minioProperties.getObjectPrefix());
        if (prefix == null) {
            return DEFAULT_OBJECT_PREFIX;
        }
        String normalized = prefix.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : DEFAULT_OBJECT_PREFIX;
    }

    private boolean isFolderUploadRequest(MinioUploadInitRequest request) {
        return request != null
                && StringUtils.hasText(trimToNull(request.getFolderName()))
                && StringUtils.hasText(trimToNull(request.getFolderUploadId()))
                && StringUtils.hasText(trimToNull(firstNonBlank(request.getRelativePath(), request.getFileName())));
    }

    private String resolveFolderUploadDate(String folderUploadDate) {
        String normalizedDate = trimToNull(folderUploadDate);
        if (normalizedDate == null) {
            return LocalDate.now().format(FOLDER_DATE_FORMATTER);
        }
        try {
            return LocalDate.parse(normalizedDate, FOLDER_DATE_FORMATTER).format(FOLDER_DATE_FORMATTER);
        } catch (Exception ex) {
            throw new ServiceException("Invalid folderUploadDate");
        }
    }

    private String normalizeFolderRelativePath(String path, String folderName) {
        String normalizedPath = path == null ? "" : path.trim().replace("\\", "/");
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        List<String> segments = Arrays.stream(normalizedPath.split("/"))
                .map(this::normalizeObjectNameSegment)
                .filter(segment -> StringUtils.hasText(segment) && !".".equals(segment))
                .collect(Collectors.toList());
        if (!segments.isEmpty() && segments.get(0).equals(folderName)) {
            segments.remove(0);
        }
        return String.join("/", segments);
    }

    private String normalizeObjectNameSegment(String value) {
        String segment = value == null ? "" : value.trim();
        if (segment.isEmpty()) {
            return "";
        }
        segment = segment.replace("\\", "_").replace("/", "_");
        segment = segment.replaceAll("[:*?\"<>|\\p{Cntrl}]+", "_");
        while (segment.contains("..")) {
            segment = segment.replace("..", ".");
        }
        return segment.trim();
    }

    private String normalizeBusinessSegment(String businessType) {
        String normalized = trimToNull(businessType);
        if (normalized == null) {
            return "default";
        }

        String safeValue = normalized.toLowerCase(Locale.ROOT)
                .replace("\\", "-")
                .replace("/", "-")
                .replaceAll("[^a-z0-9_-]", "-")
                .replaceAll("-{2,}", "-");

        safeValue = trimEdgeDash(safeValue);
        return StringUtils.hasText(safeValue) ? safeValue : "default";
    }

    private String normalizeObjectName(String objectName) {
        String normalized = trimToNull(objectName);
        if (normalized == null) {
            throw new ServiceException("objectName is required");
        }
        return normalized.replace("\\", "/");
    }

    private String extractFileName(String fileName) {
        String normalized = normalizeObjectName(fileName);
        int lastSlashIndex = normalized.lastIndexOf('/');
        return lastSlashIndex >= 0 ? normalized.substring(lastSlashIndex + 1) : normalized;
    }

    private String extractExtension(String fileName) {
        String normalizedFileName = extractFileName(fileName);
        int lastDotIndex = normalizedFileName.lastIndexOf('.');
        if (lastDotIndex <= 0 || lastDotIndex == normalizedFileName.length() - 1) {
            return "";
        }
        return normalizedFileName.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }

    private String resolveFileExtension(String originalFileName, String objectName) {
        String originalExtension = extractExtension(originalFileName);
        return StringUtils.hasText(originalExtension) ? originalExtension : extractExtension(objectName);
    }

    private String resolveInitOriginalFileName(MinioUploadInitRequest request) {
        if (isFolderUploadRequest(request)) {
            return extractFileName(firstNonBlank(trimToNull(request.getRelativePath()), request.getFileName()));
        }
        return extractFileName(request.getFileName());
    }

    private String resolveFolderName(String folderName) {
        String resolvedFolderName = trimToNull(folderName);
        if (!StringUtils.hasText(resolvedFolderName)) {
            throw new ServiceException("folderName is required");
        }
        return resolvedFolderName;
    }

    private String firstNonBlank(String preferred, String fallback) {
        String preferredValue = trimToNull(preferred);
        return preferredValue != null ? preferredValue : trimToNull(fallback);
    }

    private String trimEdgeDash(String value) {
        String result = value;
        while (result.startsWith("-")) {
            result = result.substring(1);
        }
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long calcMultipartPartSize(long fileSize) {
        long byPartCount = (fileSize + MAX_PART_COUNT - 1) / MAX_PART_COUNT;
        return Math.max(Math.max(DEFAULT_MULTIPART_SIZE, MIN_MULTIPART_SIZE), byPartCount);
    }

    private int calcMultipartPartCount(long fileSize, long partSize) {
        return (int) ((fileSize + partSize - 1) / partSize);
    }

    private static MinioAsyncClient resolveAsyncClient(MinioClient minioClient) {
        try {
            Field field = MinioClient.class.getDeclaredField("asyncClient");
            field.setAccessible(true);
            return (MinioAsyncClient) field.get(minioClient);
        } catch (Exception ex) {
            throw new ServiceException("Failed to initialize MinIO multipart support: " + ex.getMessage());
        }
    }

    private static final class MultipartAsyncSupport extends MinioAsyncClient {
        private MultipartAsyncSupport(MinioAsyncClient minioAsyncClient) {
            super(minioAsyncClient);
        }

        private CreateMultipartUploadResponse createMultipartUpload(String bucket,
                                                                   String objectName,
                                                                   String contentType) {
            try {
                Multimap<String, String> headers = HashMultimap.create();
                Multimap<String, String> queryParams = HashMultimap.create();
                if (StringUtils.hasText(contentType)) {
                    headers.put("Content-Type", contentType);
                }
                return super.createMultipartUpload(bucket, null, objectName, headers, queryParams);
            } catch (Exception ex) {
                throw new ServiceException("Failed to create MinIO multipart upload: " + ex.getMessage());
            }
        }

        private ObjectWriteResponse completeMultipartUpload(String bucket,
                                                            String objectName,
                                                            String minioUploadId,
                                                            Part[] parts) {
            try {
                return super.completeMultipartUpload(
                        bucket,
                        null,
                        objectName,
                        minioUploadId,
                        parts,
                        HashMultimap.create(),
                        HashMultimap.create()
                );
            } catch (Exception ex) {
                throw new ServiceException("Failed to complete MinIO multipart upload: " + ex.getMessage());
            }
        }
    }
}
