package com.agriculture.service.impl;

import com.agriculture.dto.PlotDTO;
import com.agriculture.entity.Device;
import com.agriculture.entity.Plot;
import com.agriculture.mapper.PlotMapper;
import com.agriculture.service.DeviceService;
import com.agriculture.service.PlotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {

    private final DeviceService deviceService;

    public PlotServiceImpl(@Lazy DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    @Transactional
    public Plot createPlot(PlotDTO dto) {
        Plot plot = new Plot();
        plot.setName(dto.getName());
        plot.setCropType(dto.getCropType());
        plot.setLocation(dto.getLocation());
        plot.setArea(dto.getArea());
        plot.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? "ONLINE" : dto.getStatus());
        plot.setDescription(dto.getDescription());
        plot.setCreatedAt(LocalDateTime.now());
        plot.setUpdatedAt(LocalDateTime.now());

        this.save(plot);
        return plot;
    }

    @Override
    @Transactional
    public Plot updatePlot(Long id, PlotDTO dto) {
        Plot plot = this.getById(id);
        if (plot == null) {
            throw new IllegalArgumentException("地块不存在：" + id);
        }

        plot.setName(dto.getName());
        plot.setCropType(dto.getCropType());
        plot.setLocation(dto.getLocation());
        plot.setArea(dto.getArea());
        plot.setStatus(dto.getStatus() == null || dto.getStatus().isBlank() ? plot.getStatus() : dto.getStatus());
        plot.setDescription(dto.getDescription());
        plot.setUpdatedAt(LocalDateTime.now());

        this.updateById(plot);
        return plot;
    }

    @Override
    @Transactional
    public void deletePlot(Long id) {
        Plot plot = this.getById(id);
        if (plot == null) {
            throw new IllegalArgumentException("地块不存在：" + id);
        }

        long deviceCount = deviceService.count(
                new LambdaQueryWrapper<Device>().eq(Device::getPlotId, id)
        );
        if (deviceCount > 0) {
            throw new IllegalArgumentException("该地块下还有设备，不能删除，请先解绑或删除设备");
        }

        this.removeById(id);
    }
}
