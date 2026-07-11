package com.agriculture.service.impl;

import com.agriculture.config.AiChatModelProperties;
import com.agriculture.dto.AiChatDTO;
import com.agriculture.dto.AiChatModelResponse;
import com.agriculture.service.AiChatModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class HttpAiChatModelClient implements AiChatModelClient {

    private final AiChatModelProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public HttpAiChatModelClient(AiChatModelProperties properties,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    @Override
    public Optional<AiChatModelResponse> chat(AiChatDTO dto, String question) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        Long plotId = dto == null ? null : dto.getPlotId();
        String requestJson = null;
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("conversationId", resolveConversationId(dto));
            request.put("userId", dto == null || dto.getUserId() == null ? 0L : dto.getUserId());
            if (plotId != null) {
                request.put("plotId", plotId);
            }
            request.put("message", question);
            request.put("context", dto == null || dto.getContext() == null ? Map.of() : dto.getContext());
            request.put("forceCommit", dto != null && Boolean.TRUE.equals(dto.getForceCommit()));

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getUrl().trim()))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json");
            String token = cleanToken(properties.getToken());
            if (token != null) {
                requestBuilder.header("X-AI-Service-Token", token);
            }

            requestJson = objectMapper.writeValueAsString(request);
            log.debug("AI chat request json={}", requestJson);
            HttpRequest httpRequest = requestBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("AI chat model request failed, url={}, status={}, response={}, requestJson={}",
                        properties.getUrl(), response.statusCode(), response.body(), requestJson);
                return Optional.empty();
            }

            JsonNode body = objectMapper.readTree(response.body());
            String answer = firstText(body, "answer", "reply", "response", "text", "content");
            if (answer == null || answer.isBlank()) {
                answer = firstText(body, "message");
            }
            if (answer == null || answer.isBlank()) {
                return Optional.empty();
            }

            AiChatModelResponse result = new AiChatModelResponse();
            result.setAnswer(answer.trim());
            result.setSource(firstText(body, "source"));
            result.setModelStatus(firstText(body, "modelStatus", "model_status", "status"));
            result.setMessage(firstText(body, "message"));
            result.setSuggestions(firstTextList(body, "suggestions"));
            result.setActionProposal(firstObjectMap(body, "actionProposal", "action_proposal"));
            String conversationId = firstText(body, "conversationId", "conversation_id");
            result.setConversationId(conversationId == null || conversationId.isBlank()
                    ? resolveConversationId(dto)
                    : conversationId);
            result.setMemoryCommitted(firstBoolean(body, "memoryCommitted", "memory_committed"));
            result.setMemoryRecallCount(firstInteger(body, "memoryRecallCount", "memory_recall_count"));
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("AI chat model request failed, url={}, plotId={}, reason={}, requestJson={}",
                    properties.getUrl(), plotId, e.getMessage(), requestJson);
            return Optional.empty();
        }
    }

    private String cleanToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String cleaned = token.trim();
        if (cleaned.contains("\r") || cleaned.contains("\n")) {
            log.warn("AI service token contains line breaks and will not be sent");
            return null;
        }
        return cleaned.isBlank() ? null : cleaned;
    }

    private String resolveConversationId(AiChatDTO dto) {
        if (dto != null && dto.getConversationId() != null && !dto.getConversationId().isBlank()) {
            return dto.getConversationId();
        }
        Long userId = dto == null ? null : dto.getUserId();
        Long plotId = dto == null ? null : dto.getPlotId();
        if (userId != null && plotId != null) {
            return "user-" + userId + "-plot-" + plotId;
        }
        if (userId != null) {
            return "user-" + userId;
        }
        if (plotId != null) {
            return "plot-" + plotId;
        }
        return "conversation-default";
    }

    private String firstText(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return null;
    }

    private List<String> firstTextList(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (item != null && item.isValueNode()) {
                String text = item.asText();
                if (text != null && !text.isBlank()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstObjectMap(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || !value.isObject()) {
            return null;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private Boolean firstBoolean(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isTextual()) {
            return Boolean.parseBoolean(value.asText());
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... names) {
        JsonNode value = firstNode(node, names);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isInt() || value.isLong()) {
            return value.asInt();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private JsonNode firstNode(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }

        JsonNode direct = firstNodeDirect(node, names);
        if (direct != null) {
            return direct;
        }

        JsonNode data = node.get("data");
        direct = firstNodeDirect(data, names);
        if (direct != null) {
            return direct;
        }

        return firstNodeDirect(node.get("result"), names);
    }

    private JsonNode firstNodeDirect(JsonNode node, String... names) {
        if (node == null) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }
}
