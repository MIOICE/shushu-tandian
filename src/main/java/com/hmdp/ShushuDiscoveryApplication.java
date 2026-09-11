package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@MapperScan("com.hmdp.mapper")
@EnableScheduling
@SpringBootApplication
public class ShushuDiscoveryApplication {

    public static void main(String[] args) {
        if (System.getProperty("rocketmq.client.logUseSlf4j") == null) {
            System.setProperty("rocketmq.client.logUseSlf4j", "true");
        }
        if (System.getProperty("rocketmq.client.localOffsetStoreDir") == null) {
            String offsetDirectory = System.getenv("ROCKETMQ_LOCAL_OFFSET_DIR");
            if (offsetDirectory == null || offsetDirectory.trim().isEmpty()) {
                offsetDirectory = new File(
                        System.getProperty("java.io.tmpdir"), "shushu-rocketmq-offsets").getAbsolutePath();
            }
            System.setProperty("rocketmq.client.localOffsetStoreDir", offsetDirectory);
        }
        SpringApplication.run(ShushuDiscoveryApplication.class, args);
    }
}
