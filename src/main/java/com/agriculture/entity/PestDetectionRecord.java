package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pest_detection_record")
public class PestDetectionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long plotId;

    private String fileName;

    private String pestId;

    private String pestName;

    private String dangerLevel;

    private BigDecimal confidence;

    private String modelStatus;

    private LocalDateTime detectedAt;

    private LocalDateTime createdAt;
}
