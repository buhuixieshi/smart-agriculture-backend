package com.agriculture.service.impl;

import com.agriculture.dto.LightStrategyDTO;
import com.agriculture.entity.LightStrategy;
import com.agriculture.mapper.LightStrategyMapper;
import com.agriculture.service.LightStrategyService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class LightStrategyServiceImpl extends ServiceImpl<LightStrategyMapper, LightStrategy>
        implements LightStrategyService {

    @Override
    public LightStrategy getByPlotId(Long plotId) {
        return this.getOne(
                new LambdaQueryWrapper<LightStrategy>()
                        .eq(LightStrategy::getPlotId, plotId)
                        .last("LIMIT 1"),
                false
        );
    }

    @Override
    @Transactional
    public LightStrategy getOrCreateDefault(Long plotId) {
        LocalDateTime now = LocalDateTime.now();
        try {
            LightStrategy strategy = getByPlotId(plotId);
            if (strategy != null) {
                return strategy;
            }

            strategy = defaultStrategy(plotId);
            strategy.setCreateTime(now);
            strategy.setUpdateTime(now);
            this.save(strategy);
            return strategy;
        } catch (DataAccessException e) {
            LightStrategy strategy = defaultStrategy(plotId);
            strategy.setCreateTime(now);
            strategy.setUpdateTime(now);
            return strategy;
        }
    }

    @Override
    @Transactional
    public LightStrategy saveOrUpdateByPlotId(Long plotId, LightStrategyDTO dto) {
        LightStrategy strategy = getByPlotId(plotId);
        LocalDateTime now = LocalDateTime.now();
        if (strategy == null) {
            strategy = defaultStrategy(plotId);
            strategy.setCreateTime(now);
        }

        if (dto.getIlluminanceMin() != null) {
            strategy.setIlluminanceMin(dto.getIlluminanceMin());
        }
        if (dto.getIlluminanceMax() != null) {
            strategy.setIlluminanceMax(dto.getIlluminanceMax());
        }
        if (dto.getAutoMode() != null) {
            strategy.setAutoMode(dto.getAutoMode());
        }
        if (dto.getStartTime() != null) {
            strategy.setStartTime(dto.getStartTime());
        }
        if (dto.getEndTime() != null) {
            strategy.setEndTime(dto.getEndTime());
        }
        if (dto.getCooldownMinutes() != null) {
            strategy.setCooldownMinutes(dto.getCooldownMinutes());
        }

        validate(strategy);
        strategy.setUpdateTime(now);
        this.saveOrUpdate(strategy);
        return strategy;
    }

    @Override
    public List<LightStrategy> listAllOrderByPlot() {
        try {
            return this.list(new LambdaQueryWrapper<LightStrategy>().orderByAsc(LightStrategy::getPlotId));
        } catch (DataAccessException e) {
            return List.of();
        }
    }

    private LightStrategy defaultStrategy(Long plotId) {
        LightStrategy strategy = new LightStrategy();
        strategy.setPlotId(plotId);
        strategy.setIlluminanceMin(new BigDecimal("500.00"));
        strategy.setIlluminanceMax(new BigDecimal("800.00"));
        strategy.setAutoMode(true);
        strategy.setStartTime(LocalTime.of(6, 0));
        strategy.setEndTime(LocalTime.of(20, 0));
        strategy.setCooldownMinutes(5);
        return strategy;
    }

    private void validate(LightStrategy strategy) {
        if (strategy.getIlluminanceMin() == null || strategy.getIlluminanceMax() == null) {
            return;
        }
        if (strategy.getIlluminanceMin().compareTo(strategy.getIlluminanceMax()) >= 0) {
            throw new IllegalArgumentException("补光开启阈值必须小于关闭阈值");
        }
    }
}
