package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("alarm")
public class Alarm {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private Long deviceId;

    private String alarmType;

    private String severity;

    private BigDecimal triggerValue;

    private BigDecimal thresholdValue;

    private String status;

    private String message;

    private LocalDateTime createTime;

    private LocalDateTime resolveTime;
}
