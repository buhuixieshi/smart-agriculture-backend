# AI 服务切换说明

## 本次调整

后端已经将 AI 调用从设备端提供的旧模型服务，切换为当前后端侧自建的 AI 服务。

默认 AI 服务地址：

```text
http://127.0.0.1:5000
```

前端现有接口路径保持不变，不需要改页面调用地址。

## 智能农事问答

前端继续调用：

```http
POST /api/ai/chat
Content-Type: application/json
```

请求体兼容以下字段：

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

后端会转发到：

```http
POST http://127.0.0.1:5000/api/ai/chat
```

成功接入真实 AI 时，前端返回中不应再看到：

```text
source=BACKEND_RULE_FALLBACK
modelStatus=MODEL_UNAVAILABLE
```

正常应看到类似：

```text
source=DEEPSEEK
modelStatus=SUCCESS
```

如果 AI 服务未启动、地址不可达或 token 不一致，后端仍会自动降级为规则问答，保证页面可用。

## 虫害图像识别

前端继续调用原有后端接口：

```http
POST /api/ai/pest/detect
Content-Type: multipart/form-data
```

或兼容旧路径：

```http
POST /api/pest/detect
Content-Type: multipart/form-data
```

前端表单字段仍使用：

```text
file: 图片文件
plotId: 地块ID，可选
```

后端内部会转换并调用新的 AI 服务：

```http
POST http://127.0.0.1:5000/api/ai/pest/analyze
Content-Type: multipart/form-data
```

其中后端转发给 AI 服务的图片字段名为：

```text
image
```

模型返回 `observation + advice` 结构后，后端会映射回前端现有的 `PestDetectVO` 结构，因此前端展示逻辑暂时不用改。

## Token 配置

如果 AI 服务配置了 `AI_SERVICE_TOKEN`，后端启动时也需要配置同名环境变量：

```text
AI_SERVICE_TOKEN=你的token
```

后端调用 AI 服务时会自动携带：

```http
X-AI-Service-Token: 你的token
```

如果 AI 服务没有配置 token，可以不填。

## 后端配置位置

```yaml
pest:
  model:
    enabled: true
    url: http://127.0.0.1:5000/api/ai/pest/analyze
    token: ${AI_SERVICE_TOKEN:}
    connect-timeout-seconds: 5
    read-timeout-seconds: 240

ai:
  chat:
    model:
      enabled: true
      url: http://127.0.0.1:5000/api/ai/chat
      token: ${AI_SERVICE_TOKEN:}
      connect-timeout-seconds: 5
      read-timeout-seconds: 100
```

## 联调检查

1. 先启动 AI 服务，确认 `http://127.0.0.1:5000/docs` 能打开。
2. 启动 Spring Boot 后端。
3. 前端测试 `/api/ai/chat`，确认返回不再是 `MODEL_UNAVAILABLE`。
4. 前端上传虫害图片，确认返回 `modelStatus=MODEL_SERVICE`，而不是 `MOCK_FALLBACK`。
5. 如果仍然降级，先检查后端日志中的 AI 服务 URL、token、连接超时和返回格式。
