package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.Plot;
import com.agriculture.service.PlotService;
import com.agriculture.service.TelemetryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plots")
public class PlotController {

    private final PlotService plotService;
    private final TelemetryService telemetryService;

    public PlotController(PlotService plotService, TelemetryService telemetryService) {
        this.plotService = plotService;
        this.telemetryService = telemetryService;
    }

    @GetMapping
    public Result<List<Plot>> list() {
        return Result.ok(plotService.list());
    }

    @GetMapping("/{id}")
    public Result<Plot> detail(@PathVariable Long id) {
        return Result.ok(plotService.getById(id));
    }

    @GetMapping("/{id}/latest")
    public Result<?> latest(@PathVariable Long id) {
        return Result.ok(telemetryService.getLatestByPlotId(id));
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody Plot plot) {
        return Result.ok(plotService.save(plot));
    }

    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody Plot plot) {
        plot.setId(id);
        return Result.ok(plotService.updateById(plot));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(plotService.removeById(id));
    }
}
