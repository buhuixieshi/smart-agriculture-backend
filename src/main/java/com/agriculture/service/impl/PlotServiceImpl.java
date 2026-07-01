package com.agriculture.service.impl;

import com.agriculture.entity.Plot;
import com.agriculture.mapper.PlotMapper;
import com.agriculture.service.PlotService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class PlotServiceImpl extends ServiceImpl<PlotMapper, Plot> implements PlotService {
}
