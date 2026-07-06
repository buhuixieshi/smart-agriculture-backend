package com.agriculture.service.impl;

import com.agriculture.dto.IrrigationStrategyDTO;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.entity.Plot;
import com.agriculture.mapper.IrrigationStrategyMapper;
import com.agriculture.service.IrrigationStrategyService;
import com.agriculture.service.PlotService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class IrrigationStrategyServiceImpl
        extends ServiceImpl<IrrigationStrategyMapper, IrrigationStrategy>
        implements IrrigationStrategyService {

    private final PlotService plotService;

    public IrrigationStrategyServiceImpl(PlotService plotService) {
        this.plotService = plotService;
    }

    @Override
    public IrrigationStrategy getByPlotId(Long plotId) {
        return this.getOne(
                new LambdaQueryWrapper<IrrigationStrategy>()
                        .eq(IrrigationStrategy::getPlotId, plotId)
                        .last("LIMIT 1"),
                false
        );
    }

    @Override
    @Transactional
    public IrrigationStrategy getOrCreateDefault(Long plotId) {
        checkPlotExists(plotId);

        IrrigationStrategy strategy = getByPlotId(plotId);
        if (strategy != null) {
            return strategy;
        }

        LocalDateTime now = LocalDateTime.now();
        strategy = new IrrigationStrategy();
        strategy.setPlotId(plotId);
        strategy.setMoistureMin(new BigDecimal("40.00"));
        strategy.setMoistureMax(new BigDecimal("70.00"));
        strategy.setConsecutiveThreshold(3);
        strategy.setAutoMode(true);
        strategy.setMaxDuration(900);
        strategy.setCooldownMinutes(10);
        strategy.setCreateTime(now);
        strategy.setUpdateTime(now);
        this.save(strategy);
        return strategy;
    }

    @Override
    @Transactional
    public IrrigationStrategy saveOrUpdateByPlotId(Long plotId, IrrigationStrategyDTO dto) {
        checkPlotExists(plotId);
        validateRange(dto);

        IrrigationStrategy strategy = getByPlotId(plotId);
        LocalDateTime now = LocalDateTime.now();
        if (strategy == null) {
            strategy = new IrrigationStrategy();
            strategy.setPlotId(plotId);
            strategy.setCreateTime(now);
        }

        if (dto.getMoistureMin() != null) {
            strategy.setMoistureMin(dto.getMoistureMin());
        }
        if (dto.getMoistureMax() != null) {
            strategy.setMoistureMax(dto.getMoistureMax());
        }
        if (dto.getConsecutiveThreshold() != null) {
            strategy.setConsecutiveThreshold(dto.getConsecutiveThreshold());
        }
        if (dto.getAutoMode() != null) {
            strategy.setAutoMode(dto.getAutoMode());
        }
        if (dto.getMaxDuration() != null) {
            strategy.setMaxDuration(dto.getMaxDuration());
        }
        if (dto.getCooldownMinutes() != null) {
            strategy.setCooldownMinutes(dto.getCooldownMinutes());
        }

        fillDefaults(strategy);
        validateFinalRange(strategy);
        strategy.setUpdateTime(now);
        this.saveOrUpdate(strategy);
        return strategy;
    }

    @Override
    @Transactional
    public void deleteByPlotId(Long plotId) {
        IrrigationStrategy strategy = getByPlotId(plotId);
        if (strategy == null) {
            throw new IllegalArgumentException("阈值策略不存在：" + plotId);
        }
        this.removeById(strategy.getId());
    }

    private void validateRange(IrrigationStrategyDTO dto) {
        if (dto.getMoistureMin() != null
                && dto.getMoistureMax() != null
                && dto.getMoistureMin().compareTo(dto.getMoistureMax()) >= 0) {
            throw new IllegalArgumentException("土壤湿度下限必须小于上限");
        }
    }

    private void validateFinalRange(IrrigationStrategy strategy) {
        if (strategy.getMoistureMin() != null
                && strategy.getMoistureMax() != null
                && strategy.getMoistureMin().compareTo(strategy.getMoistureMax()) >= 0) {
            throw new IllegalArgumentException("土壤湿度下限必须小于上限");
        }
    }

    private void fillDefaults(IrrigationStrategy strategy) {
        if (strategy.getMoistureMin() == null) {
            strategy.setMoistureMin(new BigDecimal("40.00"));
        }
        if (strategy.getMoistureMax() == null) {
            strategy.setMoistureMax(new BigDecimal("70.00"));
        }
        if (strategy.getConsecutiveThreshold() == null || strategy.getConsecutiveThreshold() <= 0) {
            strategy.setConsecutiveThreshold(3);
        }
        if (strategy.getAutoMode() == null) {
            strategy.setAutoMode(true);
        }
        if (strategy.getMaxDuration() == null || strategy.getMaxDuration() <= 0) {
            strategy.setMaxDuration(900);
        }
        if (strategy.getCooldownMinutes() == null || strategy.getCooldownMinutes() < 0) {
            strategy.setCooldownMinutes(10);
        }
    }

    private void checkPlotExists(Long plotId) {
        Plot plot = plotService.getById(plotId);
        if (plot == null) {
            throw new IllegalArgumentException("地块不存在：" + plotId);
        }
    }
}
