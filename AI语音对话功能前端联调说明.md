# AI语音对话功能前端联调说明

## 1. 本次新增能力

后端已新增 AI 语音对话接口，前端不需要直接访问 Python AI 服务。

链路如下：

```text
前端录音
  -> Java 后端 /api/ai/voice/chat
  -> Python AI 语音识别 ASR
  -> Java 后端 /api/ai/chat 复用现有智能农事问答能力
  -> 可选：Python AI 语音合成 TTS
  -> 前端展示文字回答，必要时播放语音回答
```

语音问答和文本问答共用同一套 AI 上下文、硬件控制建议、页面跳转建议、停止任务建议。

## 2. 后端配置

Java 后端新增配置：

```yaml
ai:
  voice:
    model:
      enabled: true
      transcribe-url: http://127.0.0.1:5002/api/ai/voice/transcribe
      synthesize-url: http://127.0.0.1:5002/api/ai/voice/synthesize
      token: ${AI_SERVICE_TOKEN:}
      connect-timeout-seconds: 5
      read-timeout-seconds: 240
```

如果 Python AI 端语音接口路径不同，只需要改这里的 `transcribe-url` 和 `synthesize-url`。

## 3. 鉴权要求

以下语音接口都需要登录后携带 JWT：

```http
Authorization: Bearer xxx
```

接口：

```text
POST /api/ai/voice/transcribe
POST /api/ai/voice/chat
POST /api/ai/voice/synthesize
```

## 4. 语音转文字

### 请求

```http
POST /api/ai/voice/transcribe
Content-Type: multipart/form-data
Authorization: Bearer xxx
```

表单参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 录音文件，建议 wav/mp3/webm/m4a |

前端示例：

```js
const form = new FormData();
form.append("file", audioFile);

const res = await fetch("/api/ai/voice/transcribe", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`
  },
  body: form
});
```

注意：上传 `FormData` 时不要手动设置 `Content-Type`，浏览器会自动带 boundary。

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "text": "打开一号地块的水泵",
    "language": "zh",
    "source": "AI_VOICE_SERVICE",
    "modelStatus": "SUCCESS"
  }
}
```

### 语音服务不可用

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "source": "BACKEND_VOICE_FALLBACK",
    "modelStatus": "MODEL_UNAVAILABLE",
    "errorMessage": "AI语音识别服务暂时不可用，请检查 ai.voice.model.transcribe-url 和 AI 服务进程"
  }
}
```

## 5. 语音问答

这是前端最推荐使用的接口。

### 请求

```http
POST /api/ai/voice/chat
Content-Type: multipart/form-data
Authorization: Bearer xxx
```

表单参数：

| 参数 | 必填 | 说明 |
|---|---|---|
| `file` | 是 | 用户录音文件 |
| `plotId` | 否 | 当前地块 ID |
| `deviceCode` | 否 | 当前选择的设备编号；涉及控制硬件时建议传 |
| `conversationId` | 否 | 会话 ID |
| `forceCommit` | 否 | 是否强制提交长期记忆，默认 false |
| `synthesize` | 否 | 是否同时返回语音回答，默认 false |

前端示例：

```js
const form = new FormData();
form.append("file", audioFile);
form.append("plotId", "1");
form.append("deviceCode", "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001");
form.append("conversationId", "web-user-1");
form.append("synthesize", "true");

const res = await fetch("/api/ai/voice/chat", {
  method: "POST",
  headers: {
    Authorization: `Bearer ${token}`
  },
  body: form
});
```

### 响应示例

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "transcribedText": "打开一号地块的水泵",
    "chat": {
      "conversationId": "web-user-1",
      "plotId": 1,
      "question": "打开一号地块的水泵",
      "answer": "已识别到打开水泵的意图，请确认是否执行。",
      "status": "success",
      "source": "DEEPSEEK",
      "modelStatus": "SUCCESS",
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
        "description": "用户要求打开水泵，前端确认后执行。"
      }
    },
    "audioBase64": "base64音频内容",
    "audioContentType": "audio/mpeg",
    "voiceStatus": "SUCCESS"
  }
}
```

## 6. 文字转语音

如果前端只想把已有文字回答转成语音，可以单独调用：

```http
POST /api/ai/voice/synthesize
Content-Type: application/json
Authorization: Bearer xxx
```

请求体：

```json
{
  "text": "当前一号地块土壤湿度正常，暂不建议灌溉。",
  "voice": "default",
  "format": "mp3"
}
```

响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "text": "当前一号地块土壤湿度正常，暂不建议灌溉。",
    "audioBase64": "base64音频内容",
    "audioContentType": "audio/mpeg",
    "format": "mp3",
    "source": "AI_VOICE_SERVICE",
    "modelStatus": "SUCCESS"
  }
}
```

前端播放 base64 音频示例：

```js
const audio = new Audio(`data:${audioContentType};base64,${audioBase64}`);
audio.play();
```

## 7. 和硬件控制的关系

语音接口不会直接绕过前端去控制硬件。

语音控制流程仍然是：

```text
用户语音：“打开水泵”
  -> /api/ai/voice/chat
  -> 返回 chat.actionProposal
  -> 前端弹窗确认
  -> 前端调用 /api/control/send 或 /api/light/control
```

前端仍按 `actionProposal.type` 固定处理：

| type | 前端处理 |
|---|---|
| `CONTROL_DEVICE` | 弹窗确认后调用 `POST /api/control/send` |
| `CONTROL_LIGHT` | 弹窗确认后调用 `POST /api/light/control` |
| `NAVIGATE` | 直接或确认后跳转页面 |

## 8. 语音停止任务

用户语音可以说：

```text
停止当前任务
关闭水泵
停止灌溉
关灯
关闭补光灯
```

后端会复用文本 AI 的停止任务逻辑，返回关闭类 `actionProposal`。

例如：

```json
{
  "type": "CONTROL_DEVICE",
  "payload": {
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "commandType": "PUMP_OFF",
    "commandValue": "OFF"
  }
}
```

## 9. 测试顺序

1. 登录获取 JWT。
2. 直接测试 `POST /api/ai/chat`，确认文本 AI 可用。
3. 测试 `POST /api/ai/voice/transcribe`，确认录音能转文字。
4. 测试 `POST /api/ai/voice/chat?synthesize=false`，确认语音能进入 AI 问答。
5. 测试 `POST /api/ai/voice/chat?synthesize=true`，确认能返回语音回答。
6. 用语音说“打开水泵”，检查是否返回 `actionProposal.type=CONTROL_DEVICE`。
7. 用语音说“停止当前任务”，检查是否返回关闭水泵或关闭补光的动作建议。

## 10. 当前后端完成情况

已完成：

- Java 后端语音配置；
- 语音转文字接口；
- 语音问答接口；
- 文字转语音接口；
- 语音问答复用现有 AI 上下文；
- 语音问答复用硬件控制建议；
- 语音问答复用停止任务逻辑；
- 语音回答 base64 返回；
- `/api/ai/voice/**` JWT 鉴权；
- 编译验证通过。

需要 AI 端配合：

- 提供 `POST /api/ai/voice/transcribe`；
- 提供 `POST /api/ai/voice/synthesize`；
- 或者把实际语音接口路径告诉后端，后端只需改 `application.yml`。
