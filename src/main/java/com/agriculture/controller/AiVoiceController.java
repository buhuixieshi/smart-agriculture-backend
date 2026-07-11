package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.dto.AiVoiceSynthesizeDTO;
import com.agriculture.service.AiVoiceService;
import com.agriculture.vo.AiVoiceChatVO;
import com.agriculture.vo.AiVoiceSynthesizeVO;
import com.agriculture.vo.AiVoiceTranscribeVO;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/ai/voice")
public class AiVoiceController {

    private final AiVoiceService aiVoiceService;

    public AiVoiceController(AiVoiceService aiVoiceService) {
        this.aiVoiceService = aiVoiceService;
    }

    @PostMapping("/transcribe")
    public Result<AiVoiceTranscribeVO> transcribe(@RequestParam("file") MultipartFile file) {
        return Result.ok(aiVoiceService.transcribe(file));
    }

    @PostMapping("/chat")
    public Result<AiVoiceChatVO> chat(@RequestParam("file") MultipartFile file,
                                      @RequestParam(value = "plotId", required = false) Long plotId,
                                      @RequestParam(value = "deviceCode", required = false) String deviceCode,
                                      @RequestParam(value = "conversationId", required = false) String conversationId,
                                      @RequestParam(value = "forceCommit", required = false) Boolean forceCommit,
                                      @RequestParam(value = "synthesize", required = false) Boolean synthesize,
                                      Authentication authentication) {
        return Result.ok(aiVoiceService.chat(
                file,
                resolveUserId(authentication),
                plotId,
                deviceCode,
                conversationId,
                forceCommit,
                synthesize
        ));
    }

    @PostMapping("/synthesize")
    public Result<AiVoiceSynthesizeVO> synthesize(@RequestBody AiVoiceSynthesizeDTO dto) {
        return Result.ok(aiVoiceService.synthesize(dto));
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("用户未登录");
        }

        Object details = authentication.getDetails();
        if (!(details instanceof Claims claims)) {
            throw new IllegalArgumentException("无法读取登录用户信息");
        }

        Object rawUserId = claims.get("userId");
        if (rawUserId instanceof Number number) {
            return number.longValue();
        }
        if (rawUserId != null && !rawUserId.toString().isBlank()) {
            return Long.parseLong(rawUserId.toString());
        }

        throw new IllegalArgumentException("JWT中缺少userId");
    }
}
