package com.agriculture.service.impl;

import com.agriculture.entity.PestKnowledge;
import com.agriculture.mapper.PestKnowledgeMapper;
import com.agriculture.service.PestKnowledgeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PestKnowledgeServiceImpl extends ServiceImpl<PestKnowledgeMapper, PestKnowledge>
        implements PestKnowledgeService {

    @Override
    public PestKnowledge getByPestId(String pestId) {
        return this.getOne(
                new LambdaQueryWrapper<PestKnowledge>()
                        .eq(PestKnowledge::getPestId, pestId)
                        .last("LIMIT 1"),
                false
        );
    }

    @Override
    public List<PestKnowledge> listAll() {
        return this.list(
                new LambdaQueryWrapper<PestKnowledge>()
                        .orderByAsc(PestKnowledge::getId)
        );
    }
}
