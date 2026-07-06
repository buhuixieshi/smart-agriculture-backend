package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.OperationLog;
import com.agriculture.service.OperationLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @GetMapping
    public Result<List<OperationLog>> list(@RequestParam(required = false) String operationType,
                                           @RequestParam(required = false) String target,
                                           @RequestParam(required = false) String result,
                                           @RequestParam(required = false) String operatorName) {
        return Result.ok(operationLogService.query(operationType, target, result, operatorName));
    }
}
