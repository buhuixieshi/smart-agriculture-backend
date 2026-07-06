package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.DeviceDTO;
import com.agriculture.entity.Device;
import com.agriculture.service.DeviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        if (plotId != null) {
            return Result.ok(deviceService.listByPlotId(plotId));
        }
        return Result.ok(deviceService.list());
    }

    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        Device device = deviceService.getById(id);
        if (device == null) {
            return Result.fail(400, "设备不存在：" + id);
        }
        return Result.ok(device);
    }

    @PostMapping
    @OperationLogRecord(type = "DEVICE_CREATE", target = "device", detail = "新增设备")
    public Result<Device> create(@Valid @RequestBody DeviceDTO dto) {
        return Result.ok(deviceService.createDevice(dto));
    }

    @PutMapping("/{id}")
    @OperationLogRecord(type = "DEVICE_UPDATE", target = "device", detail = "修改设备")
    public Result<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return Result.ok(deviceService.updateDevice(id, dto));
    }

    @DeleteMapping("/{id}")
    @OperationLogRecord(type = "DEVICE_DELETE", target = "device", detail = "删除设备")
    public Result<String> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.ok("删除成功");
    }

    @PutMapping("/{id}/bind")
    @OperationLogRecord(type = "DEVICE_BIND", target = "device", detail = "绑定地块")
    public Result<Device> bindPlot(@PathVariable Long id, @RequestParam Long plotId) {
        return Result.ok(deviceService.bindPlot(id, plotId));
    }

    @PutMapping("/{id}/unbind")
    @OperationLogRecord(type = "DEVICE_UNBIND", target = "device", detail = "解绑地块")
    public Result<Device> unbindPlot(@PathVariable Long id) {
        return Result.ok(deviceService.unbindPlot(id));
    }
}
