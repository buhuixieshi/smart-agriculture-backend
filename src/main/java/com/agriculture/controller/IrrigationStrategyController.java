package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.IrrigationStrategyDTO;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.service.IrrigationStrategyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/strategies")
public class IrrigationStrategyController {

    private final IrrigationStrategyService irrigationStrategyService;

    public IrrigationStrategyController(IrrigationStrategyService irrigationStrategyService) {
        this.irrigationStrategyService = irrigationStrategyService;
    }

    @GetMapping
    public Result<List<IrrigationStrategy>> list() {
        return Result.ok(irrigationStrategyService.list());
    }

    @GetMapping("/{plotId}")
    public Result<IrrigationStrategy> detail(@PathVariable Long plotId) {
        return Result.ok(irrigationStrategyService.getOrCreateDefault(plotId));
    }

    @PutMapping("/{plotId}")
    @OperationLogRecord(type = "STRATEGY_UPDATE", target = "irrigation_strategy", detail = "修改阈值策略")
    public Result<IrrigationStrategy> update(@PathVariable Long plotId,
                                             @Valid @RequestBody IrrigationStrategyDTO dto) {
        return Result.ok(irrigationStrategyService.saveOrUpdateByPlotId(plotId, dto));
    }

    @DeleteMapping("/{plotId}")
    @OperationLogRecord(type = "STRATEGY_DELETE", target = "irrigation_strategy", detail = "删除阈值策略")
    public Result<String> delete(@PathVariable Long plotId) {
        irrigationStrategyService.deleteByPlotId(plotId);
        return Result.ok("删除成功");
    }
}
