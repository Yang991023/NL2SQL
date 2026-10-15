package com.nl2sql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 可视化 NL2SQL 自助查询工具 - 启动类
 */
@SpringBootApplication
@EnableAsync
public class Nl2sqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(Nl2sqlApplication.class, args);
    }
}
