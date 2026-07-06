package com.agriculture.service;

import com.agriculture.entity.OperationLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface OperationLogService extends IService<OperationLog> {

    List<OperationLog> query(String operationType, String target, String result, String operatorName);
}
