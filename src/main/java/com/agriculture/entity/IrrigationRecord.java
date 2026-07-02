package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("irrigation_record")
public class IrrigationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private Long deviceId;

    private String deviceCode;

    private Long commandId;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer durationSeconds;

    private BigDecimal waterAmount;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
