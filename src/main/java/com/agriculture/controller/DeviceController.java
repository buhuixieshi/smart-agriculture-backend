package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.Device;
import com.agriculture.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<List<Device>> list(@RequestParam(required = false) Long plotId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        if (plotId != null) {
            wrapper.eq(Device::getPlotId, plotId);
        }
        return Result.ok(deviceService.list(wrapper));
    }

    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        return Result.ok(deviceService.getById(id));
    }
}
