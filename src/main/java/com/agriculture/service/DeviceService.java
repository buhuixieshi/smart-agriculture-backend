package com.agriculture.service;

import com.agriculture.dto.DeviceDTO;
import com.agriculture.entity.Device;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface DeviceService extends IService<Device> {

    Device getByDeviceCode(String deviceCode);

    boolean updateHeartbeatByDeviceCode(String deviceCode);

    List<Device> listByPlotId(Long plotId);

    Device createDevice(DeviceDTO dto);

    Device updateDevice(Long id, DeviceDTO dto);

    void deleteDevice(Long id);

    Device bindPlot(Long id, Long plotId);

    Device unbindPlot(Long id);
}
