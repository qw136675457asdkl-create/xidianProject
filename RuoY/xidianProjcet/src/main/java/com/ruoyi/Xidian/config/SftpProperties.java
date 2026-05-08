package com.ruoyi.Xidian.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sftp")
public class SftpProperties {

    private String host;

    private int port = 22;

    private String username;

    private String password;

    private boolean allowUnknownHostKey = true;

    private int connectTimeoutMillis = 10_000;

    private int socketTimeoutMillis = 30_000;

    /**
     * 私钥路径，例如 /home/app/.ssh/id_rsa。
     * 配置后优先使用私钥登录。
     */
    private String privateKeyPath;

    /**
     * 私钥密码，没有则不配置。
     */
    private String privateKeyPassphrase;

    private String remoteBaseDir;


}