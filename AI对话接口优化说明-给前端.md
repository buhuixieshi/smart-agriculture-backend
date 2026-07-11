# AI 对话接口优化说明

## 本次调整

后端已优化 `/api/ai/chat`：

- 输入字段不再固定，只要能表达用户问题即可。
- 返回结构更接近正常聊天结果。
- 正常调用 AI 成功时，不再返回一堆调试字段。
- 只有 AI 服务不可用并进入后端兜底时，才返回 `source`、`modelStatus`、`errorMessage` 这些排查字段。

## 接口地址

```http
POST /api/ai/chat
Content-Type: application/json
```

## 支持的请求格式

前端可以继续使用原来的：

```json
{
  "message": "当前地块需要灌溉吗？"
}
```

也可以使用这些字段之一：

```json
{
  "question": "当前地块需要灌溉吗？"
}
```

```json
{
  "query": "补光灯要不要打开？"
}
```

```json
{
  "input": "土壤湿度低怎么办？"
}
```

```json
{
  "prompt": "帮我分析一下地块状态"
}
```

```json
{
  "text": "设备离线怎么办？"
}
```

```json
{
  "content": "最近的告警怎么处理？"
}
```

也支持类似 OpenAI 的 `messages` 数组：

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

如果同时传多个字段，后端优先级为：

```text
message > question > query > input > prompt > text > content > messages
```

## 可选业务字段

前端仍然可以传这些业务字段，后端会转发给 AI 服务：

```json
{
  "conversationId": "user-1-plot-1",
  "userId": 1,
  "plotId": 1,
  "message": "当前地块需要灌溉吗？",
  "context": {
    "telemetry": {
      "soilMoisture": 22.5,
      "airTemperature": 31.2,
      "airHumidity": 58.6,
      "illuminance": 280
    }
  },
  "forceCommit": false
}
```

说明：

- `conversationId`：用于连续对话。
- `userId`：用户 ID，可选。
- `plotId`：地块 ID，可选。
- `context`：前端或后端整理的上下文数据，可选。
- `forceCommit`：是否提交会话记忆，可选。

## 正常成功返回

AI 服务正常可用时，返回会更简洁：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "user-1-plot-1",
    "plotId": 1,
    "question": "当前地块需要灌溉吗？",
    "answer": "AI 返回的自然语言回答",
    "status": "success",
    "suggestions": [
      "可以查看土壤湿度趋势",
      "确认水泵是否在线"
    ],
    "answeredAt": "2026-07-11T10:30:00"
  }
}
```

如果 AI 没有返回 `suggestions`，该字段会被省略。

前端判断真实 AI 成功的核心字段：

```text
data.status = success
```

## AI 不可用时的兜底返回

如果 AI 服务未启动、地址不可达或 token 不一致，后端会返回规则兜底回答：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "plotId": 1,
    "question": "当前地块需要灌溉吗？",
    "answer": "可以先看当前土壤湿度、灌溉策略和水泵在线状态...",
    "status": "fallback",
    "source": "BACKEND_RULE_FALLBACK",
    "modelStatus": "MODEL_UNAVAILABLE",
    "errorMessage": "AI文本模型服务暂时不可用，已返回后端兜底回答。请检查 ai.chat.model.url、AI服务进程和 AI_SERVICE_TOKEN。",
    "suggestions": [
      "当前地块需要灌溉吗？",
      "补光灯应该开到多少亮度？",
      "最近的告警应该怎么处理？"
    ],
    "answeredAt": "2026-07-11T10:30:00"
  }
}
```

前端可以根据：

```text
data.status = fallback
```

显示“AI 服务暂不可用，当前为基础规则回答”之类的轻提示。

## 前端建议

前端聊天框只需要把用户输入传给任一字段即可，推荐统一使用：

```json
{
  "message": "用户输入内容",
  "plotId": 1,
  "conversationId": "当前会话ID"
}
```

页面展示时建议主要使用：

```text
data.answer
```

`status/source/modelStatus/errorMessage` 只用于调试或服务降级提示，不建议在正常聊天气泡里直接展示。

## 后端验证

后端已通过编译：

```bash
mvn -q -DskipTests compile
```
