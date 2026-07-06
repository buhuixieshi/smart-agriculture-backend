package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.IrrigationControlDTO;
import com.agriculture.entity.ControlCommand;
import com.agriculture.service.ControlService;
import com.agriculture.vo.CommandVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    @OperationLogRecord(type = "CONTROL_SEND", target = "control_command", detail = "发送控制命令")
    public Result<ControlCommand> send(@RequestParam(required = false) String deviceCode,
                                       @RequestParam(required = false) String commandType,
                                       @RequestParam(required = false) String commandValue) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return Result.fail(400, "deviceCode为必填参数");
        }

        if (commandType == null || commandType.isBlank()) {
            return Result.fail(400, "commandType为必填参数");
        }

        return Result.ok(controlService.sendCommand(deviceCode, commandType, commandValue));
    }

    @PostMapping("/irrigation")
    @OperationLogRecord(type = "IRRIGATION_CONTROL", target = "control_command", detail = "灌溉控制")
    public Result<CommandVO> irrigation(@Valid @RequestBody IrrigationControlDTO dto) {
        return Result.ok(controlService.irrigationControl(dto));
    }

    @GetMapping("/commands/{commandNo}")
    public Result<CommandVO> commandStatus(@PathVariable String commandNo) {
        return Result.ok(controlService.getCommandStatus(commandNo));
    }

    @GetMapping("/list")
    public Result<List<ControlCommand>> list(@RequestParam(required = false) String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return Result.fail(400, "deviceCode为必填参数");
        }

        return Result.ok(controlService.listByDeviceCode(deviceCode));
    }
}
