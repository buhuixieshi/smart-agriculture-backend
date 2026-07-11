# AI 对话控制硬件联调说明

## 一、本次后端改动结论

后端已支持“前端与 AI Agent 对话后控制硬件”的安全闭环。

当前链路为：

```text
前端用户输入自然语言
  -> POST /api/ai/chat
  -> Java 后端补充地块、设备、遥测、策略、告警上下文
  -> Java 调用 Python AI Agent
  -> AI 返回语义动作 actionProposal
  -> Java 统一转换为固定后端动作格式
  -> 前端弹窗二次确认
  -> 前端调用 Java 固定控制接口
  -> Java ControlService 记录命令并下发到硬件网关或 MQTT
```

Python AI 不直接控制硬件，所有真实命令仍经过 Java 后端校验、记录、下发。

## 二、鉴权变化

`POST /api/ai/chat` 现在需要登录态。

请求头必须携带：

```http
Authorization: Bearer 登录后获得的JWT
```

原因：AI 聊天现在可能返回硬件控制动作，不能允许未登录用户调用。

虫害识别接口仍可继续按原方式联调：

```text
/api/ai/pest/**
/api/pest/**
```

## 三、前端调用 AI 聊天接口

请求：

```http
POST /api/ai/chat
Content-Type: application/json
Authorization: Bearer xxx
```

示例：

```json
{
  "conversationId": "web-user-1",
  "plotId": 1,
  "message": "帮我打开水泵",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  },
  "forceCommit": false
}
```

重点：

- `deviceCode` 必须由前端用户明确选择后传入；
- 后端不会在多个设备里替用户随便选一个；
- 如果没有 `deviceCode`，后端不会生成可执行硬件控制动作。

## 四、后端返回动作格式

水泵控制返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "answer": "已识别到开启水泵操作，请确认后执行。",
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
      "description": "用户要求开启水泵"
    }
  }
}
```

补光控制返回示例：

```json
{
  "type": "CONTROL_LIGHT",
  "api": "/api/light/control",
  "method": "POST",
  "requiresConfirmation": true,
  "payload": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "action": "ON",
    "brightness": 80
  },
  "description": "用户要求开启补光灯"
}
```

页面跳转返回示例：

```json
{
  "type": "NAVIGATE",
  "route": "/devices",
  "title": "设备管理",
  "requiresConfirmation": false
}
```

## 五、前端执行规则

前端不要直接信任模型返回的任意 `api`。

固定执行规则：

- `type=CONTROL_DEVICE`：调用 `POST /api/control/send`
- `type=CONTROL_LIGHT`：调用 `POST /api/light/control`
- `type=NAVIGATE`：只做前端页面跳转

执行硬件控制前必须弹窗确认。

确认弹窗建议展示：

```text
AI 建议执行以下硬件操作：

操作：开启水泵
设备：6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
原因：用户要求开启水泵

确认执行吗？
```

## 六、确认后调用接口

水泵：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer xxx
```

补光：

```http
POST /api/light/control
Content-Type: application/json
Authorization: Bearer xxx

{
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "action": "ON",
  "brightness": 80
}
```

## 七、命令状态查询

控制接口返回 `commandNo` 后，前端可以轮询：

```http
GET /api/control/commands/{commandNo}
Authorization: Bearer xxx
```

常见状态：

| 状态 | 含义 |
|---|---|
| `SENT` | 已发送到 MQTT 或硬件网关 |
| `SUCCESS` | 硬件或网关确认执行成功 |
| `FAILED` | 下发失败 |
| `TIMEOUT` | 等待硬件回执超时 |

## 八、本地联调前端已同步改动

当前仓库内的简易前端已做适配：

- AI 页面新增控制设备下拉框；
- `/api/ai/chat` 请求会传 `context.deviceCode`；
- 控制动作会弹窗确认；
- 前端固定调用 `/api/control/send` 和 `/api/light/control`；
- 控制后会自动查询命令状态。

前端正式项目按同样规则实现即可。
