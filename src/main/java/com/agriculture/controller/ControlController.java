package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.entity.ControlCommand;
import com.agriculture.service.ControlService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/control")
public class ControlController {

    private final ControlService controlService;

    public ControlController(ControlService controlService) {
        this.controlService = controlService;
    }

    @PostMapping("/send")
    public Result<ControlCommand> send(@RequestParam String deviceCode,
                                       @RequestParam String commandType,
                                       @RequestParam(required = false) String commandValue) {
        return Result.ok(controlService.sendCommand(deviceCode, commandType, commandValue));
    }

    @GetMapping("/list")
    public Result<List<ControlCommand>> list(@RequestParam String deviceCode) {
        return Result.ok(controlService.listByDeviceCode(deviceCode));
    }
}
