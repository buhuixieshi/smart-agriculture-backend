package com.agriculture.service;

import com.agriculture.entity.PestKnowledge;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface PestKnowledgeService extends IService<PestKnowledge> {

    PestKnowledge getByPestId(String pestId);

    List<PestKnowledge> listAll();
}
