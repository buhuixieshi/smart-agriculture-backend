# AI 所有功能以及联调说明

## 一、当前 AI 功能总览

当前后端 AI 相关功能分为三块：

| 功能 | 前端入口 | 后端接口 | 是否需要登录 | 当前状态 |
|---|---|---|---|---|
| 智能农事问答 | AI 助手/聊天框 | `POST /api/ai/chat` | 需要 JWT | 已接入 Python AI 服务，失败时后端规则兜底 |
| AI 对话控制硬件 | AI 助手/聊天框 | `POST /api/ai/chat` + 控制接口 | 需要 JWT | 已支持返回动作，前端确认后下发真实控制 |
| 害虫图像识别 | 害虫识别页面 | `POST /api/ai/pest/detect` | 暂不需要 | 已接入图像模型，失败时本地知识库兜底 |

AI 服务地址由后端配置决定：

```yaml
ai:
  chat:
    model:
      enabled: true
      url: http://127.0.0.1:5002/api/ai/chat
      token: ${AI_SERVICE_TOKEN:}

pest:
  model:
    enabled: true
    url: http://127.0.0.1:5002/api/ai/pest/analyze
    token: ${AI_SERVICE_TOKEN:}
```

前端只调用 Java 后端，不直接调用 Python AI 服务。

## 二、智能农事问答接口

### 1. 接口

```http
POST /api/ai/chat
Content-Type: application/json
Authorization: Bearer xxx
```

该接口现在必须登录，因为它可能返回硬件控制动作。

### 2. 请求体

推荐请求：

```json
{
  "conversationId": "web-user-1",
  "plotId": 1,
  "message": "当前地块需要灌溉吗？",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  },
  "forceCommit": false
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| `conversationId` | 否 | 会话 ID，建议按用户生成，如 `web-user-1` |
| `plotId` | 否 | 当前选中的地块 ID |
| `message` | 是 | 用户问题 |
| `context.deviceCode` | 控制硬件时建议必传 | 当前用户明确选择的控制设备 |
| `forceCommit` | 否 | 是否强制提交长期记忆，默认 `false` |

兼容字段：

后端也能识别 `question`、`query`、`input`、`prompt`、`text`、`content`，以及 `messages` 数组中的最后一条用户消息。

### 3. 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "web-user-1",
    "plotId": 1,
    "question": "当前地块需要灌溉吗？",
    "answer": "当前土壤湿度为 37.1%，暂不建议立即灌溉。",
    "status": "success",
    "source": "DEEPSEEK",
    "modelStatus": "SUCCESS",
    "suggestions": [
      "查看最近一小时湿度变化",
      "是否开启自动灌溉策略？"
    ],
    "memoryCommitted": false,
    "memoryRecallCount": 2,
    "answeredAt": "2026-07-11T10:30:10"
  }
}
```

如果 Python AI 服务不可用，后端会返回兜底：

```json
{
  "status": "fallback",
  "source": "BACKEND_RULE_FALLBACK",
  "modelStatus": "MODEL_UNAVAILABLE",
  "errorMessage": "AI 文本模型服务暂时不可用，已返回后端兜底回答。"
}
```

前端判断：

- `modelStatus=SUCCESS`：真实 AI 服务可用；
- `source=BACKEND_RULE_FALLBACK` 或 `modelStatus=MODEL_UNAVAILABLE`：后端兜底，不是真实大模型回答；
- 有 `actionProposal` 时，说明 AI 识别到了页面跳转或硬件控制动作。

## 三、AI 对话控制硬件

### 1. 设计原则

Python AI 不直接控制硬件。

真实控制链路为：

```text
用户自然语言
  -> POST /api/ai/chat
  -> Java 补充实时业务上下文
  -> Python AI 返回语义动作
  -> Java 转成固定 actionProposal
  -> 前端弹窗确认
  -> 前端调用 Java 控制接口
  -> Java 记录 control_command 并下发到硬件网关或 MQTT
```

### 2. 请求示例

```json
{
  "conversationId": "web-user-1",
  "plotId": 1,
  "message": "帮我打开水泵",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  }
}
```

重点：

- `deviceCode` 必须由前端用户明确选择后传入；
- 后端不会替用户从多个设备里随机选一个；
- 没有 `deviceCode` 时，不返回可执行控制动作。

### 3. 水泵动作返回

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
    "description": "用户要求开启水泵"
  }
}
```

关闭水泵时：

```json
{
  "commandType": "PUMP_OFF",
  "commandValue": "OFF"
}
```

### 4. 补光动作返回

```json
{
  "actionProposal": {
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
}
```

关闭补光时：

```json
{
  "action": "OFF",
  "brightness": 0
}
```

### 5. 页面跳转动作返回

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

### 6. 前端执行规则

前端不要直接信任 AI 返回的任意 `api`。

固定执行规则：

| `actionProposal.type` | 前端动作 |
|---|---|
| `CONTROL_DEVICE` | 固定调用 `POST /api/control/send` |
| `CONTROL_LIGHT` | 固定调用 `POST /api/light/control` |
| `NAVIGATE` | 前端页面跳转 |

硬件动作必须弹窗确认：

```text
AI 建议执行以下硬件操作：

操作：开启水泵
设备：6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
原因：用户要求开启水泵

确认执行吗？
```

### 7. 用户确认后调用控制接口

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

### 8. 命令状态查询

控制接口返回 `commandNo` 后轮询：

```http
GET /api/control/commands/{commandNo}
Authorization: Bearer xxx
```

状态说明：

| 状态 | 含义 |
|---|---|
| `SENT` | 已发送到 MQTT 或硬件网关 |
| `SUCCESS` | 硬件或网关确认执行成功 |
| `FAILED` | 下发失败 |
| `TIMEOUT` | 等待硬件回执超时 |

## 四、害虫图像识别

### 1. 推荐接口

```http
POST /api/ai/pest/detect
Content-Type: multipart/form-data
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 图片文件 |
| `plotId` | 否 | 关联地块 ID |

旧接口仍保留兼容：

```text
POST /api/pest/detect
```

两个接口底层逻辑一致，建议前端统一使用 `/api/ai/pest/**`。

### 2. 请求示例

Postman：

```text
method: POST
url: http://localhost:8080/api/ai/pest/detect?plotId=1
body: form-data
key: file
type: File
value: 选择图片
```

前端 fetch 示例：

```js
const form = new FormData();
form.append("file", file);

const res = await fetch("/api/ai/pest/detect?plotId=1", {
  method: "POST",
  body: form
});
```

注意：上传图片时不要手动设置 `Content-Type: application/json`。

### 3. 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "plotId": 1,
    "fileName": "pest.jpg",
    "pestId": "aphid",
    "pestName": "蚜虫",
    "dangerLevel": "MEDIUM",
    "confidence": 0.91,
    "modelStatus": "MODEL_SERVICE",
    "detectTime": "2026-07-11T10:30:10",
    "suggestion": {
      "pestId": "aphid",
      "pestName": "蚜虫",
      "dangerLevel": "MEDIUM",
      "description": "常聚集在嫩叶和新梢吸食汁液，容易诱发叶片卷曲和病毒病传播。",
      "physicalControl": [
        "剪除虫量集中的嫩梢",
        "悬挂黄色粘虫板诱捕有翅蚜"
      ],
      "biologicalControl": [
        "保护瓢虫、草蛉等天敌",
        "释放蚜茧蜂进行生物控制"
      ],
      "chemicalControl": [
        "虫口密度较高时选用吡虫啉或啶虫脒，按说明低浓度喷施"
      ],
      "prevention": [
        "避免氮肥过量",
        "加强通风，降低植株郁闭度"
      ]
    }
  }
}
```

### 4. `modelStatus` 判断

| `modelStatus` | 含义 |
|---|---|
| `MODEL_SERVICE` | 已调用真实图像模型 |
| `MOCK_FALLBACK` | 图像模型不可用，后端使用本地 fallback 知识库生成结果 |

如果前端验收要求“真实模型通过”，应以：

```json
"modelStatus": "MODEL_SERVICE"
```

为准。

### 5. Java 后端调用 Python 图像模型的格式

前端上传到 Java 时字段名是：

```text
file
```

Java 调 Python AI 服务时会转换成：

```text
POST http://127.0.0.1:5002/api/ai/pest/analyze
Content-Type: multipart/form-data

image: 图片文件
plotId: 地块ID，可选
conversationId: pest-plot-{plotId}
context: {}
```

如果配置了 `AI_SERVICE_TOKEN`，Java 会带：

```http
X-AI-Service-Token: xxx
```

### 6. Java 能解析的模型响应字段

模型响应可以是根字段，也可以放在 `observation` 和 `advice` 中。

支持字段：

```json
{
  "observation": {
    "pestId": "aphid",
    "pestName": "蚜虫",
    "dangerLevel": "MEDIUM",
    "confidence": 0.91,
    "safetyNote": "..."
  },
  "advice": {
    "answer": "防治建议...",
    "source": "MODEL_SERVICE",
    "modelStatus": "SUCCESS"
  }
}
```

也兼容：

```json
{
  "pest_id": "aphid",
  "pest_name": "蚜虫",
  "danger_level": "MEDIUM",
  "confidence": 0.91,
  "answer": "防治建议..."
}
```

### 7. 害虫知识与历史记录接口

查询某个害虫建议：

```http
GET /api/ai/pest/suggestions/{pestId}
```

查询全部害虫建议：

```http
GET /api/ai/pest/suggestions
```

查询识别记录：

```http
GET /api/ai/pest/records?plotId=1&pestId=aphid&startDate=2026-07-01&endDate=2026-07-11
```

查询趋势：

```http
GET /api/ai/pest/trend?plotId=1&startDate=2026-07-01&endDate=2026-07-11
```

响应：

```json
[
  {
    "date": "2026-07-11",
    "totalCount": 2,
    "pestCounts": {
      "aphid": 2
    }
  }
]
```

查询分布：

```http
GET /api/ai/pest/distribution?plotId=1&startDate=2026-07-01&endDate=2026-07-11
```

响应：

```json
[
  {
    "pestId": "aphid",
    "pestName": "蚜虫",
    "count": 2,
    "percent": 100.00
  }
]
```

## 五、前端页面建议

### 1. AI 助手页

建议包含：

- 地块选择框；
- 控制设备选择框；
- 用户输入框；
- AI 回答展示区；
- `actionProposal` 硬件确认弹窗；
- 命令状态展示。

### 2. 害虫识别页

建议包含：

- 地块选择框；
- 图片上传；
- 识别结果卡片；
- `modelStatus` 标签；
- 防治建议分组展示；
- 历史识别记录；
- 趋势/分布图表。

## 六、联调测试顺序

### 1. 登录

```http
POST /api/auth/login
```

拿到 JWT 后，用于 `/api/ai/chat`、控制接口和命令状态查询。

### 2. 测试文本 AI

```http
POST /api/ai/chat
Authorization: Bearer xxx

{
  "message": "你好",
  "context": {}
}
```

预期：

- `code=200`
- 如果 Python AI 正常：`modelStatus=SUCCESS`
- 如果 Python AI 不可用：`source=BACKEND_RULE_FALLBACK`

### 3. 测试 AI 控制水泵

```json
{
  "plotId": 1,
  "message": "打开水泵",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  }
}
```

预期：

- 返回 `actionProposal.type=CONTROL_DEVICE`
- 前端弹窗确认
- 确认后调用 `/api/control/send`
- 再轮询 `/api/control/commands/{commandNo}`

### 4. 测试 AI 控制补光

```json
{
  "plotId": 1,
  "message": "把补光灯调到80",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  }
}
```

预期：

- 返回 `actionProposal.type=CONTROL_LIGHT`
- `payload.brightness` 为 80 或模型理解后的亮度；
- 前端确认后调用 `/api/light/control`。

### 5. 测试害虫识别

```http
POST /api/ai/pest/detect?plotId=1
Content-Type: multipart/form-data

file: 图片
```

预期：

- `code=200`
- 返回 `pestId/pestName/dangerLevel/confidence/suggestion`
- `modelStatus=MODEL_SERVICE` 表示真实模型通过
- `modelStatus=MOCK_FALLBACK` 表示后端兜底，不算真实模型验收通过

### 6. 测试害虫历史

```http
GET /api/ai/pest/records?plotId=1
GET /api/ai/pest/trend?plotId=1
GET /api/ai/pest/distribution?plotId=1
```

预期：能看到刚才上传识别后的记录、趋势和分布。

## 七、常见问题判断

### 1. `/api/ai/chat` 返回 401

说明没带 JWT，或 token 过期。

处理：重新登录，带上：

```http
Authorization: Bearer xxx
```

### 2. AI 回答是后端规则兜底

表现：

```json
"source": "BACKEND_RULE_FALLBACK",
"modelStatus": "MODEL_UNAVAILABLE"
```

说明请求到了 Java，但 Python 文本 AI 服务不可用或调用失败。

### 3. 害虫识别返回 `MOCK_FALLBACK`

说明 Java 没有成功调用图像模型，使用了本地兜底知识库。

需要检查：

- Python AI 服务是否启动；
- `pest.model.url` 是否正确；
- `AI_SERVICE_TOKEN` 是否一致；
- Python 端 `/api/ai/pest/analyze` 是否支持 `image` 字段；
- 图片格式是否正常。

### 4. AI 返回了控制动作但硬件没动

分两步看：

1. 前端是否弹窗确认并调用了控制接口；
2. `/api/control/commands/{commandNo}` 的状态是什么。

如果是：

- `SUCCESS`：后端认为硬件或网关执行成功；
- `FAILED`：看 `errorMessage`；
- `TIMEOUT`：命令发出但未收到硬件回执。

## 八、当前代码检查结论

已检查：

- `AiChatController`
- `AiChatServiceImpl`
- `HttpAiChatModelClient`
- `AiPestController`
- `PestController`
- `PestServiceImpl`
- `HttpPestModelClient`
- `PestDetectionRecordServiceImpl`
- `SecurityConfig`
- `application.yml`
- `schema.sql`
- `data.sql`
- 本地简易前端 `frontend/app.js`

当前结论：

- 智能问答链路已接入 Python AI，失败时有后端兜底；
- AI 对话控制硬件已支持动作返回、前端确认、固定控制接口下发；
- 害虫识别已支持模型调用、fallback 知识库、记录保存、趋势和分布查询；
- `/api/ai/chat` 已要求 JWT；
- `/api/ai/pest/**` 和 `/api/pest/**` 当前放行，方便图片识别联调。
