package com.agriculture.service;

import com.agriculture.dto.LightStrategyDTO;
import com.agriculture.entity.LightStrategy;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface LightStrategyService extends IService<LightStrategy> {

    LightStrategy getByPlotId(Long plotId);

    LightStrategy getOrCreateDefault(Long plotId);

    LightStrategy saveOrUpdateByPlotId(Long plotId, LightStrategyDTO dto);

    List<LightStrategy> listAllOrderByPlot();
}
