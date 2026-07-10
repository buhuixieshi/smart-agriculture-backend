package com.agriculture.service.impl;

import com.agriculture.dto.DeviceBindDTO;
import com.agriculture.dto.DeviceDTO;
import com.agriculture.entity.Device;
import com.agriculture.entity.Plot;
import com.agriculture.mapper.DeviceMapper;
import com.agriculture.service.AlarmService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.PlotService;
import com.agriculture.vo.DeviceCapabilitiesVO;
import com.agriculture.vo.DeviceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private static final String REAL_BEARPI_DEVICE_CODE = "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001";

    private final PlotService plotService;
    private final AlarmService alarmService;

    public DeviceServiceImpl(PlotService plotService, AlarmService alarmService) {
        this.plotService = plotService;
        this.alarmService = alarmService;
    }

    @Override
    public Device getByDeviceCode(String deviceCode) {
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode.trim());
        return this.getOne(wrapper, false);
    }

    @Override
    public boolean updateHeartbeatByDeviceCode(String deviceCode) {
        Device device = getByDeviceCode(deviceCode);
        if (device == null) {
            return false;
        }

        LambdaUpdateWrapper<Device> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Device::getDeviceCode, deviceCode)
                .set(Device::getLastHeartbeat, LocalDateTime.now())
                .set(Device::getUpdatedAt, LocalDateTime.now());

        if (!"DISABLED".equalsIgnoreCase(device.getStatus())) {
            wrapper.set(Device::getStatus, "ONLINE");
        }

        boolean updated = this.update(wrapper);
        if (updated && !"DISABLED".equalsIgnoreCase(device.getStatus())) {
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
    public List<DeviceVO> listDevices(String keyword, Long plotId, String status) {
        return this.list(buildQueryWrapper(keyword, plotId, status))
                .stream()
                .map(this::toDeviceVO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<DeviceVO> pageDevices(String keyword, Long plotId, String status, Integer page, Integer size) {
        long current = page == null || page < 1 ? 1L : page.longValue();
        long pageSize = size == null || size < 1 ? 10L : size.longValue();

        Page<Device> sourcePage = this.page(new Page<>(current, pageSize), buildQueryWrapper(keyword, plotId, status));
        Page<DeviceVO> result = new Page<>(sourcePage.getCurrent(), sourcePage.getSize(), sourcePage.getTotal());
        result.setRecords(sourcePage.getRecords()
                .stream()
                .map(this::toDeviceVO)
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public DeviceVO getDeviceDetail(Long id) {
        Device device = requireDevice(id);
        return toDeviceVO(device);
    }

    @Override
    @Transactional
    public Device createDevice(DeviceDTO dto) {
        Device exists = getByDeviceCode(dto.getDeviceCode());
        if (exists != null) {
            throw new IllegalArgumentException("\u8bbe\u5907\u7f16\u53f7\u5df2\u5b58\u5728\uff1a" + dto.getDeviceCode());
        }

        if (dto.getPlotId() != null) {
            checkPlotExists(dto.getPlotId());
        }

        Device device = new Device();
        device.setPlotId(dto.getPlotId());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        device.setStatus(dto.getStatus() == null || dto.getStatus().isBlank()
                ? "OFFLINE"
                : normalizeStatus(dto.getStatus()));
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
        Device device = requireDevice(id);

        Device exists = getByDeviceCode(dto.getDeviceCode());
        if (exists != null && !exists.getId().equals(id)) {
            throw new IllegalArgumentException("\u8bbe\u5907\u7f16\u53f7\u5df2\u5b58\u5728\uff1a" + dto.getDeviceCode());
        }

        if (dto.getPlotId() != null) {
            checkPlotExists(dto.getPlotId());
        }

        device.setPlotId(dto.getPlotId());
        device.setDeviceCode(dto.getDeviceCode());
        device.setDeviceName(dto.getDeviceName());
        device.setDeviceType(dto.getDeviceType());
        if (dto.getStatus() != null && !dto.getStatus().isBlank()) {
            device.setStatus(normalizeStatus(dto.getStatus()));
        }
        device.setSignalStrength(dto.getSignalStrength());
        device.setBattery(dto.getBattery());
        device.setUpdatedAt(LocalDateTime.now());

        this.updateById(device);
        return device;
    }

    @Override
    @Transactional
    public void deleteDevice(Long id) {
        requireDevice(id);
        this.removeById(id);
    }

    @Override
    @Transactional
    public Device bindPlot(Long id, Long plotId) {
        Device device = requireDevice(id);
        checkPlotExists(plotId);

        return updatePlotIdPersistently(device, plotId);
    }

    @Override
    @Transactional
    public Device unbindPlot(Long id) {
        Device device = requireDevice(id);

        return updatePlotIdPersistently(device, null);
    }

    @Override
    @Transactional
    public Device updatePlotBinding(DeviceBindDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("\u7ed1\u5b9a\u53c2\u6570\u4e0d\u80fd\u4e3a\u7a7a");
        }

        Device device = resolveDevice(dto.getDeviceId(), dto.getDeviceCode());
        if (dto.getPlotId() != null) {
            checkPlotExists(dto.getPlotId());
        }

        return updatePlotIdPersistently(device, dto.getPlotId());
    }

    @Override
    @Transactional
    public Device updateStatus(Long id, String status) {
        Device device = requireDevice(id);

        device.setStatus(normalizeStatus(status));
        device.setUpdatedAt(LocalDateTime.now());
        this.updateById(device);
        return device;
    }

    @Override
    @Transactional
    public Device disableDevice(Long id) {
        return updateStatus(id, "DISABLED");
    }

    @Override
    @Transactional
    public Device enableDevice(Long id) {
        return updateStatus(id, "OFFLINE");
    }

    private Device requireDevice(Long id) {
        Device device = this.getById(id);
        if (device == null) {
            throw new IllegalArgumentException("\u8bbe\u5907\u4e0d\u5b58\u5728\uff1a" + id);
        }
        return device;
    }

    private Device resolveDevice(Long deviceId, String deviceCode) {
        if (deviceId != null) {
            return requireDevice(deviceId);
        }

        if (deviceCode != null && !deviceCode.isBlank()) {
            Device device = getByDeviceCode(deviceCode.trim());
            if (device == null) {
                throw new IllegalArgumentException("\u8bbe\u5907\u4e0d\u5b58\u5728\uff1a" + deviceCode);
            }
            return device;
        }

        throw new IllegalArgumentException("deviceId\u6216deviceCode\u81f3\u5c11\u4f20\u4e00\u4e2a");
    }

    private Device updatePlotIdPersistently(Device device, Long plotId) {
        LocalDateTime now = LocalDateTime.now();
        this.update(
                new LambdaUpdateWrapper<Device>()
                        .eq(Device::getId, device.getId())
                        .set(Device::getPlotId, plotId)
                        .set(Device::getUpdatedAt, now)
        );

        device.setPlotId(plotId);
        device.setUpdatedAt(now);
        return this.getById(device.getId());
    }

    private void checkPlotExists(Long plotId) {
        Plot plot = plotService.getById(plotId);
        if (plot == null) {
            throw new IllegalArgumentException("\u5730\u5757\u4e0d\u5b58\u5728\uff1a" + plotId);
        }
    }

    private LambdaQueryWrapper<Device> buildQueryWrapper(String keyword, Long plotId, String status) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();

        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            wrapper.and(query -> query.like(Device::getDeviceCode, value)
                    .or()
                    .like(Device::getDeviceName, value));
        }

        if (plotId != null) {
            wrapper.eq(Device::getPlotId, plotId);
        }

        if (status != null && !status.isBlank()) {
            wrapper.eq(Device::getStatus, normalizeStatus(status));
        }

        wrapper.orderByDesc(Device::getCreatedAt);
        return wrapper;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("\u8bbe\u5907\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a");
        }

        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!"ONLINE".equals(value) && !"OFFLINE".equals(value) && !"DISABLED".equals(value)) {
            throw new IllegalArgumentException("\u8bbe\u5907\u72b6\u6001\u53ea\u652f\u6301 ONLINE/OFFLINE/DISABLED");
        }
        return value;
    }

    private DeviceVO toDeviceVO(Device device) {
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setDeviceCode(device.getDeviceCode());
        vo.setDeviceSn(device.getDeviceCode());
        vo.setDeviceName(device.getDeviceName());
        vo.setDeviceType(device.getDeviceType());
        vo.setPlotId(device.getPlotId());
        vo.setStatus(device.getStatus());
        vo.setLastHeartbeat(device.getLastHeartbeat());
        vo.setSignalStrength(device.getSignalStrength());
        vo.setBattery(device.getBattery());
        vo.setRegisterTime(device.getCreatedAt());
        vo.setCreatedAt(device.getCreatedAt());
        vo.setUpdatedAt(device.getUpdatedAt());

        DeviceCapabilitiesVO capabilities = buildCapabilities(device);
        vo.setCapabilities(capabilities);
        vo.setTelemetryEnabled(capabilities.getTelemetry());
        vo.setPumpControllable(capabilities.getPumpControl());
        vo.setLightControllable(capabilities.getLightControl());
        return vo;
    }

    private DeviceCapabilitiesVO buildCapabilities(Device device) {
        String deviceType = device.getDeviceType() == null ? "" : device.getDeviceType().toUpperCase(Locale.ROOT);
        boolean isRealBearPi = REAL_BEARPI_DEVICE_CODE.equals(device.getDeviceCode()) || "BEARPI".equals(deviceType);
        boolean soilSensor = deviceType.contains("SOIL");
        boolean sensor = deviceType.contains("SENSOR") || soilSensor || isRealBearPi;
        boolean pumpController = deviceType.contains("PUMP") || isRealBearPi;
        boolean lightController = deviceType.contains("LIGHT") || isRealBearPi;

        DeviceCapabilitiesVO capabilities = new DeviceCapabilitiesVO();
        capabilities.setTelemetry(sensor || isRealBearPi);
        capabilities.setPumpControl(pumpController);
        capabilities.setLightControl(lightController);
        capabilities.setSoilMoisture(soilSensor || isRealBearPi);
        capabilities.setAirTemperature(sensor || isRealBearPi);
        capabilities.setAirHumidity(sensor || isRealBearPi);
        capabilities.setIlluminance(sensor || isRealBearPi);
        return capabilities;
    }
}
