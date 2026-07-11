package com.agriculture.service.impl;

import com.agriculture.config.PestModelProperties;
import com.agriculture.dto.PestModelResponse;
import com.agriculture.service.PestModelClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Service
@Slf4j
public class HttpPestModelClient implements PestModelClient {

    private final PestModelProperties properties;
    private final RestTemplate restTemplate;

    public HttpPestModelClient(PestModelProperties properties, RestTemplateBuilder restTemplateBuilder) {
        this.properties = properties;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .readTimeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .build();
    }

    @Override
    public Optional<PestModelResponse> detect(Long plotId, MultipartFile file) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return Optional.empty();
        }

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            HttpHeaders fileHeaders = new HttpHeaders();
            String contentType = file.getContentType();
            fileHeaders.setContentType(contentType == null || contentType.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(contentType));
            body.add("image", new HttpEntity<>(new MultipartFileResource(file), fileHeaders));
            if (plotId != null) {
                body.add("plotId", String.valueOf(plotId));
                body.add("conversationId", "pest-plot-" + plotId);
            }
            body.add("context", "{}");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (properties.getToken() != null && !properties.getToken().isBlank()) {
                headers.set("X-AI-Service-Token", properties.getToken());
            }

            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    properties.getUrl(),
                    new HttpEntity<>(body, headers),
                    JsonNode.class
            );

            PestModelResponse result = toResponse(response.getBody());
            if (result == null || isBlank(result.getPestName())) {
                return Optional.empty();
            }
            if (isBlank(result.getPestId())) {
                result.setPestId(buildModelPestId(result.getPestName()));
            }
            return Optional.of(result);
        } catch (Exception e) {
            log.warn("Pest model request failed, url={}, plotId={}, reason={}",
                    properties.getUrl(), plotId, e.getMessage());
            return Optional.empty();
        }
    }

    private PestModelResponse toResponse(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }

        JsonNode observation = root.path("observation");
        JsonNode advice = root.path("advice");

        PestModelResponse response = new PestModelResponse();
        response.setPestId(firstText(root, observation, "pestId", "pest_id"));
        response.setPestName(firstText(root, observation, "pestName", "pest_name", "name"));
        response.setDangerLevel(firstText(root, observation, "dangerLevel", "danger_level", "riskLevel"));
        response.setConfidence(firstDouble(root, observation, "confidence"));
        response.setAnswer(firstText(root, advice, "answer"));
        response.setSource(firstText(root, advice, "source"));
        response.setModelStatus(firstText(root, advice, "modelStatus", "model_status", "status"));
        response.setMessage(firstText(root, observation, "safetyNote", "message"));
        return response;
    }

    private String firstText(JsonNode root, JsonNode nested, String... names) {
        String text = firstTextDirect(nested, names);
        if (!isBlank(text)) {
            return text;
        }
        return firstTextDirect(root, names);
    }

    private String firstTextDirect(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && value.isValueNode()) {
                return value.asText();
            }
        }
        return null;
    }

    private Double firstDouble(JsonNode root, JsonNode nested, String... names) {
        Double value = firstDoubleDirect(nested, names);
        if (value != null) {
            return value;
        }
        return firstDoubleDirect(root, names);
    }

    private Double firstDoubleDirect(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return null;
    }

    private String buildModelPestId(String pestName) {
        return "model_" + Integer.toHexString(Math.abs(pestName.hashCode()));
    }

    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static class MultipartFileResource extends InputStreamResource {

        private final MultipartFile file;

        MultipartFileResource(MultipartFile file) throws IOException {
            super(file.getInputStream());
            this.file = file;
        }

        @Override
        public String getFilename() {
            return file.getOriginalFilename();
        }

        @Override
        public long contentLength() {
            return file.getSize();
        }
    }
}
