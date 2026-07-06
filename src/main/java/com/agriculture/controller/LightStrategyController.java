package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.dto.LightStrategyDTO;
import com.agriculture.entity.LightStrategy;
import com.agriculture.service.LightStrategyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/light-strategies")
public class LightStrategyController {

    private final LightStrategyService lightStrategyService;

    public LightStrategyController(LightStrategyService lightStrategyService) {
        this.lightStrategyService = lightStrategyService;
    }

    @GetMapping
    public Result<List<LightStrategy>> list() {
        return Result.ok(lightStrategyService.listAllOrderByPlot());
    }

    @GetMapping("/{plotId}")
    public Result<LightStrategy> get(@PathVariable Long plotId) {
        return Result.ok(lightStrategyService.getOrCreateDefault(plotId));
    }

    @PutMapping("/{plotId}")
    public Result<LightStrategy> saveOrUpdate(@PathVariable Long plotId,
                                              @Valid @RequestBody LightStrategyDTO dto) {
        return Result.ok(lightStrategyService.saveOrUpdateByPlotId(plotId, dto));
    }
}
