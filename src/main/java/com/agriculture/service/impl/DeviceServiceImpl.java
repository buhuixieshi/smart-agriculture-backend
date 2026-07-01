package com.agriculture.service.impl;

import com.agriculture.entity.Device;
import com.agriculture.mapper.DeviceMapper;
import com.agriculture.service.DeviceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {
}
