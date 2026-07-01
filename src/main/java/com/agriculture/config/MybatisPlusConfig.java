package com.agriculture.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.agriculture.mapper")
public class MybatisPlusConfig {
}