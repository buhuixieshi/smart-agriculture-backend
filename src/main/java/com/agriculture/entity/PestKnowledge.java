package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pest_knowledge")
public class PestKnowledge {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String pestId;

    private String pestName;

    private String dangerLevel;

    private String description;

    private String physicalControl;

    private String biologicalControl;

    private String chemicalControl;

    private String prevention;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
