package com.agriculture.service;

import com.agriculture.dto.PlotDTO;
import com.agriculture.entity.Plot;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PlotService extends IService<Plot> {

    Plot createPlot(PlotDTO dto);

    Plot updatePlot(Long id, PlotDTO dto);

    void deletePlot(Long id);
}
