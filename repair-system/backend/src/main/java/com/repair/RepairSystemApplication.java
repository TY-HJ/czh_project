package com.repair;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 社区报修工单管理系统 启动类
 */
@SpringBootApplication
@EnableScheduling   // 开启定时任务(工单超时催办)
public class RepairSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(RepairSystemApplication.class, args);
    }
}
