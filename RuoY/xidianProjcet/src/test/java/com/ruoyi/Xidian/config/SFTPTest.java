package com.ruoyi.Xidian.config;


import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts;
import net.schmizz.sshj.xfer.FileSystemFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootTest
public class SFTPTest {
    private final String host = "10.1.22.71";
    private final int port = 22;
    private final String userName = "hyy1208";
    private final String password = "xdhyy123";
    @Test
    void testSSH() throws IOException {
        Path localFile = Paths.get("C:\\home\\hyy1208\\config_1773491811606_项目一_测试试验一.xlsx");
        String remotePath = "/home/hyy1208/data/test.xlsx";
        uploadByPassword(host,port,userName,password,localFile,remotePath);
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
