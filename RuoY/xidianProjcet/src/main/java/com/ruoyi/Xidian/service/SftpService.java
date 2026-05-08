package com.ruoyi.Xidian.service;

import com.ruoyi.Xidian.config.SftpProperties;
import com.ruoyi.Xidian.utils.SftpUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class SftpService {

    private final SftpProperties properties;

    public SftpService(SftpProperties properties) {
        this.properties = properties;
    }

    public void upload(String localFilePath, String remoteFileName) throws IOException {
        String remoteFilePath = buildRemotePath(remoteFileName);
        SftpUtil.upload(properties, localFilePath, remoteFilePath);
    }

    public void upload(Path localFilePath, String remoteFileName) throws IOException {
        upload(localFilePath.toString(), remoteFileName);
    }

    public void uploadToPath(String localFilePath, String remoteFilePath) throws IOException {
        SftpUtil.upload(properties, localFilePath, remoteFilePath);
    }

    public void download(String remoteFileName, String localFilePath) throws IOException {
        String remoteFilePath = buildRemotePath(remoteFileName);
        SftpUtil.download(properties, remoteFilePath, localFilePath);
    }

    public void downloadFromPath(String remoteFilePath, String localFilePath) throws IOException {
        SftpUtil.download(properties, remoteFilePath, localFilePath);
    }

    public void delete(String remoteFileName) throws IOException {
        String remoteFilePath = buildRemotePath(remoteFileName);
        SftpUtil.deleteFile(properties, remoteFilePath);
    }

    public void deleteByPath(String remoteFilePath) throws IOException {
        SftpUtil.deleteFile(properties, remoteFilePath);
    }

    public boolean exists(String remoteFileName) throws IOException {
        String remoteFilePath = buildRemotePath(remoteFileName);
        return SftpUtil.exists(properties, remoteFilePath);
    }

    public boolean existsByPath(String remoteFilePath) throws IOException {
        return SftpUtil.exists(properties, remoteFilePath);
    }

    private String buildRemotePath(String remoteFileName) {
        String baseDir = properties.getRemoteBaseDir();

        if (baseDir == null || baseDir.isBlank()) {
            baseDir = "/";
        }

        if (!baseDir.startsWith("/")) {
            baseDir = "/" + baseDir;
        }

        if (baseDir.endsWith("/")) {
            return baseDir + remoteFileName;
        }

        return baseDir + "/" + remoteFileName;
    }
}