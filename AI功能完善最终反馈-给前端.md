# AI 功能完善最终反馈

## 当前结论

后端 AI 对话功能已经完成真实联调。

当前链路已经打通：

```text
前端 / Postman
→ Java 后端 POST /api/ai/chat
→ AI 服务 POST http://127.0.0.1:5002/api/ai/chat
→ Java 后端统一包装返回
→ 前端展示
```

前端不需要直接访问 AI 服务，只需要继续调用 Java 后端接口。

## 一、智能农事问答接口

### 接口地址

```http
POST /api/ai/chat
Content-Type: application/json
```

### 推荐请求

```json
{
  "conversationId": "conversation-test",
  "userId": 0,
  "plotId": 1,
  "message": "你好",
  "context": {},
  "forceCommit": false
}
```

说明：

- `message`：用户输入内容，推荐前端统一使用这个字段。
- `conversationId`：会话 ID，用于连续对话。
- `userId`：用户 ID，没有登录态时可传 `0`。
- `plotId`：地块 ID，可选。
- `context`：实时业务上下文，可选但建议传。
- `forceCommit`：是否提交会话记忆，默认 `false`。

## 二、灵活输入兼容

为了兼容不同前端写法，后端目前支持以下字段作为用户问题：

```text
message
question
query
input
prompt
text
content
messages
```

优先级：

```text
message > question > query > input > prompt > text > content > messages
```

前端推荐只使用：

```json
{
  "message": "用户输入内容"
}
```

如果使用 OpenAI 风格 `messages`，也可以：

```json
{
  "messages": [
    {
      "role": "user",
      "content": "当前地块需要灌溉吗？"
    }
  ]
}
```

后端会统一转换为 AI 服务接受的 `message` 字段。

## 三、实时业务上下文

建议前端或后端在提问时带上最新业务上下文：

```json
{
  "message": "当前地块需要灌溉吗？",
  "plotId": 1,
  "context": {
    "telemetry": {
      "soilMoisture": 22.5,
      "airTemperature": 31.2,
      "airHumidity": 58.6,
      "illuminance": 280
    },
    "irrigationStrategy": {
      "moistureMin": 30,
      "moistureMax": 65
    },
    "device": {
      "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
      "status": "ONLINE",
      "pumpStatus": "OFF",
      "lightStatus": "OFF"
    },
    "activeAlarms": []
  }
}
```

AI 服务会保留农业知识库和会话记忆，但实时遥测、设备状态、告警和策略仍以 Java 后端当前数据为准。

## 四、成功返回

真实 AI 调用成功时，返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conversation-test",
    "plotId": 1,
    "question": "你好",
    "answer": "您好！我是您的农业智能助手。目前系统未提供实时业务数据，因此无法分析具体的环境或设备状态。请提供您需要了解的信息，例如灌溉、补光、温湿度或设备状态等，我将尽力为您解答。",
    "status": "success",
    "suggestions": [
      "您可以询问当前土壤湿度或天气情况",
      "需要了解灌溉策略或设备状态吗？"
    ],
    "memoryCommitted": false,
    "memoryRecallCount": 0,
    "answeredAt": "2026-07-11T10:30:10"
  }
}
```

前端主要展示：

```text
data.answer
```

判断真实 AI 成功：

```text
data.status === "success"
```

## 五、操作建议字段

如果 AI 判断需要建议用户执行设备操作，可能返回：

```json
{
  "actionProposal": {
    "action": "PUMP_ON",
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "requiresConfirmation": true,
    "reason": "土壤湿度低于策略下限",
    "parameters": {}
  }
}
```

前端注意：

- `actionProposal` 只是 AI 建议，不代表已经控制设备。
- 需要用户确认后，再调用现有控制接口。
- 不要直接把 AI 建议自动下发到硬件。

## 六、AI 降级返回

如果 AI 服务不可用，后端会返回兜底答案：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conversation-test",
    "question": "当前地块需要灌溉吗？",
    "answer": "可以先查看当前土壤湿度、灌溉策略和水泵在线状态。若土壤湿度低于策略下限，且设备在线、没有未处理故障，再建议用户确认后开启水泵。",
    "status": "fallback",
    "source": "BACKEND_RULE_FALLBACK",
    "modelStatus": "MODEL_UNAVAILABLE",
    "errorMessage": "AI 文本模型服务暂时不可用，已返回后端兜底回答。请检查 ai.chat.model.url、AI 服务进程和 AI_SERVICE_TOKEN。",
    "suggestions": [
      "当前地块需要灌溉吗？",
      "补光灯应该开到多少亮度？",
      "最近的告警应该怎么处理？"
    ],
    "answeredAt": "2026-07-11T10:30:10"
  }
}
```

前端判断降级：

```text
data.status === "fallback"
```

建议只做轻提示，例如：

```text
AI 服务暂不可用，当前为基础规则回答
```

不要把 `source`、`modelStatus`、`errorMessage` 直接展示在普通聊天气泡里。

## 七、后端实现状态

后端已完成以下优化：

- `/api/ai/chat` 输入字段灵活兼容。
- Java 后端转发 AI 服务时统一整理为合法 JSON。
- Java 调 AI 服务已改为 Java 17 原生 `HttpClient`，解决原 HTTP 客户端与 FastAPI/Uvicorn 的请求兼容问题。
- AI 成功时返回 `status=success`。
- AI 不可用时返回 `status=fallback`。
- 正常中文兜底文案已修复。
- 已映射 `suggestions`、`actionProposal`、`memoryCommitted`、`memoryRecallCount`。

## 八、AI 服务地址

Java 后端内部调用：

```text
http://127.0.0.1:5002/api/ai/chat
```

前端不要直接调用该地址。

前端只调用：

```text
http://localhost:8080/api/ai/chat
```

## 九、验收结论

当前智能农事问答已完成真实 AI 联调。

前端验收标准：

```text
data.status === "success"
```

页面展示标准：

```text
展示 data.answer
```

AI 降级判断：

```text
data.status === "fallback"
```
