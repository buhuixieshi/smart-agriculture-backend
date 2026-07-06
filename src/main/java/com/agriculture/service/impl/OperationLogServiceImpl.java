package com.agriculture.service.impl;

import com.agriculture.entity.OperationLog;
import com.agriculture.mapper.OperationLogMapper;
import com.agriculture.service.OperationLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    @Override
    public List<OperationLog> query(String operationType, String target, String result, String operatorName) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();

        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(OperationLog::getOperationType, operationType);
        }
        if (target != null && !target.isBlank()) {
            wrapper.like(OperationLog::getTarget, target);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(OperationLog::getResult, result);
        }
        if (operatorName != null && !operatorName.isBlank()) {
            wrapper.like(OperationLog::getOperatorName, operatorName);
        }

        wrapper.orderByDesc(OperationLog::getCreateTime);
        return this.list(wrapper);
    }
}
