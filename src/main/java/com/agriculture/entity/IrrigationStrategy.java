package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("irrigation_strategy")
public class IrrigationStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private BigDecimal moistureMin;

    private BigDecimal moistureMax;

    private Integer consecutiveThreshold;

    private Boolean autoMode;

    private Integer maxDuration;

    private Integer cooldownMinutes;

    @TableField(exist = false)
    private LocalDateTime createTime;

    @TableField(exist = false)
    private LocalDateTime updateTime;
}
