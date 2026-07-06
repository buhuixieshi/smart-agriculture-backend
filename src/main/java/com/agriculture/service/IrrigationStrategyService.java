package com.agriculture.service;

import com.agriculture.dto.IrrigationStrategyDTO;
import com.agriculture.entity.IrrigationStrategy;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IrrigationStrategyService extends IService<IrrigationStrategy> {

    IrrigationStrategy getByPlotId(Long plotId);

    IrrigationStrategy getOrCreateDefault(Long plotId);

    IrrigationStrategy saveOrUpdateByPlotId(Long plotId, IrrigationStrategyDTO dto);

    void deleteByPlotId(Long plotId);
}
