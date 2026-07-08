package com.agriculture.service.impl;

import com.agriculture.config.AiChatModelProperties;
import com.agriculture.dto.AiChatModelResponse;
import com.agriculture.service.AiChatModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HttpAiChatModelClient implements AiChatModelClient {

    private final AiChatModelProperties properties;
    private final RestTemplate restTemplate;

    public HttpAiChatModelClient(AiChatModelProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    @Override
    public Optional<AiChatModelResponse> chat(Long plotId, String question) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("plotId", plotId);
            request.put("query", question);
            request.put("message", question);
            request.put("question", question);
            request.put("systemPrompt", "你是智慧农业系统的智能农事助手，请用中文回答灌溉、补光、设备状态、告警、害虫识别和农事管理相关问题。回答要简洁、可执行。");

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    properties.getUrl(),
                    request,
                    JsonNode.class
            );

            JsonNode body = response.getBody();
            String answer = firstText(body, "answer", "reply", "response", "text", "content", "message");
            if (answer == null || answer.isBlank()) {
                return Optional.empty();
            }

            AiChatModelResponse result = new AiChatModelResponse();
            result.setAnswer(answer.trim());
            result.setSource(firstText(body, "source"));
            result.setModelStatus(firstText(body, "modelStatus", "model_status", "status"));
            result.setMessage(firstText(body, "message"));
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI chat model request failed, url={}, plotId={}, reason={}",
                    properties.getUrl(), plotId, e.getMessage());
            return Optional.empty();
        }
    }

    private String firstText(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }

        String direct = firstTextDirect(node, names);
        if (direct != null) {
            return direct;
        }

        JsonNode data = node.get("data");
        String fromData = firstTextDirect(data, names);
        if (fromData != null) {
            return fromData;
        }

        JsonNode result = node.get("result");
        return firstTextDirect(result, names);
    }

    private String firstTextDirect(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                if (value.isTextual()) {
                    return value.asText();
                }
                if (value.isNumber() || value.isBoolean()) {
                    return value.asText();
                }
            }
        }
        return null;
    }
}
