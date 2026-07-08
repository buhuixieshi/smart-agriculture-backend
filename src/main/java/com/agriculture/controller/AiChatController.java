package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.dto.AiChatDTO;
import com.agriculture.service.AiChatService;
import com.agriculture.vo.AiChatVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody(required = false) AiChatDTO dto) {
        return Result.ok(aiChatService.chat(dto));
    }
}
