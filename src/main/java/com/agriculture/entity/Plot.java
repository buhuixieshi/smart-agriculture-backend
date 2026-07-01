package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("plot")
public class Plot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String cropType;

    private String location;

    private BigDecimal area;

    private String status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}