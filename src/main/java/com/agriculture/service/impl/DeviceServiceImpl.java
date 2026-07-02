package com.agriculture.service.impl;

import com.agriculture.entity.Device;
import com.agriculture.mapper.DeviceMapper;
import com.agriculture.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    @Override
    public Device getByDeviceCode(String deviceCode) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode);
        return this.getOne(wrapper);
    }

    @Override
    public boolean updateHeartbeatByDeviceCode(String deviceCode) {
        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode)
                .set(Device::getStatus, "ONLINE")
                .set(Device::getLastHeartbeat, LocalDateTime.now());

        return this.update(wrapper);
    }
}
