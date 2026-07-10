package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceCode;

    private String deviceName;

    private String deviceType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long plotId;

    private String status;

    private LocalDateTime lastHeartbeat;

    private Integer signalStrength;

    private Integer battery;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
