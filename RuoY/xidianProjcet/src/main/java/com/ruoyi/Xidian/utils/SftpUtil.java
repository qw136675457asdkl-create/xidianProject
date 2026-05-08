package com.ruoyi.Xidian.utils;

import com.ruoyi.Xidian.config.SftpProperties;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SftpUtil {

    private SftpUtil() {
    }

    /**
     * 上传文件
     */
    public static void upload(
            SftpProperties properties,
            String localFilePath,
            String remoteFilePath
    ) throws IOException {
        Path localPath = Path.of(localFilePath);

        if (!Files.exists(localPath)) {
            throw new FileNotFoundException("本地文件不存在：" + localFilePath);
        }

        if (!Files.isRegularFile(localPath)) {
            throw new IOException("本地路径不是文件：" + localFilePath);
        }

        try (SSHClient ssh = connect(properties);
             SFTPClient sftp = ssh.newSFTPClient()) {

            createRemoteParentDirs(sftp, remoteFilePath);
            sftp.put(localPath.toString(), remoteFilePath);
        }
    }

    /**
     * 下载文件
     */
    public static void download(
            SftpProperties properties,
            String remoteFilePath,
            String localFilePath
    ) throws IOException {
        Path localPath = Path.of(localFilePath);
        Path parent = localPath.toAbsolutePath().getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (SSHClient ssh = connect(properties);
             SFTPClient sftp = ssh.newSFTPClient()) {

            if (!exists(sftp, remoteFilePath)) {
                throw new FileNotFoundException("远程文件不存在：" + remoteFilePath);
            }

            sftp.get(remoteFilePath, localPath.toString());
        }
    }

    /**
     * 删除远程文件
     */
    public static void deleteFile(
            SftpProperties properties,
            String remoteFilePath
    ) throws IOException {
        deleteFile(properties, remoteFilePath, false);
    }

    /**
     * 删除远程文件
     */
    public static void deleteFile(
            SftpProperties properties,
            String remoteFilePath,
            boolean ignoreMissing
    ) throws IOException {
        try (SSHClient ssh = connect(properties);
             SFTPClient sftp = ssh.newSFTPClient()) {

            if (!exists(sftp, remoteFilePath)) {
                if (ignoreMissing) {
                    return;
                }
                throw new FileNotFoundException("远程文件不存在：" + remoteFilePath);
            }

            sftp.rm(remoteFilePath);
        }
    }

    /**
     * 判断远程文件或目录是否存在
     */
    public static boolean exists(
            SftpProperties properties,
            String remotePath
    ) throws IOException {
        try (SSHClient ssh = connect(properties);
             SFTPClient sftp = ssh.newSFTPClient()) {

            return exists(sftp, remotePath);
        }
    }

    private static boolean exists(SFTPClient sftp, String remotePath) throws IOException {
        FileAttributes attrs = sftp.statExistence(remotePath);
        return attrs != null;
    }

    private static SSHClient connect(SftpProperties properties) throws IOException {
        SSHClient ssh = new SSHClient();

        ssh.setConnectTimeout(properties.getConnectTimeoutMillis());
        ssh.setTimeout(properties.getSocketTimeoutMillis());

        if (properties.isAllowUnknownHostKey()) {
            ssh.addHostKeyVerifier(new PromiscuousVerifier());
        } else {
            ssh.loadKnownHosts();
        }

        try {
            ssh.connect(properties.getHost(), properties.getPort());

            if (hasText(properties.getPrivateKeyPath())) {
                KeyProvider keyProvider;

                if (hasText(properties.getPrivateKeyPassphrase())) {
                    keyProvider = ssh.loadKeys(
                            properties.getPrivateKeyPath(),
                            properties.getPrivateKeyPassphrase()
                    );
                } else {
                    keyProvider = ssh.loadKeys(properties.getPrivateKeyPath());
                }

                ssh.authPublickey(properties.getUsername(), keyProvider);
            } else {
                ssh.authPassword(properties.getUsername(), properties.getPassword());
            }

            return ssh;
        } catch (IOException | RuntimeException e) {
            try {
                ssh.disconnect();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    private static void createRemoteParentDirs(
            SFTPClient sftp,
            String remoteFilePath
    ) throws IOException {
        String parentDir = getRemoteParentDir(remoteFilePath);

        if (hasText(parentDir) && !"/".equals(parentDir)) {
            sftp.mkdirs(parentDir);
        }
    }

    private static String getRemoteParentDir(String remoteFilePath) {
        if (!hasText(remoteFilePath)) {
            throw new IllegalArgumentException("远程文件路径不能为空");
        }

        String path = remoteFilePath.replace("\\", "/");
        int index = path.lastIndexOf('/');

        if (index < 0) {
            return null;
        }

        if (index == 0) {
            return "/";
        }

        return path.substring(0, index);
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}