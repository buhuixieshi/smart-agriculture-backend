package com.agriculture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_face")
public class UserFace {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String faceFeature;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
