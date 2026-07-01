package com.agriculture;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.agriculture.mapper")
@SpringBootApplication
public class SmartAgricultureApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAgricultureApplication.class, args);
    }
}
