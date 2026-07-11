# AI 功能前端联调说明

## 1. 总体说明

前端只需要调用 Java 后端接口，不直接调用 Python AI 服务。

当前 AI 功能包括：

| 功能 | 推荐接口 | 是否需要登录 | 说明 |
|---|---|---|---|
| 智能农事问答 | `POST /api/ai/chat` | 需要 JWT | 文本问答，后端会补充地块、设备、遥测、告警、策略上下文 |
| AI 页面跳转 | `POST /api/ai/chat` | 需要 JWT | AI 返回 `actionProposal.type=NAVIGATE`，由前端跳转 |
| AI 控制硬件 | `POST /api/ai/chat` + 控制接口 | 需要 JWT | AI 只返回动作建议，前端确认后再调用控制接口 |
| AI 停止任务 | `POST /api/ai/chat` + 控制接口 | 需要 JWT | 用户说“停止/取消任务”时返回关闭水泵或关闭补光动作 |
| 害虫/病虫图像识别 | `POST /api/ai/pest/detect` | 暂不需要 | 上传图片识别，失败时使用后端本地知识库兜底 |
| 害虫建议/历史/统计 | `/api/ai/pest/**` | 暂不需要 | 查询防治建议、识别记录、趋势、分布 |

后端配置的 Python AI 服务地址：

```yaml
ai:
  chat:
    model:
      url: http://127.0.0.1:5002/api/ai/chat

pest:
  model:
    url: http://127.0.0.1:5002/api/ai/pest/analyze
```

如果 Python AI 不可用，Java 后端会返回兜底结果，不会让前端接口直接崩掉。

## 2. 鉴权要求

### 2.1 需要登录的接口

```http
POST /api/ai/chat
Authorization: Bearer xxx
```

原因：`/api/ai/chat` 可能返回硬件控制动作，必须使用登录用户身份。

### 2.2 暂不需要登录的接口

```text
/api/ai/pest/**
/api/pest/**
```

这两个害虫识别入口当前放行，方便前端联调。

## 3. 智能农事问答

### 3.1 请求

```http
POST /api/ai/chat
Content-Type: application/json
Authorization: Bearer xxx
```

推荐请求体：

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
| `conversationId` | 否 | 会话 ID，建议前端按用户生成，例如 `web-user-1` |
| `plotId` | 否 | 当前地块 ID；传了以后 AI 会拿到该地块遥测、策略和告警上下文 |
| `message` | 是 | 用户输入的问题 |
| `context.deviceCode` | 控制硬件时必传 | 当前用户明确选择的设备编号 |
| `forceCommit` | 否 | 是否强制提交长期记忆，默认 `false` |

兼容字段：

后端也能识别 `question`、`query`、`input`、`prompt`、`text`、`content`，以及 `messages` 数组中的最后一条用户消息。

### 3.2 成功响应

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

### 3.3 兜底响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "status": "fallback",
    "source": "BACKEND_RULE_FALLBACK",
    "modelStatus": "MODEL_UNAVAILABLE",
    "errorMessage": "AI 文本模型服务暂时不可用，已返回后端兜底回答。"
  }
}
```

前端判断：

- `modelStatus=SUCCESS`：真实 AI 服务可用；
- `source=BACKEND_RULE_FALLBACK` 或 `modelStatus=MODEL_UNAVAILABLE`：Java 后端规则兜底；
- `actionProposal` 不为空：AI 识别到了页面跳转或硬件控制动作。

## 4. AI 页面跳转

用户输入示例：

```text
跳转到设备管理页面
打开害虫识别页面
去告警管理
```

响应中的动作：

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

前端处理：

| route | 建议页面 |
|---|---|
| `/` | 首页/数据看板 |
| `/plots` | 地块管理 |
| `/devices` | 设备管理 |
| `/telemetry` | 遥测数据 |
| `/alarms` | 告警管理 |
| `/irrigation` | 灌溉管理 |
| `/light` | 智能补光 |
| `/pest` | 害虫识别 |
| `/ai` | 智能农事问答 |

## 5. AI 控制硬件

### 5.1 设计原则

AI 不直接控制硬件。

真实链路：

```text
用户自然语言
  -> POST /api/ai/chat
  -> Java 补充实时上下文
  -> Python AI 返回语义动作
  -> Java 归一化为 actionProposal
  -> 前端弹窗确认
  -> 前端调用 Java 固定控制接口
  -> Java 记录 control_command 并下发到硬件网关或 MQTT
```

### 5.2 前端必须传 deviceCode

例如：

```json
{
  "plotId": 1,
  "message": "帮我打开水泵",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  }
}
```

注意：

- `deviceCode` 必须由用户明确选择；
- 后端不会在多个设备里随机选一个；
- 没有 `deviceCode` 时，不返回可执行控制动作。

### 5.3 水泵控制动作

打开水泵：

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

关闭水泵：

```json
{
  "payload": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "commandType": "PUMP_OFF",
    "commandValue": "OFF"
  }
}
```

前端确认后调用：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer xxx
```

### 5.4 补光控制动作

打开补光：

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

关闭补光：

```json
{
  "payload": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "action": "OFF",
    "brightness": 0
  }
}
```

前端确认后调用：

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

### 5.5 前端执行规则

前端不要信任 AI 返回的任意 `api` 字段。

固定执行：

| `actionProposal.type` | 固定执行 |
|---|---|
| `CONTROL_DEVICE` | `POST /api/control/send` |
| `CONTROL_LIGHT` | `POST /api/light/control` |
| `NAVIGATE` | 前端路由跳转 |

硬件动作必须弹窗确认：

```text
AI 建议执行以下硬件操作：

操作：开启水泵
设备：6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
原因：用户要求开启水泵

确认执行吗？
```

## 6. AI 停止正在执行的任务

### 6.1 支持的用户表达

```text
停止当前任务
取消正在执行的任务
结束当前任务
停止灌溉
停止水泵
关闭水泵
停止补光
关灯
关闭补光灯
```

### 6.2 后端处理规则

后端会把停止类意图转换成关闭类动作：

| 用户意图 | 返回动作 |
|---|---|
| 停止水泵/停止灌溉 | `CONTROL_DEVICE` + `PUMP_OFF` |
| 停止补光/关灯 | `CONTROL_LIGHT` + `OFF` |
| 只说“停止当前任务” | 优先看当前地块最新遥测；水泵 ON 则关水泵，补光 ON 则关补光；无法判断时默认关水泵 |

### 6.3 停止水泵响应示例

```json
{
  "actionProposal": {
    "type": "CONTROL_DEVICE",
    "api": "/api/control/send",
    "method": "POST",
    "requiresConfirmation": true,
    "payload": {
      "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
      "commandType": "PUMP_OFF",
      "commandValue": "OFF"
    },
    "description": "检测到停止当前任务意图，前端确认后关闭水泵。"
  }
}
```

### 6.4 停止补光响应示例

```json
{
  "actionProposal": {
    "type": "CONTROL_LIGHT",
    "api": "/api/light/control",
    "method": "POST",
    "requiresConfirmation": true,
    "payload": {
      "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
      "action": "OFF",
      "brightness": 0
    },
    "description": "检测到停止补光任务意图，前端确认后关闭补光灯。"
  }
}
```

### 6.5 命令状态查询

控制接口返回 `commandNo` 后，前端可以轮询：

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

## 7. 害虫/病虫图像识别

### 7.1 推荐接口

```http
POST /api/ai/pest/detect
Content-Type: multipart/form-data
```

参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 图片文件 |
| `plotId` | 否 | 关联地块 ID |

旧接口保留兼容：

```text
POST /api/pest/detect
```

建议前端统一使用 `/api/ai/pest/**`。

### 7.2 请求示例

Postman：

```text
method: POST
url: http://localhost:8080/api/ai/pest/detect?plotId=1
body: form-data
key: file
type: File
value: 选择图片
```

前端：

```js
const form = new FormData();
form.append("file", file);

const res = await fetch("/api/ai/pest/detect?plotId=1", {
  method: "POST",
  body: form
});
```

注意：上传图片时不要手动设置 `Content-Type: application/json`。

### 7.3 响应示例

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

### 7.4 `modelStatus` 判断

| `modelStatus` | 含义 |
|---|---|
| `MODEL_SERVICE` | 已调用真实图像模型 |
| `MOCK_FALLBACK` | 图像模型不可用，Java 使用本地 fallback 知识库生成结果 |

如果要验收真实模型，必须看到：

```json
"modelStatus": "MODEL_SERVICE"
```

### 7.5 后端已兼容的模型响应

Java 调 Python 时会同时传：

```text
image: 图片文件
file: 图片文件
plotId: 地块ID，可选
conversationId: pest-plot-{plotId}
context: {}
```

后端可解析以下响应结构：

```json
{
  "data": {
    "observation": {
      "pestId": "aphid",
      "pestName": "蚜虫",
      "dangerLevel": "MEDIUM",
      "confidence": 0.91
    },
    "advice": {
      "answer": "防治建议...",
      "source": "MODEL_SERVICE",
      "modelStatus": "SUCCESS"
    }
  }
}
```

也兼容：

```json
{
  "predictions": [
    {
      "label": "蚜虫",
      "score": "91%"
    }
  ],
  "answer": "防治建议..."
}
```

支持字段别名：

| 标准字段 | 兼容字段 |
|---|---|
| `pestId` | `pest_id`, `diseaseId`, `disease_id`, `classId`, `class_id` |
| `pestName` | `pest_name`, `diseaseName`, `disease_name`, `name`, `label`, `className`, `class_name`, `category`, `diagnosis`, `result`, `title` |
| `dangerLevel` | `danger_level`, `riskLevel`, `risk_level`, `severity`, `level` |
| `confidence` | `score`, `probability`, `prob` |
| `answer` | `content`, `text`, `recommendation`, `suggestion`, `advice` |

### 7.6 中文病虫名称映射

后端会把常见中文名称映射到本地知识库：

| 模型返回 | 标准 `pestId` |
|---|---|
| 蚜虫 / aphid | `aphid` |
| 白粉虱 / 粉虱 / whitefly | `whitefly` |
| 蓟马 / thrips | `thrips` |
| 红蜘蛛 / 叶螨 / spider mite | `spider_mite` |
| 粘虫 / 黏虫 / armyworm | `armyworm` |

如果模型返回未知病虫，后端会生成 `model_xxx` 形式的 ID，并返回模型给出的名称和建议。

## 8. 害虫建议、历史、趋势、分布

查询单个害虫建议：

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

响应示例：

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

响应示例：

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

## 9. 前端推荐页面行为

### 9.1 AI 助手页面

建议包含：

- 地块选择框；
- 控制设备选择框；
- 用户输入框；
- AI 回答展示区；
- `actionProposal` 动作确认弹窗；
- 命令状态展示。

### 9.2 害虫识别页面

建议包含：

- 地块选择框；
- 图片上传；
- 识别结果卡片；
- `modelStatus` 标签；
- 防治建议分组展示；
- 历史识别记录；
- 趋势/分布图表。

## 10. 推荐测试顺序

### 10.1 登录

```http
POST /api/auth/login
```

拿到 JWT 后，用于 `/api/ai/chat`、控制接口和命令状态查询。

### 10.2 测试文本问答

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
- AI 正常时 `modelStatus=SUCCESS`
- AI 不可用时 `source=BACKEND_RULE_FALLBACK`

### 10.3 测试 AI 打开水泵

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
- 轮询 `/api/control/commands/{commandNo}`

### 10.4 测试 AI 停止任务

```json
{
  "plotId": 1,
  "message": "停止当前任务",
  "context": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001"
  }
}
```

预期：

- 返回关闭类 `actionProposal`
- 水泵运行时返回 `PUMP_OFF`
- 补光运行时返回 `LIGHT OFF`
- 前端确认后执行关闭命令

### 10.5 测试 AI 控制补光

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

### 10.6 测试害虫/病虫识别

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

### 10.7 测试害虫历史和统计

```http
GET /api/ai/pest/records?plotId=1
GET /api/ai/pest/trend?plotId=1
GET /api/ai/pest/distribution?plotId=1
```

预期：可以看到刚才上传识别后的记录、趋势和分布。

## 11. 常见问题

### 11.1 `/api/ai/chat` 返回 401

原因：没带 JWT 或 token 过期。

处理：重新登录，并带：

```http
Authorization: Bearer xxx
```

### 11.2 AI 问答返回后端兜底

表现：

```json
"source": "BACKEND_RULE_FALLBACK",
"modelStatus": "MODEL_UNAVAILABLE"
```

说明请求到了 Java，但 Python 文本 AI 服务不可用或调用失败。

### 11.3 害虫识别返回 `MOCK_FALLBACK`

说明 Java 没有成功调用图像模型，使用了本地兜底知识库。

需要检查：

- Python AI 服务是否启动；
- `pest.model.url` 是否正确；
- `AI_SERVICE_TOKEN` 是否一致；
- Python 端 `/api/ai/pest/analyze` 是否可用；
- 图片字段是否兼容。Java 当前已同时传 `image` 和 `file`；
- 图片格式是否正常。

### 11.4 AI 返回控制动作但硬件没动

分两步看：

1. 前端是否弹窗确认并调用了控制接口；
2. `/api/control/commands/{commandNo}` 的状态是什么。

状态判断：

- `SUCCESS`：后端认为硬件或网关执行成功；
- `FAILED`：查看 `errorMessage`；
- `TIMEOUT`：命令发出但未收到硬件回执；
- `SENT`：已发送，等待硬件回执。

## 12. 当前后端检查结论

已检查并适配：

- `/api/ai/chat` 文本问答；
- AI 返回 `actionProposal`；
- 页面跳转动作；
- 水泵控制动作；
- 补光控制动作；
- 停止/取消当前任务动作；
- `/api/ai/pest/detect` 害虫/病虫识别；
- 图像模型响应格式兼容；
- 中文病虫名称映射；
- 害虫建议、历史、趋势、分布接口。

当前前端可以按本说明进行完整 AI 功能联调。
