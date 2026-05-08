package com.ruoyi;


import com.ruoyi.Xidian.config.SftpProperties;
import com.ruoyi.Xidian.service.SftpService;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SFTPTest {

    @Autowired
    private SftpProperties sftpProperties;

    @Autowired
    private SftpService sftpService;

    @Test
    void testSSH() throws IOException {
        Path localFile = Paths.get("C:\\home\\hyy1208\\config_1773491811606_项目一_测试试验一.xlsx");
        String remotePath = "/home/hyy1208/data/test2.xlsx";
        uploadByPassword(sftpProperties.getHost(),sftpProperties.getPort(),sftpProperties.getUsername(),sftpProperties.getPassword(),localFile,remotePath);
    }
    @Test
    void testSSH2() throws IOException {
        Path localFile = Paths.get("C:\\home\\hyy1208\\config_1773491811606_项目一_测试试验一.xlsx");
        String remotePath = "test2.xlsx";
        sftpService.upload(localFile,remotePath);
    }

    @Test
    void testDelete() throws IOException {
        if(!sftpService.exists("test1.xlsx")){
            System.out.println("文件不存在");
            return;
        }
        sftpService.delete("test1.xlsx");
    }

    public static void uploadByPassword(String host, int port, String username, String password, Path localFile, String remotePath) throws IOException {

        File knownHosts = Path.of(System.getProperty("user.home"), ".ssh", "known_hosts").toFile();

        try (SSHClient ssh = new SSHClient()) {
            if (knownHosts.exists()) {
                ssh.addHostKeyVerifier(new OpenSSHKnownHosts(knownHosts));
            } else {
                throw new IllegalStateException("error");
            }

            ssh.connect(host, port);

            try {
                ssh.authPassword(username, password);

                try (SFTPClient sftp = ssh.newSFTPClient()) {
                    sftp.put(new FileSystemFile(localFile.toFile()), remotePath);
                }
            } finally {
                ssh.disconnect();
            }
        }
    }

    @Test
    void testSSHByConfig(){

    }

}
