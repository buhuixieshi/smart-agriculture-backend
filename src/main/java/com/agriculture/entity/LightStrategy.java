package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("light_strategy")
public class LightStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private BigDecimal illuminanceMin;

    private BigDecimal illuminanceMax;

    private Boolean autoMode;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer cooldownMinutes;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
