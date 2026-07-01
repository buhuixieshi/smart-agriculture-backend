package com.agriculture.service.impl;

import com.agriculture.service.RealtimePushService;
import com.agriculture.websocket.RealtimeWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class RealtimePushServiceImpl implements RealtimePushService {

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final ObjectMapper objectMapper;

    public RealtimePushServiceImpl(RealtimeWebSocketHandler realtimeWebSocketHandler,
                                   ObjectMapper objectMapper) {
        this.realtimeWebSocketHandler = realtimeWebSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public void pushTelemetryTest() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("soilMoisture", 32.0);
            data.put("airTemperature", 27.0);
            data.put("airHumidity", 65.0);
            data.put("illuminance", 850.0);
            data.put("pumpStatus", "OFF");
            data.put("lightStatus", "OFF");
            data.put("collectedAt", LocalDateTime.now().toString());

            Map<String, Object> message = new HashMap<>();
            message.put("type", "TELEMETRY");
            message.put("plotId", 1);
            message.put("data", data);

            String json = objectMapper.writeValueAsString(message);
            realtimeWebSocketHandler.sendToAll(json);
        } catch (Exception e) {
            throw new RuntimeException("WebSocket push failed", e);
        }
    }

    @Override
    public int getOnlineCount() {
        return realtimeWebSocketHandler.getOnlineCount();
    }
}
