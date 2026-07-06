package com.agriculture.service.impl;

import com.agriculture.dto.DeviceDTO;
import com.agriculture.entity.Device;
import com.agriculture.entity.Plot;
import com.agriculture.mapper.DeviceMapper;
import com.agriculture.service.AlarmService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.PlotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private final PlotService plotService;
    private final AlarmService alarmService;

    public DeviceServiceImpl(PlotService plotService, AlarmService alarmService) {
        this.plotService = plotService;
        this.alarmService = alarmService;
    }

    @Override
    public Device getByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);
        return this.getOne(wrapper);
    }

    @Override
    public boolean updateHeartbeatByDeviceCode(String deviceCode) {
        Device device = getByDeviceCode(deviceCode);
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode)
                .set(Device::getStatus, "ONLINE")
                .set(Device::getLastHeartbeat, LocalDateTime.now())
                .set(Device::getUpdatedAt, LocalDateTime.now());

        boolean updated = this.update(wrapper);
        if (updated && device != null) {
            alarmService.recoverActiveDeviceOffline(device.getId());
        }
        return updated;
    }

    @Override
    public List<Device> listByPlotId(Long plotId) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getPlotId, plotId)
                .orderByDesc(Device::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public Device createDevice(DeviceDTO dto) {
        Device exists = getByDeviceCode(dto.getDeviceCode());
        if (exists != null) {
            throw new IllegalArgumentException("设备编号已存在：" + dto.getDeviceCode());
        }

        if (dto.getPlotId() != null) {
            checkPlotExists(dto.getPlotId());
        }

        Device device = new Device();
        device.setPlotId(dto.getPlotId());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        device.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "OFFLINE" : dto.getStatus());
        device.setSignalStrength(dto.getSignalStrength());
        device.setBattery(dto.getBattery());
        device.setCreatedAt(LocalDateTime.now());
        device.setUpdatedAt(LocalDateTime.now());

        this.save(device);
        return device;
    }

    @Override
    @Transactional
    public Device updateDevice(Long id, DeviceDTO dto) {
        Device device = this.getById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + id);
        }

        Device exists = getByDeviceCode(dto.getDeviceCode());
        if (exists != null && !exists.getId().equals(id)) {
            throw new IllegalArgumentException("设备编号已存在：" + dto.getDeviceCode());
        }

        if (dto.getPlotId() != null) {
            checkPlotExists(dto.getPlotId());
        }

        device.setPlotId(dto.getPlotId());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        device.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? device.getStatus() : dto.getStatus());
        device.setSignalStrength(dto.getSignalStrength());
        device.setBattery(dto.getBattery());
        device.setUpdatedAt(LocalDateTime.now());

        this.updateById(device);
        return device;
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        Device device = this.getById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + id);
        }

        this.removeById(id);
    }

    @Override
    @Transactional
    public Device bindPlot(Long id, Long plotId) {
        Device device = this.getById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + id);
        }

        checkPlotExists(plotId);

        device.setPlotId(plotId);
        device.setUpdatedAt(LocalDateTime.now());

        this.updateById(device);
        return device;
    }

    @Override
    @Transactional
    public Device unbindPlot(Long id) {
        Device device = this.getById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在：" + id);
        }

        device.setPlotId(null);
        device.setUpdatedAt(LocalDateTime.now());

        this.updateById(device);
        return device;
    }

    private void checkPlotExists(Long plotId) {
        Plot plot = plotService.getById(plotId);
        if (plot == null) {
            throw new IllegalArgumentException("地块不存在：" + plotId);
        }
    }
}
