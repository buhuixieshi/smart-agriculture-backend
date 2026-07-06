package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("water_usage_limit")
public class WaterUsageLimit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private BigDecimal dailyLimit;

    private BigDecimal monthlyLimit;

    private BigDecimal singleLimit;

    private BigDecimal alertPercent;

    private Integer minEffectiveDuration;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
