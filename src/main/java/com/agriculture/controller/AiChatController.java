package com.agriculture.controller;

import com.agriculture.common.Result;
import com.agriculture.dto.AiChatDTO;
import com.agriculture.service.AiChatService;
import com.agriculture.vo.AiChatVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    public AiChatController(AiChatService aiChatService, ObjectMapper objectMapper) {
        this.aiChatService = aiChatService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody(required = false) JsonNode body,
                                 Authentication authentication) {
        AiChatDTO dto = toDto(body);
        dto.setUserId(resolveUserId(authentication));
        return Result.ok(aiChatService.chat(dto));
    }

    private AiChatDTO toDto(JsonNode body) {
        AiChatDTO dto = new AiChatDTO();
        if (body == null || body.isNull()) {
            return dto;
        }
        if (body.isTextual()) {
            dto.setMessage(body.asText());
            return dto;
        }
        if (!body.isObject()) {
            dto.setMessage(body.asText(""));
            return dto;
        }

        dto = objectMapper.convertValue(body, AiChatDTO.class);
        String message = firstText(body, "message", "question", "query", "input", "prompt", "text", "content");
        if (message == null || message.isBlank()) {
            message = lastUserMessage(body.get("messages"));
        }
        if (message != null && !message.isBlank()) {
            dto.setMessage(message);
        }
        return dto;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && value.isValueNode()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String lastUserMessage(JsonNode messages) {
        if (messages == null || !messages.isArray()) {
            return null;
        }
        String fallback = null;
        for (JsonNode item : messages) {
            if (item == null || !item.isObject()) {
                continue;
            }
            JsonNode content = item.get("content");
            if (content == null || !content.isValueNode()) {
                continue;
            }
            String text = content.asText();
            if (text == null || text.isBlank()) {
                continue;
            }
            fallback = text;
            JsonNode role = item.get("role");
            if (role != null && "user".equalsIgnoreCase(role.asText())) {
                fallback = text;
            }
        }
        return fallback;
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
