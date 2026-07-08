package com.agriculture.service;

import com.agriculture.dto.DeviceDTO;
import com.agriculture.entity.Device;
import com.agriculture.vo.DeviceVO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface DeviceService extends IService<Device> {

    Device getByDeviceCode(String deviceCode);

    boolean updateHeartbeatByDeviceCode(String deviceCode);

    List<Device> listByPlotId(Long plotId);

    List<DeviceVO> listDevices(String keyword, Long plotId, String status);

    Page<DeviceVO> pageDevices(String keyword, Long plotId, String status, Integer page, Integer size);

    DeviceVO getDeviceDetail(Long id);

    Device createDevice(DeviceDTO dto);

    Device updateDevice(Long id, DeviceDTO dto);

    void deleteDevice(Long id);

    Device bindPlot(Long id, Long plotId);

    Device unbindPlot(Long id);

    Device updateStatus(Long id, String status);

    Device disableDevice(Long id);

    Device enableDevice(Long id);
}
