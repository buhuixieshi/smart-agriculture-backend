package com.agriculture.service.impl;

import com.agriculture.config.HardwareControlProperties;
import com.agriculture.entity.ControlCommand;
import com.agriculture.service.HardwareControlClient;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HardwareControlClientImpl implements HardwareControlClient {

    private final String device;
    private final RestClient restClient;

    public HardwareControlClientImpl(HardwareControlProperties properties,
                                     RestClient.Builder restClientBuilder) {
        this.device = properties.getDevice();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(5000);

        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String sendCommand(ControlCommand command) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("device", device);
        body.put("type", resolveType(command.getCommandType()));
        body.put("action", resolveAction(command));
        if (command.getDurationSeconds() != null) {
            body.put("durationSeconds", command.getDurationSeconds());
        }
        if (command.getBrightness() != null) {
            body.put("brightness", command.getBrightness());
        }

        JsonNode response = restClient.post()
                .uri("/api/control")
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw new IllegalStateException("Hardware control response is empty");
        }

        int code = response.path("code").asInt(-1);
        String message = response.path("msg").asText(response.path("message").asText(""));

        if (code != 200) {
            throw new IllegalStateException("Hardware control failed: " + message);
        }

        return message == null || message.isBlank() ? "ok" : message;
    }

    private String resolveType(String commandType) {
        if ("PUMP_ON".equals(commandType) || "PUMP_OFF".equals(commandType)) {
            return "motor";
        }

        if ("LIGHT_ON".equals(commandType) || "LIGHT_OFF".equals(commandType)) {
            return "light";
        }

        throw new IllegalArgumentException("Unsupported hardware command type: " + commandType);
    }

    private String resolveAction(ControlCommand command) {
        if (command.getCommandValue() != null && !command.getCommandValue().isBlank()) {
            if ("LIGHT_ON".equals(command.getCommandType()) || "LIGHT_OFF".equals(command.getCommandType())) {
                return command.getCommandValue().trim();
            }
            return command.getCommandValue().trim().toUpperCase();
        }

        if (command.getCommandType().endsWith("_ON")) {
            return "ON";
        }

        if (command.getCommandType().endsWith("_OFF")) {
            return "OFF";
        }

        throw new IllegalArgumentException("Unsupported hardware command action: " + command.getCommandType());
    }
}
