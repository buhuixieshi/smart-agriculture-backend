package com.agriculture.service.impl;

import com.agriculture.dto.AiChatDTO;
import com.agriculture.dto.AiChatModelResponse;
import com.agriculture.entity.Alarm;
import com.agriculture.entity.Device;
import com.agriculture.entity.IrrigationStrategy;
import com.agriculture.entity.LightStrategy;
import com.agriculture.entity.Plot;
import com.agriculture.entity.TelemetryData;
import com.agriculture.service.AiChatModelClient;
import com.agriculture.service.AiChatService;
import com.agriculture.service.AlarmService;
import com.agriculture.service.DeviceService;
import com.agriculture.service.IrrigationStrategyService;
import com.agriculture.service.LightStrategyService;
import com.agriculture.service.PlotService;
import com.agriculture.service.TelemetryService;
import com.agriculture.vo.AiChatVO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiChatServiceImpl implements AiChatService {

    private static final int MAX_CONTEXT_PLOTS = 20;
    private static final int MAX_CONTEXT_DEVICES = 30;
    private static final int MAX_CONTEXT_ALARMS = 10;

    private final AiChatModelClient aiChatModelClient;
    private final PlotService plotService;
    private final DeviceService deviceService;
    private final TelemetryService telemetryService;
    private final AlarmService alarmService;
    private final IrrigationStrategyService irrigationStrategyService;
    private final LightStrategyService lightStrategyService;

    public AiChatServiceImpl(AiChatModelClient aiChatModelClient,
                             PlotService plotService,
                             DeviceService deviceService,
                             TelemetryService telemetryService,
                             AlarmService alarmService,
                             IrrigationStrategyService irrigationStrategyService,
                             LightStrategyService lightStrategyService) {
        this.aiChatModelClient = aiChatModelClient;
        this.plotService = plotService;
        this.deviceService = deviceService;
        this.telemetryService = telemetryService;
        this.alarmService = alarmService;
        this.irrigationStrategyService = irrigationStrategyService;
        this.lightStrategyService = lightStrategyService;
    }

    @Override
    public AiChatVO chat(AiChatDTO dto) {
        AiChatDTO request = dto == null ? new AiChatDTO() : dto;
        String question = normalizeQuestion(request);
        if (question.isBlank()) {
            throw new IllegalArgumentException("问题内容不能为空");
        }

        Long plotId = request.getPlotId();
        Map<String, Object> backendContext = buildBackendContext(request, question);
        request.setContext(mergeContext(request.getContext(), backendContext));

        Map<String, Object> localActionProposal = inferActionProposal(question, request);
        Optional<AiChatModelResponse> modelResponse = aiChatModelClient.chat(request, question);
        if (modelResponse.isPresent()) {
            return modelAnswer(request, plotId, question, modelResponse.get(), localActionProposal);
        }

        AiChatVO vo = new AiChatVO();
        vo.setConversationId(resolveConversationId(request));
        vo.setPlotId(plotId);
        vo.setQuestion(question);
        vo.setAnswer(fallbackAnswer(question, localActionProposal));
        vo.setStatus("fallback");
        vo.setSource("BACKEND_RULE_FALLBACK");
        vo.setModelStatus("MODEL_UNAVAILABLE");
        vo.setErrorMessage("AI 文本模型服务暂时不可用，已返回后端兜底回答。请检查 ai.chat.model.url、AI 服务进程和 AI_SERVICE_TOKEN。");
        vo.setSuggestions(defaultSuggestions());
        vo.setActionProposal(localActionProposal);
        vo.setAnsweredAt(LocalDateTime.now());
        return vo;
    }

    private AiChatVO modelAnswer(AiChatDTO dto,
                                 Long plotId,
                                 String question,
                                 AiChatModelResponse response,
                                 Map<String, Object> localActionProposal) {
        AiChatVO vo = new AiChatVO();
        vo.setConversationId(firstNonBlank(response.getConversationId(), resolveConversationId(dto)));
        vo.setPlotId(plotId);
        vo.setQuestion(question);
        vo.setAnswer(response.getAnswer());
        vo.setStatus("success");
        vo.setSource(firstNonBlank(response.getSource(), "AI_MODEL_SERVICE"));
        vo.setModelStatus(firstNonBlank(response.getModelStatus(), "SUCCESS"));
        vo.setSuggestions(response.getSuggestions() == null || response.getSuggestions().isEmpty()
                ? null
                : response.getSuggestions());
        Map<String, Object> modelActionProposal = normalizeModelAction(response.getActionProposal(), dto);
        vo.setActionProposal(modelActionProposal == null ? localActionProposal : modelActionProposal);
        vo.setMemoryCommitted(response.getMemoryCommitted());
        vo.setMemoryRecallCount(response.getMemoryRecallCount());
        vo.setAnsweredAt(LocalDateTime.now());
        return vo;
    }

    private Map<String, Object> mergeContext(Map<String, Object> frontendContext, Map<String, Object> backendContext) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (frontendContext != null) {
            merged.putAll(frontendContext);
        }
        merged.put("backendContext", backendContext);
        return merged;
    }

    private Map<String, Object> buildBackendContext(AiChatDTO dto, String question) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("projectName", "智慧农业系统");
        context.put("now", LocalDateTime.now().toString());
        context.put("userQuestion", question);
        context.put("systemInstruction", systemInstruction());
        context.put("frontendRoutes", frontendRoutes());
        context.put("actionProtocol", actionProtocol());

        Long plotId = dto.getPlotId();
        try {
            List<Plot> plots = plotService.list();
            context.put("plots", plots.stream().limit(MAX_CONTEXT_PLOTS).map(this::plotMap).toList());
            if (plotId != null) {
                context.put("currentPlot", plotMap(plotService.getById(plotId)));
            }
        } catch (Exception e) {
            context.put("plotsError", e.getMessage());
        }

        try {
            List<Device> devices = plotId == null ? deviceService.list() : deviceService.listByPlotId(plotId);
            context.put("devices", devices.stream().limit(MAX_CONTEXT_DEVICES).map(this::deviceMap).toList());
        } catch (Exception e) {
            context.put("devicesError", e.getMessage());
        }

        try {
            if (plotId != null) {
                context.put("latestTelemetry", telemetryMap(telemetryService.getLatestByPlotId(plotId)));
                context.put("irrigationStrategy", irrigationStrategyMap(irrigationStrategyService.getByPlotId(plotId)));
                context.put("lightStrategy", lightStrategyMap(lightStrategyService.getByPlotId(plotId)));
            } else {
                context.put("latestTelemetryByPlot", latestTelemetryByPlot());
            }
        } catch (Exception e) {
            context.put("telemetryOrStrategyError", e.getMessage());
        }

        try {
            List<Alarm> alarms = alarmService.query(plotId, null, null, "ACTIVE", null);
            context.put("activeAlarms", alarms.stream().limit(MAX_CONTEXT_ALARMS).map(this::alarmMap).toList());
        } catch (Exception e) {
            context.put("alarmsError", e.getMessage());
        }

        return context;
    }

    private String systemInstruction() {
        return """
                你是智慧农业项目的智能农事助手。回答必须优先依据 context.backendContext 中的实时数据。
                如果用户询问当前地块、设备、遥测、告警、灌溉策略、补光策略，必须结合后端提供的数据回答；没有数据时要明确说明缺少哪类数据。
                你不能假装已经操作硬件或浏览器页面。需要硬件控制时，返回 actionProposal，由前端展示确认后调用后端控制接口。
                如果用户要求停止、取消、结束正在执行的灌溉或补光任务，必须返回关闭类 actionProposal，例如 PUMP_OFF 或 LIGHT_OFF。
                需要页面跳转时，返回 actionProposal.type=NAVIGATE 和 route，由前端执行跳转。
                硬件控制动作必须使用现有后端接口，不要编造新接口。
                """;
    }

    private List<Map<String, Object>> frontendRoutes() {
        return List.of(
                route("dashboard", "首页/数据看板", "/"),
                route("plots", "地块管理", "/plots"),
                route("devices", "设备管理", "/devices"),
                route("telemetry", "遥测数据", "/telemetry"),
                route("alarms", "告警管理", "/alarms"),
                route("irrigation", "灌溉管理", "/irrigation"),
                route("light", "智能补光", "/light"),
                route("pest", "害虫识别", "/pest"),
                route("ai", "智能农事问答", "/ai")
        );
    }

    private Map<String, Object> actionProtocol() {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("navigate", Map.of(
                "type", "NAVIGATE",
                "route", "/devices",
                "description", "前端收到后执行页面跳转"
        ));
        protocol.put("pumpControl", Map.of(
                "type", "CONTROL_DEVICE",
                "requiresConfirmation", true,
                "api", "/api/control/send",
                "method", "POST",
                "paramsExample", Map.of(
                        "deviceCode", "设备编号",
                        "commandType", "PUMP_ON 或 PUMP_OFF",
                        "commandValue", "ON 或 OFF"
                )
        ));
        protocol.put("lightControl", Map.of(
                "type", "CONTROL_LIGHT",
                "requiresConfirmation", true,
                "api", "/api/light/control",
                "method", "POST",
                "bodyExample", Map.of(
                        "deviceCode", "设备编号",
                        "action", "ON 或 OFF",
                        "brightness", "0~100"
                )
        ));
        return protocol;
    }

    private Map<String, Object> inferActionProposal(String question, AiChatDTO dto) {
        String text = question.toLowerCase();

        Map<String, Object> navigation = inferNavigation(text);
        if (navigation != null) {
            return navigation;
        }

        Map<String, Object> stopAction = inferStopAction(text, dto);
        if (stopAction != null) {
            return stopAction;
        }

        if (containsAny(text, "打开水泵", "开启水泵", "开水泵", "pump on", "start pump")) {
            String deviceCode = resolveDeviceCode(dto);
            if (deviceCode == null || deviceCode.isBlank()) {
                return null;
            }
            return controlAction("CONTROL_DEVICE", "/api/control/send", Map.of(
                    "deviceCode", deviceCode,
                    "commandType", "PUMP_ON",
                    "commandValue", "ON"
            ), "检测到开水泵意图，前端确认后调用控制接口。");
        }
        if (containsAny(text, "关闭水泵", "关水泵", "pump off", "stop pump")) {
            String deviceCode = resolveDeviceCode(dto);
            if (deviceCode == null || deviceCode.isBlank()) {
                return null;
            }
            return controlAction("CONTROL_DEVICE", "/api/control/send", Map.of(
                    "deviceCode", deviceCode,
                    "commandType", "PUMP_OFF",
                    "commandValue", "OFF"
            ), "检测到关水泵意图，前端确认后调用控制接口。");
        }
        if (containsAny(text, "打开补光", "开启补光", "开灯", "打开灯", "light on")) {
            String deviceCode = resolveDeviceCode(dto);
            if (deviceCode == null || deviceCode.isBlank()) {
                return null;
            }
            return controlAction("CONTROL_LIGHT", "/api/light/control", Map.of(
                    "deviceCode", deviceCode,
                    "action", "ON"
            ), "检测到开启补光意图，前端确认后调用补光接口。");
        }
        if (containsAny(text, "关闭补光", "关灯", "关闭灯", "light off")) {
            String deviceCode = resolveDeviceCode(dto);
            if (deviceCode == null || deviceCode.isBlank()) {
                return null;
            }
            return controlAction("CONTROL_LIGHT", "/api/light/control", Map.of(
                    "deviceCode", deviceCode,
                    "action", "OFF"
            ), "检测到关闭补光意图，前端确认后调用补光接口。");
        }

        return null;
    }

    private Map<String, Object> inferStopAction(String text, AiChatDTO dto) {
        if (!containsAny(text, "停止", "停下", "取消", "结束", "关闭当前", "停止任务", "取消任务", "stop", "cancel")) {
            return null;
        }

        String deviceCode = resolveDeviceCode(dto);
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }

        if (containsAny(text, "补光", "灯", "light")) {
            return controlAction("CONTROL_LIGHT", "/api/light/control", Map.of(
                    "deviceCode", deviceCode,
                    "action", "OFF",
                    "brightness", 0
            ), "检测到停止补光任务意图，前端确认后关闭补光灯。");
        }

        if (containsAny(text, "水泵", "灌溉", "浇水", "泵", "pump", "irrigation", "watering")) {
            return controlAction("CONTROL_DEVICE", "/api/control/send", Map.of(
                    "deviceCode", deviceCode,
                    "commandType", "PUMP_OFF",
                    "commandValue", "OFF"
            ), "检测到停止当前任务意图，前端确认后关闭水泵。");
        }

        String runningTarget = resolveRunningStopTarget(dto);
        if ("LIGHT".equals(runningTarget)) {
            return controlAction("CONTROL_LIGHT", "/api/light/control", Map.of(
                    "deviceCode", deviceCode,
                    "action", "OFF",
                    "brightness", 0
            ), "检测到停止当前任务意图，当前补光灯可能正在运行，前端确认后关闭补光灯。");
        }

        return controlAction("CONTROL_DEVICE", "/api/control/send", Map.of(
                "deviceCode", deviceCode,
                "commandType", "PUMP_OFF",
                "commandValue", "OFF"
        ), "检测到停止当前任务意图，前端确认后关闭水泵。");
    }

    private String resolveRunningStopTarget(AiChatDTO dto) {
        if (dto == null || dto.getPlotId() == null) {
            return null;
        }
        try {
            TelemetryData latest = telemetryService.getLatestByPlotId(dto.getPlotId());
            if (latest == null) {
                return null;
            }
            if ("ON".equalsIgnoreCase(latest.getPumpStatus())) {
                return "PUMP";
            }
            if ("ON".equalsIgnoreCase(latest.getLightStatus())) {
                return "LIGHT";
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private Map<String, Object> inferNavigation(String text) {
        if (!containsAny(text, "跳转", "打开页面", "进入", "去", "查看页面", "navigate")) {
            return null;
        }
        if (containsAny(text, "设备")) {
            return navigateAction("/devices", "设备管理");
        }
        if (containsAny(text, "地块")) {
            return navigateAction("/plots", "地块管理");
        }
        if (containsAny(text, "告警", "报警")) {
            return navigateAction("/alarms", "告警管理");
        }
        if (containsAny(text, "灌溉", "水泵")) {
            return navigateAction("/irrigation", "灌溉管理");
        }
        if (containsAny(text, "补光", "灯光", "灯")) {
            return navigateAction("/light", "智能补光");
        }
        if (containsAny(text, "害虫", "虫害", "识别")) {
            return navigateAction("/pest", "害虫识别");
        }
        if (containsAny(text, "遥测", "传感器", "数据")) {
            return navigateAction("/telemetry", "遥测数据");
        }
        if (containsAny(text, "ai", "问答", "助手")) {
            return navigateAction("/ai", "智能农事问答");
        }
        return navigateAction("/", "首页/数据看板");
    }

    private Map<String, Object> navigateAction(String route, String title) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "NAVIGATE");
        action.put("route", route);
        action.put("title", title);
        action.put("requiresConfirmation", false);
        return action;
    }

    private Map<String, Object> controlAction(String type, String api, Map<String, Object> payload, String description) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", type);
        action.put("api", api);
        action.put("method", "POST");
        action.put("requiresConfirmation", true);
        action.put("payload", payload);
        action.put("description", description);
        return action;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeModelAction(Map<String, Object> rawAction, AiChatDTO dto) {
        if (rawAction == null || rawAction.isEmpty()) {
            return null;
        }

        String type = stringValue(rawAction.get("type"));
        if ("NAVIGATE".equalsIgnoreCase(type)) {
            String route = stringValue(rawAction.get("route"));
            if (route == null || route.isBlank()) {
                return null;
            }
            return navigateAction(route, firstNonBlank(stringValue(rawAction.get("title")), "页面跳转"));
        }

        Map<String, Object> parameters = new LinkedHashMap<>();
        copyMap(parameters, rawAction.get("parameters"));
        copyMap(parameters, rawAction.get("payload"));

        String deviceCode = firstNonBlank(
                stringValue(rawAction.get("deviceCode")),
                stringValue(parameters.get("deviceCode")),
                contextText(dto, "deviceCode")
        );
        if (deviceCode == null || deviceCode.isBlank()) {
            return null;
        }

        String action = firstNonBlank(
                stringValue(rawAction.get("action")),
                stringValue(rawAction.get("commandType")),
                stringValue(parameters.get("action")),
                stringValue(parameters.get("commandType"))
        );
        if (action == null || action.isBlank()) {
            return null;
        }

        String reason = firstNonBlank(stringValue(rawAction.get("reason")),
                stringValue(rawAction.get("description")),
                "AI识别到设备控制意图");

        String normalizedAction = action.trim().toUpperCase();
        if ("CONTROL_DEVICE".equalsIgnoreCase(type)) {
            normalizedAction = normalizePumpCommand(normalizedAction);
        } else if ("CONTROL_LIGHT".equalsIgnoreCase(type)) {
            normalizedAction = normalizeLightCommand(normalizedAction);
        } else if (isStopAction(normalizedAction)) {
            normalizedAction = inferStopCommandFromRawAction(rawAction, parameters);
        }

        return switch (normalizedAction) {
            case "PUMP_ON" -> controlAction(
                    "CONTROL_DEVICE",
                    "/api/control/send",
                    buildPumpPayload(deviceCode, "PUMP_ON", "ON", parameters),
                    reason
            );
            case "PUMP_OFF" -> controlAction(
                    "CONTROL_DEVICE",
                    "/api/control/send",
                    buildPumpPayload(deviceCode, "PUMP_OFF", "OFF", parameters),
                    reason
            );
            case "LIGHT_ON" -> controlAction(
                    "CONTROL_LIGHT",
                    "/api/light/control",
                    buildLightPayload(deviceCode, "ON", parameters),
                    reason
            );
            case "LIGHT_OFF" -> controlAction(
                    "CONTROL_LIGHT",
                    "/api/light/control",
                    buildLightPayload(deviceCode, "OFF", parameters),
                    reason
            );
            default -> null;
        };
    }

    private Map<String, Object> buildPumpPayload(String deviceCode,
                                                 String commandType,
                                                 String commandValue,
                                                 Map<String, Object> parameters) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceCode", deviceCode);
        payload.put("commandType", commandType);
        payload.put("commandValue", commandValue);
        putIfPresent(payload, "durationSeconds", parameters.get("durationSeconds"));
        return payload;
    }

    private Map<String, Object> buildLightPayload(String deviceCode,
                                                  String action,
                                                  Map<String, Object> parameters) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("deviceCode", deviceCode);
        payload.put("action", action);
        Object brightness = firstPresent(parameters.get("brightness"), parameters.get("value"), parameters.get("commandValue"));
        if ("OFF".equalsIgnoreCase(action) && brightness == null) {
            brightness = 0;
        }
        putIfPresent(payload, "brightness", brightness);
        putIfPresent(payload, "durationSeconds", parameters.get("durationSeconds"));
        return payload;
    }

    private String normalizePumpCommand(String action) {
        if ("ON".equals(action)) {
            return "PUMP_ON";
        }
        if ("OFF".equals(action)) {
            return "PUMP_OFF";
        }
        return action;
    }

    private String normalizeLightCommand(String action) {
        if ("ON".equals(action)) {
            return "LIGHT_ON";
        }
        if ("OFF".equals(action)) {
            return "LIGHT_OFF";
        }
        return action;
    }

    private boolean isStopAction(String action) {
        return "STOP".equals(action)
                || "CANCEL".equals(action)
                || "STOP_TASK".equals(action)
                || "CANCEL_TASK".equals(action)
                || "TASK_STOP".equals(action);
    }

    private String inferStopCommandFromRawAction(Map<String, Object> rawAction, Map<String, Object> parameters) {
        String target = firstNonBlank(
                stringValue(rawAction.get("target")),
                stringValue(rawAction.get("deviceType")),
                stringValue(rawAction.get("taskType")),
                stringValue(parameters.get("target")),
                stringValue(parameters.get("deviceType")),
                stringValue(parameters.get("taskType"))
        );
        if (target != null && containsAny(target.toLowerCase(), "light", "灯", "补光")) {
            return "LIGHT_OFF";
        }
        return "PUMP_OFF";
    }

    private void copyMap(Map<String, Object> target, Object source) {
        if (source instanceof Map<?, ?> sourceMap) {
            sourceMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    target.put(key.toString(), value);
                }
            });
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            target.put(key, value);
        }
    }

    private Object firstPresent(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String resolveDeviceCode(AiChatDTO dto) {
        Object contextDeviceCode = dto.getContext() == null ? null : dto.getContext().get("deviceCode");
        if (contextDeviceCode != null && !contextDeviceCode.toString().isBlank()) {
            return contextDeviceCode.toString().trim();
        }
        return null;
    }

    private List<Map<String, Object>> latestTelemetryByPlot() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Plot> plots = plotService.list();
        for (Plot plot : plots.stream().limit(MAX_CONTEXT_PLOTS).toList()) {
            TelemetryData telemetry = telemetryService.getLatestByPlotId(plot.getId());
            if (telemetry != null) {
                result.add(Map.of(
                        "plotId", plot.getId(),
                        "plotName", nullToEmpty(plot.getName()),
                        "telemetry", telemetryMap(telemetry)
                ));
            }
        }
        return result;
    }

    private Map<String, Object> plotMap(Plot plot) {
        if (plot == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", plot.getId());
        map.put("name", plot.getName());
        map.put("cropType", plot.getCropType());
        map.put("location", plot.getLocation());
        map.put("area", plot.getArea());
        map.put("status", plot.getStatus());
        map.put("description", plot.getDescription());
        return map;
    }

    private Map<String, Object> deviceMap(Device device) {
        if (device == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", device.getId());
        map.put("deviceCode", device.getDeviceCode());
        map.put("deviceName", device.getDeviceName());
        map.put("deviceType", device.getDeviceType());
        map.put("plotId", device.getPlotId());
        map.put("status", device.getStatus());
        map.put("lastHeartbeat", device.getLastHeartbeat());
        map.put("signalStrength", device.getSignalStrength());
        map.put("battery", device.getBattery());
        return map;
    }

    private Map<String, Object> telemetryMap(TelemetryData telemetry) {
        if (telemetry == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", telemetry.getId());
        map.put("plotId", telemetry.getPlotId());
        map.put("deviceId", telemetry.getDeviceId());
        map.put("deviceCode", telemetry.getDeviceCode());
        map.put("soilMoisture", telemetry.getSoilMoisture());
        map.put("airTemperature", telemetry.getAirTemperature());
        map.put("airHumidity", telemetry.getAirHumidity());
        map.put("illuminance", telemetry.getIlluminance());
        map.put("pumpStatus", telemetry.getPumpStatus());
        map.put("lightStatus", telemetry.getLightStatus());
        map.put("collectedAt", telemetry.getCollectedAt());
        return map;
    }

    private Map<String, Object> alarmMap(Alarm alarm) {
        if (alarm == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", alarm.getId());
        map.put("plotId", alarm.getPlotId());
        map.put("deviceId", alarm.getDeviceId());
        map.put("alarmType", alarm.getAlarmType());
        map.put("severity", alarm.getSeverity());
        map.put("triggerValue", alarm.getTriggerValue());
        map.put("thresholdValue", alarm.getThresholdValue());
        map.put("status", alarm.getStatus());
        map.put("message", alarm.getMessage());
        map.put("createTime", alarm.getCreateTime());
        return map;
    }

    private Map<String, Object> irrigationStrategyMap(IrrigationStrategy strategy) {
        if (strategy == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("plotId", strategy.getPlotId());
        map.put("moistureMin", strategy.getMoistureMin());
        map.put("moistureMax", strategy.getMoistureMax());
        map.put("consecutiveThreshold", strategy.getConsecutiveThreshold());
        map.put("autoMode", strategy.getAutoMode());
        map.put("maxDuration", strategy.getMaxDuration());
        map.put("cooldownMinutes", strategy.getCooldownMinutes());
        return map;
    }

    private Map<String, Object> lightStrategyMap(LightStrategy strategy) {
        if (strategy == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("plotId", strategy.getPlotId());
        map.put("illuminanceMin", strategy.getIlluminanceMin());
        map.put("illuminanceMax", strategy.getIlluminanceMax());
        map.put("autoMode", strategy.getAutoMode());
        map.put("startTime", strategy.getStartTime());
        map.put("endTime", strategy.getEndTime());
        map.put("cooldownMinutes", strategy.getCooldownMinutes());
        return map;
    }

    private Map<String, Object> route(String key, String title, String path) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("key", key);
        route.put("title", title);
        route.put("path", path);
        return route;
    }

    private String normalizeQuestion(AiChatDTO dto) {
        if (dto == null) {
            return "";
        }
        String question = firstNonBlank(
                dto.getMessage(),
                dto.getQuestion(),
                dto.getQuery(),
                dto.getInput(),
                dto.getPrompt(),
                dto.getText(),
                dto.getContent()
        );
        return question == null ? "" : question;
    }

    private String fallbackAnswer(String question, Map<String, Object> actionProposal) {
        String text = question.toLowerCase();

        if (actionProposal != null) {
            Object type = actionProposal.get("type");
            if ("NAVIGATE".equals(type)) {
                return "我已识别到页面跳转意图，已返回跳转动作，前端可以根据 actionProposal.route 执行跳转。";
            }
            if ("CONTROL_DEVICE".equals(type) || "CONTROL_LIGHT".equals(type)) {
                return "我已识别到硬件控制意图，已返回控制动作。为避免误操作，请前端先让用户确认，再调用 actionProposal 中的后端控制接口。";
            }
        }

        if (containsAny(text, "灌溉", "浇水", "水泵", "pump", "irrigation")) {
            return "可以先查看当前土壤湿度、灌溉策略和水泵在线状态。若土壤湿度低于策略下限，且设备在线、没有未处理故障，再建议用户确认后开启水泵。";
        }
        if (containsAny(text, "补光", "灯", "light", "照度", "光照")) {
            return "补光建议主要参考当前照度和补光策略。照度低于策略下限且处于允许时间段时，可以建议开启补光；照度恢复或超出时间段后应关闭。";
        }
        if (containsAny(text, "虫", "害虫", "识别", "pest", "病虫害")) {
            return "文字问答可以解释虫害处理思路；如果要识别图片，请上传图片到虫害识别入口，后端会调用图像模型给出候选害虫和治理建议。";
        }
        if (containsAny(text, "告警", "报警", "alarm")) {
            return "告警通常由传感器数据触发，例如土壤湿度、温度、空气湿度或光照超出正常范围。建议先确认告警类型、地块、设备和最新遥测数据。";
        }
        if (containsAny(text, "设备", "在线", "离线", "device", "offline", "online")) {
            return "设备状态通常由最近一次心跳或遥测上报时间判断。如果长时间没有上报，后端会把设备视为离线，前端可以提示用户检查网络和供电。";
        }

        return "我可以回答灌溉、补光、设备状态、告警和害虫识别相关问题。当前 AI 服务不可用时，我会先给出基础规则建议；AI 服务恢复后会返回更完整的智能回答。";
    }

    private List<String> defaultSuggestions() {
        return List.of(
                "当前地块需要灌溉吗？",
                "补光灯应该开到多少亮度？",
                "最近的告警应该怎么处理？"
        );
    }

    private String resolveConversationId(AiChatDTO dto) {
        if (dto != null && dto.getConversationId() != null && !dto.getConversationId().isBlank()) {
            return dto.getConversationId();
        }
        if (dto != null && dto.getUserId() != null && dto.getPlotId() != null) {
            return "user-" + dto.getUserId() + "-plot-" + dto.getPlotId();
        }
        if (dto != null && dto.getUserId() != null) {
            return "user-" + dto.getUserId();
        }
        if (dto != null && dto.getPlotId() != null) {
            return "plot-" + dto.getPlotId();
        }
        return "conversation-default";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (keyword != null && text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String contextText(AiChatDTO dto, String key) {
        if (dto == null || dto.getContext() == null) {
            return null;
        }
        return stringValue(dto.getContext().get(key));
    }
}
