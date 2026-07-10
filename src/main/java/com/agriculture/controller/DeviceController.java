package com.agriculture.controller;

import com.agriculture.aspect.OperationLogRecord;
import com.agriculture.common.Result;
import com.agriculture.dto.DeviceBindDTO;
import com.agriculture.dto.DeviceDTO;
import com.agriculture.dto.DeviceStatusDTO;
import com.agriculture.entity.Device;
import com.agriculture.service.DeviceService;
import com.agriculture.vo.DeviceVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @GetMapping
    public Result<?> list(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Long plotId,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) Integer page,
                          @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            return Result.ok(deviceService.pageDevices(keyword, plotId, status, page, size));
        }
        return Result.ok(deviceService.listDevices(keyword, plotId, status));
    }

    @GetMapping("/{id}")
    public Result<DeviceVO> detail(@PathVariable Long id) {
        return Result.ok(deviceService.getDeviceDetail(id));
    }

    @PostMapping
    @OperationLogRecord(type = "DEVICE_CREATE", target = "device", detail = "\u65b0\u589e\u8bbe\u5907")
    public Result<Device> create(@Valid @RequestBody DeviceDTO dto) {
        return Result.ok(deviceService.createDevice(dto));
    }

    @PutMapping("/{id}")
    @OperationLogRecord(type = "DEVICE_UPDATE", target = "device", detail = "\u4fee\u6539\u8bbe\u5907")
    public Result<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceDTO dto) {
        return Result.ok(deviceService.updateDevice(id, dto));
    }

    @DeleteMapping("/{id}")
    @OperationLogRecord(type = "DEVICE_DELETE", target = "device", detail = "\u5220\u9664\u8bbe\u5907")
    public Result<String> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.ok("\u5220\u9664\u6210\u529f");
    }

    @PutMapping("/{id}/bind")
    @OperationLogRecord(type = "DEVICE_BIND", target = "device", detail = "\u7ed1\u5b9a\u5730\u5757")
    public Result<Device> bindPlot(@PathVariable Long id, @RequestParam Long plotId) {
        return Result.ok(deviceService.bindPlot(id, plotId));
    }

    @PutMapping("/{id}/unbind")
    @OperationLogRecord(type = "DEVICE_UNBIND", target = "device", detail = "\u89e3\u7ed1\u5730\u5757")
    public Result<Device> unbindPlot(@PathVariable Long id) {
        return Result.ok(deviceService.unbindPlot(id));
    }

    @PutMapping("/binding")
    @OperationLogRecord(type = "DEVICE_BINDING_UPDATE", target = "device", detail = "\u66f4\u65b0\u8bbe\u5907\u5730\u5757\u7ed1\u5b9a")
    public Result<Device> updatePlotBinding(@RequestBody DeviceBindDTO dto) {
        return Result.ok(deviceService.updatePlotBinding(dto));
    }

    @PutMapping("/{id}/disable")
    @OperationLogRecord(type = "DEVICE_DISABLE", target = "device", detail = "\u505c\u7528\u8bbe\u5907")
    public Result<Device> disable(@PathVariable Long id) {
        return Result.ok(deviceService.disableDevice(id));
    }

    @PutMapping("/{id}/enable")
    @OperationLogRecord(type = "DEVICE_ENABLE", target = "device", detail = "\u542f\u7528\u8bbe\u5907")
    public Result<Device> enable(@PathVariable Long id) {
        return Result.ok(deviceService.enableDevice(id));
    }

    @PatchMapping("/{id}/status")
    @OperationLogRecord(type = "DEVICE_STATUS_UPDATE", target = "device", detail = "\u4fee\u6539\u8bbe\u5907\u72b6\u6001")
    public Result<Device> updateStatus(@PathVariable Long id, @Valid @RequestBody DeviceStatusDTO dto) {
        return Result.ok(deviceService.updateStatus(id, dto.getStatus()));
    }
}
