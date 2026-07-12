# 智慧农业后端 AI 功能接口说明

本文档按当前后端代码整理，供前端联调使用。除特别说明外，接口响应均为 JSON，日期时间采用后端默认 ISO 格式。

## 1. 通用约定

### 1.1 服务地址

以下接口均以当前后端服务地址为前缀，例如：

```text
http://localhost:8080
```

### 1.2 统一响应结构

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

- `code = 200` 表示业务成功。
- 参数错误通常返回 `code = 400`。
- 未登录或 Token 无效时返回 HTTP 401，部分场景也可能返回 `code = 401`。
- 服务端异常返回 `code = 500`。
- 标记为“需登录”的接口，请携带请求头：`Authorization: Bearer <token>`。

## 2. 接口总览

| 功能 | 方法 | 路径 | 登录要求 | 请求类型 |
| --- | --- | --- | --- | --- |
| AI 文字问答 | POST | `/api/ai/chat` | 需要 | JSON |
| 语音转文字 | POST | `/api/ai/voice/transcribe` | 需要 | multipart/form-data |
| 语音对话 | POST | `/api/ai/voice/chat` | 需要 | multipart/form-data |
| 文字转语音 | POST | `/api/ai/voice/synthesize` | 需要 | JSON |
| 虫害图片识别 | POST | `/api/ai/pest/detect` | 不需要 | multipart/form-data |
| 单个虫害治理建议 | GET | `/api/ai/pest/suggestions/{pestId}` | 不需要 | - |
| 全部虫害治理建议 | GET | `/api/ai/pest/suggestions` | 不需要 | - |
| 虫害识别记录 | GET | `/api/ai/pest/records` | 不需要 | Query |
| 虫害趋势 | GET | `/api/ai/pest/trend` | 不需要 | Query |
| 虫害分布 | GET | `/api/ai/pest/distribution` | 不需要 | Query |
| 人脸信息注册 | POST | `/api/auth/face/register` | 需要 | multipart/form-data |
| 人脸自动登录 | POST | `/api/auth/face/login-auto` | 不需要 | multipart/form-data |
| 查询人脸注册状态 | GET | `/api/auth/face/status` | 不需要 | Query |

## 3. AI 文字问答

### POST `/api/ai/chat`

结合当前地块、设备、传感器、灌溉/补光策略和活动告警回答农业问题；也可识别页面跳转和设备控制意图。

请求示例：

```json
{
  "conversationId": "conv-001",
  "plotId": 1,
  "message": "一号地块现在需要浇水吗？",
  "context": {
    "currentRoute": "/dashboard"
  },
  "forceCommit": false
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `conversationId` | 否 | 会话 ID；不传时后端可生成，用于连续对话和记忆 |
| `plotId` | 否 | 当前关注的地块 ID |
| `message` | 是 | 用户问题 |
| `context` | 否 | 前端附加上下文，JSON 对象 |
| `forceCommit` | 否 | 是否强制提交会话记忆 |

为兼容不同前端，请求体也可直接传字符串；问题字段还兼容 `question`、`query`、`input`、`prompt`、`text`、`content`，以及 OpenAI 风格 `messages` 数组中的最后一条用户消息。建议前端统一使用 `message`。

响应 `data` 示例：

```json
{
  "conversationId": "conv-001",
  "plotId": 1,
  "question": "一号地块现在需要浇水吗？",
  "answer": "当前土壤湿度偏低，建议检查设备后进行灌溉。",
  "status": "success",
  "source": "AI_MODEL_SERVICE",
  "modelStatus": "AVAILABLE",
  "suggestions": ["查看实时数据", "检查灌溉策略"],
  "actionProposal": null,
  "memoryCommitted": false,
  "memoryRecallCount": 2,
  "answeredAt": "2026-07-12T10:30:00"
}
```

主要字段说明：

- `status`：正常模型回答通常为 `success`；AI 服务不可用时为 `fallback`。
- `source`：正常模型一般为 `AI_MODEL_SERVICE`；降级回答为 `BACKEND_RULE_FALLBACK`。
- `modelStatus` / `errorMessage`：模型服务状态及错误提示。
- `suggestions`：可展示为推荐追问。
- `actionProposal`：AI 只提出动作，不直接操作页面或硬件。
- `memoryCommitted` / `memoryRecallCount`：会话记忆写入状态及召回数量。

页面跳转动作示例：

```json
{
  "type": "NAVIGATE",
  "route": "/devices",
  "title": "设备管理",
  "requiresConfirmation": false
}
```

硬件控制动作示例：

```json
{
  "type": "PUMP_ON",
  "api": "/api/control/irrigation",
  "method": "POST",
  "requiresConfirmation": true,
  "payload": {
    "deviceCode": "PUMP-001",
    "action": "ON"
  },
  "description": "开启灌溉设备"
}
```

前端处理要求：`NAVIGATE` 可直接按 `route` 跳转；硬件动作必须先展示确认框，用户确认后再按 `api`、`method`、`payload` 调用控制接口。

## 4. AI 语音能力

### 4.1 语音转文字

`POST /api/ai/voice/transcribe`

表单字段：`file`（必填，音频文件）。

```json
{
  "text": "一号地块需要浇水吗",
  "language": "zh",
  "source": "AI_VOICE_SERVICE",
  "modelStatus": "AVAILABLE",
  "errorMessage": null
}
```

### 4.2 一站式语音对话

`POST /api/ai/voice/chat`

该接口依次完成“语音识别 → AI 问答 → 可选语音合成”。

| 表单字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 音频文件 |
| `plotId` | 否 | 当前地块 ID |
| `deviceCode` | 否 | 当前设备编号，将加入问答上下文 |
| `conversationId` | 否 | 会话 ID |
| `forceCommit` | 否 | 是否强制写入记忆 |
| `synthesize` | 否 | `true` 时将 AI 答案合成为音频 |

```json
{
  "transcribedText": "打开一号水泵",
  "chat": {
    "answer": "已识别到灌溉控制意图，请确认后执行。",
    "actionProposal": {
      "type": "PUMP_ON",
      "requiresConfirmation": true
    }
  },
  "audioUrl": null,
  "audioBase64": "...",
  "audioContentType": "audio/mpeg",
  "voiceStatus": "SUCCESS",
  "voiceErrorMessage": null
}
```

若语音识别不可用，`voiceStatus` 为 `ASR_UNAVAILABLE`；若请求了合成但合成服务不可用，则为 `TTS_UNAVAILABLE`。前端应展示文字错误提示，并允许用户改用文字输入。

### 4.3 文字转语音

`POST /api/ai/voice/synthesize`

```json
{
  "text": "当前土壤湿度偏低，建议适量灌溉。",
  "voice": "default",
  "format": "mp3"
}
```

响应字段包括 `text`、`audioUrl`、`audioBase64`、`audioContentType`、`format`、`source`、`modelStatus`、`errorMessage`。音频服务可能返回 URL，也可能直接返回 Base64，前端需要兼容两种方式。

## 5. AI 虫害识别

### 5.1 上传图片识别

`POST /api/ai/pest/detect`

| 表单字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 待识别图片 |
| `plotId` | 否 | 关联地块 ID |

```json
{
  "plotId": 1,
  "fileName": "leaf.jpg",
  "pestId": "aphid",
  "pestName": "蚜虫",
  "dangerLevel": "MEDIUM",
  "confidence": 0.92,
  "modelStatus": "MODEL_SERVICE",
  "detectTime": "2026-07-12T10:30:00",
  "suggestion": {
    "pestId": "aphid",
    "pestName": "蚜虫",
    "dangerLevel": "MEDIUM",
    "description": "...",
    "physicalControl": ["..."],
    "biologicalControl": ["..."],
    "chemicalControl": ["..."],
    "prevention": ["..."]
  }
}
```

`confidence` 为 0～1 的置信度。`modelStatus = MODEL_SERVICE` 表示来自模型服务；`MOCK_FALLBACK` 表示模型不可用，后端使用了降级模拟结果，前端建议明确标注“演示/降级结果”。成功识别后会保存识别记录。

### 5.2 查询治理建议

- `GET /api/ai/pest/suggestions/{pestId}`：查询指定虫害。
- `GET /api/ai/pest/suggestions`：查询全部内置虫害知识。

建议对象包含虫害说明、危险等级，以及物理、生物、化学和预防四类治理建议。

### 5.3 查询识别记录

`GET /api/ai/pest/records`

可选参数：`plotId`、`pestId`、`startDate`、`endDate`；日期格式为 `YYYY-MM-DD`。

记录字段：`id`、`plotId`、`fileName`、`pestId`、`pestName`、`dangerLevel`、`confidence`、`modelStatus`、`detectedAt`、`createdAt`。

### 5.4 虫害趋势

`GET /api/ai/pest/trend`

可选参数：`plotId`、`startDate`、`endDate`。

```json
[
  {
    "date": "2026-07-12",
    "totalCount": 4,
    "pestCounts": {
      "aphid": 3,
      "whitefly": 1
    }
  }
]
```

### 5.5 虫害分布

`GET /api/ai/pest/distribution`

可选参数：`plotId`、`startDate`、`endDate`。

```json
[
  {
    "pestId": "aphid",
    "pestName": "蚜虫",
    "count": 8,
    "percent": 66.67
  }
]
```

## 6. 人脸识别登录（AI 相关认证能力）

这组接口位于 `/api/auth/face/**`，不属于 `/api/ai/**`。

### 6.1 注册当前用户人脸

`POST /api/auth/face/register`，需登录，表单字段 `file`。成功时 `data` 为 `true`。

### 6.2 人脸自动登录

`POST /api/auth/face/login-auto`，无需登录，表单字段 `file`。

成功时返回：

```json
{
  "token": "JWT_TOKEN",
  "userId": 1,
  "username": "admin",
  "nickname": "管理员",
  "role": "ADMIN"
}
```

### 6.3 查询人脸注册状态

`GET /api/auth/face/status?username=admin`，返回布尔值。

## 7. 前端接入注意事项

1. 文件上传接口必须使用 `multipart/form-data`，不要手动固定 `Content-Type` 的 boundary。
2. AI 问答和所有语音接口都需要 JWT；虫害 AI 接口目前允许匿名访问。
3. 业务是否成功应优先检查响应体 `code`，同时处理 HTTP 401。
4. AI 服务不可用时，部分接口仍会以统一成功结构返回降级数据，应结合 `status`、`modelStatus`、`voiceStatus` 判断真实模型状态。
5. `actionProposal` 只是动作建议。涉及水泵、灌溉、补光灯等硬件时，前端不得自动执行，必须经过用户确认。
6. 图片、音频大小和格式在 Controller 层没有写死，实际限制受后端上传配置及独立 AI 服务支持格式影响。

