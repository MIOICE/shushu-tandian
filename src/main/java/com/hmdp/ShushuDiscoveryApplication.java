package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.hmdp.mapper")
@EnableScheduling
@SpringBootApplication
public class ShushuDiscoveryApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShushuDiscoveryApplication.class, args);
    }
}
