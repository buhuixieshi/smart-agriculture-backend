# AI 上下文与动作能力联调说明

## 一、当前结论

后端已经补充 AI 智能农事问答的业务上下文能力和动作建议能力。

现在前端调用：

```http
POST /api/ai/chat
Content-Type: application/json
```

后端会自动把数据库中的智慧农业业务数据一起传给 AI 服务，包括：

- 地块列表；
- 当前地块信息；
- 地块绑定设备；
- 最新遥测数据；
- 未处理告警；
- 灌溉策略；
- 补光策略；
- 前端可跳转页面；
- 可执行动作协议。

因此前端不需要自己拼这些业务上下文，也不需要直接访问 `smart-agriculture-ai` 服务。

## 二、推荐请求格式

```json
{
  "conversationId": "conversation-test",
  "userId": 0,
  "plotId": 1,
  "message": "当前地块需要灌溉吗？",
  "context": {},
  "forceCommit": false
}
```

说明：

- `message`：用户输入内容，推荐固定使用这个字段。
- `plotId`：当前页面选中的地块 ID，建议前端尽量传。
- `context`：前端额外上下文，可选。
- 后端会自动补充 `backendContext`，前端不用手动传遥测、告警、设备列表。

## 三、AI 现在能知道哪些数据

后端会自动注入：

```json
{
  "backendContext": {
    "projectName": "智慧农业系统",
    "plots": [],
    "currentPlot": {},
    "devices": [],
    "latestTelemetry": {},
    "activeAlarms": [],
    "irrigationStrategy": {},
    "lightStrategy": {},
    "frontendRoutes": [],
    "actionProtocol": {}
  }
}
```

如果没有传 `plotId`，后端会尽量提供各地块的最新遥测摘要。

## 四、页面跳转动作

用户输入类似：

```text
跳转到设备管理页面
打开告警页面
去智能补光页面
```

后端返回中可能包含：

```json
{
  "actionProposal": {
    "type": "NAVIGATE",
    "route": "/devices",
    "title": "设备管理",
    "requiresConfirmation": false
  }
}
```

前端处理方式：

- `type=NAVIGATE` 时，直接使用 `route` 跳转；
- 不需要再调后端执行跳转；
- 后端不能直接操作浏览器页面，跳转由前端执行。

## 五、硬件控制动作

用户输入类似：

```text
打开水泵
关闭水泵
打开补光灯
关闭补光灯
```

后端返回中可能包含：

```json
{
  "actionProposal": {
    "type": "CONTROL_DEVICE",
    "api": "/api/control/send",
    "method": "POST",
    "requiresConfirmation": true,
    "payload": {
      "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
      "commandType": "PUMP_ON",
      "commandValue": "ON"
    },
    "description": "检测到开水泵意图，前端确认后调用控制接口。"
  }
}
```

补光动作示例：

```json
{
  "actionProposal": {
    "type": "CONTROL_LIGHT",
    "api": "/api/light/control",
    "method": "POST",
    "requiresConfirmation": true,
    "payload": {
      "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
      "action": "ON"
    }
  }
}
```

前端处理方式：

- `requiresConfirmation=true` 时，前端必须弹出确认框；
- 用户确认后，再调用 `actionProposal.api`；
- 水泵走 `/api/control/send`；
- 补光走 `/api/light/control`；
- 不建议前端在用户未确认时自动执行硬件控制。

## 六、普通回答响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conversation-test",
    "plotId": 1,
    "question": "当前地块需要灌溉吗？",
    "answer": "根据当前土壤湿度和灌溉策略...",
    "status": "success",
    "source": "DEEPSEEK",
    "modelStatus": "SUCCESS",
    "suggestions": [],
    "actionProposal": null,
    "memoryCommitted": false,
    "memoryRecallCount": 0,
    "answeredAt": "2026-07-11T..."
  }
}
```

## 七、前端验收建议

### 1. 测试业务数据问答

```json
{
  "plotId": 1,
  "message": "当前地块的土壤湿度是多少，需要灌溉吗？"
}
```

预期：

- AI 回答中应结合当前地块最新遥测；
- 不应该只回答泛泛的农业常识。

### 2. 测试页面跳转

```json
{
  "message": "跳转到设备管理页面"
}
```

预期：

- 返回 `actionProposal.type=NAVIGATE`；
- 返回 `route=/devices`。

### 3. 测试硬件控制意图

```json
{
  "plotId": 1,
  "message": "打开水泵"
}
```

预期：

- 返回 `actionProposal.type=CONTROL_DEVICE`；
- `requiresConfirmation=true`；
- 前端确认后调用 `/api/control/send`。

## 八、注意事项

- AI 不会直接操作浏览器页面，页面跳转必须由前端根据 `actionProposal` 执行。
- AI 不会绕过现有控制接口直接控制硬件，硬件控制仍走后端已有接口。
- 后端会尽量根据 `plotId` 自动选择对应设备；如果设备编号为空，前端应让用户选择具体设备。
- 如果 AI 服务不可用，后端仍会返回 `status=fallback` 和基础规则回答。
