# 智慧农业后端功能清单与实现思路

## 一、项目定位

本项目后端基于 Spring Boot 实现智慧农业平台的核心业务能力，主要负责：

- 用户登录与鉴权；
- 地块、设备、传感器数据管理；
- 水泵灌溉与补光灯控制；
- 自动灌溉、智能补光、阈值告警；
- 遥测数据接收、存储与实时推送；
- AI 智能农事问答与害虫识别；
- 操作日志、灌溉统计、用水限制等辅助管理能力。

后端既对接前端页面，也对接硬件端、MQTT、AI 服务和数据库。

## 二、整体技术架构

### 技术栈

- Java 17
- Spring Boot
- Spring Security + JWT
- MyBatis-Plus
- MySQL 8.0
- WebSocket
- MQTT / EMQX
- HTTP 硬件控制网关
- 外部 AI 服务：`smart-agriculture-ai`

### 后端分层结构

```text
controller  接收前端/硬件请求
service     业务逻辑处理
mapper      MyBatis-Plus 数据库访问
entity      数据库表映射
dto         前端请求参数对象
vo          前端响应对象
config      安全、跨域、MQTT、AI 等配置
security    JWT 鉴权
mqtt        MQTT 消息收发
websocket   实时数据推送
task        定时任务
aspect      操作日志切面
```

## 三、用户与权限模块

### 已实现功能

- 用户注册；
- 用户登录；
- JWT Token 签发；
- 获取当前登录用户；
- 人脸登录；
- 人脸注册；
- 无用户名自动人脸登录。

### 主要接口

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
POST /api/auth/face/register
POST /api/auth/face/login-auto
GET  /api/auth/face/status
```

### 实现思路

用户登录成功后，后端生成 JWT，前端后续请求在请求头携带：

```http
Authorization: Bearer token
```

Spring Security 的 JWT 过滤器解析 token，识别用户身份，并控制接口访问权限。

人脸登录通过调用外部人脸识别服务提取特征，并与数据库中保存的人脸特征进行距离比对，距离低于阈值时自动登录。

## 四、地块管理模块

### 已实现功能

- 地块列表；
- 地块详情；
- 新增地块；
- 修改地块；
- 删除地块；
- 查询某地块最新遥测数据。

### 主要接口

```http
GET    /api/plots
GET    /api/plots/{id}
GET    /api/plots/{id}/latest
POST   /api/plots
PUT    /api/plots/{id}
DELETE /api/plots/{id}
```

### 实现思路

地块表 `plot` 记录地块名称、作物类型、位置、面积、状态、描述等信息。

前端新增或修改地块时传入 `PlotDTO`，后端再转换为 `Plot` 实体写入数据库。这样避免前端直接操作数据库实体中的 `id、createdAt、updatedAt` 等字段。

## 五、设备管理模块

### 已实现功能

- 设备列表；
- 设备详情；
- 新增设备；
- 修改设备；
- 删除设备；
- 设备绑定地块；
- 设备解绑地块；
- 启用设备；
- 停用设备；
- 修改设备状态；
- 根据心跳或遥测更新设备在线状态。

### 主要接口

```http
GET    /api/devices
GET    /api/devices/{id}
POST   /api/devices
PUT    /api/devices/{id}
DELETE /api/devices/{id}
PUT    /api/devices/{id}/bind
PUT    /api/devices/{id}/unbind
PUT    /api/devices/binding
PUT    /api/devices/{id}/enable
PUT    /api/devices/{id}/disable
PATCH  /api/devices/{id}/status
```

### 实现思路

设备表 `device` 记录设备编号、设备名称、设备类型、绑定地块、在线状态、心跳时间、电量、信号强度等。

设备和地块是可变绑定关系，前端可以自由将设备绑定到不同地块，也可以解绑。

设备离线判断由定时任务根据最近心跳或遥测时间判断，超时未上报则更新为离线并生成告警。

## 六、遥测数据模块

### 已实现功能

- 接收硬件 HTTP 上报；
- 接收 MQTT 遥测上报；
- 保存传感器数据；
- 查询地块最新遥测；
- 查询历史遥测；
- WebSocket 实时推送遥测。

### 主要接口

```http
POST /api/iotda/report
GET  /api/telemetry/latest
GET  /api/telemetry/history
```

### 遥测数据字段

```json
{
  "deviceCode": "设备编号",
  "soilMoisture": 35.5,
  "airTemperature": 28.2,
  "airHumidity": 60.1,
  "illuminance": 430.0,
  "pumpStatus": "OFF",
  "lightStatus": "OFF"
}
```

### 实现思路

硬件或模拟器上报数据后，后端根据 `deviceCode` 找到设备，再根据设备绑定关系确定 `plotId`，然后保存到 `telemetry_data` 表。

如果某次上报只包含部分字段，后端会尝试用最近一次遥测数据补齐缺失字段，避免前端图表出现大面积空值。

保存遥测后，后端会触发：

- 设备心跳更新；
- 阈值告警判断；
- 自动灌溉判断；
- 智能补光判断；
- WebSocket 实时推送。

## 七、MQTT 对接模块

### 已实现功能

- 连接 EMQX Broker；
- 订阅设备遥测 Topic；
- 订阅设备心跳 Topic；
- 订阅设备控制回执 Topic；
- 发布控制命令；
- 处理设备回执并更新命令状态。

### MQTT Topic

```text
device/data/upload      遥测上报
device/heartbeat        心跳上报
device/control/cmd      控制命令下发
device/control/reply    控制回执
```

### 实现思路

后端通过 Spring Integration MQTT 接入 EMQX。

硬件端或模拟器向 `device/data/upload` 发布遥测数据，后端订阅后解析 JSON，保存到数据库。

后端控制水泵或补光时，向 `device/control/cmd` 发布命令。设备执行后向 `device/control/reply` 回复执行结果，后端根据 `commandId/commandNo` 更新控制命令状态。

## 八、水泵控制与灌溉模块

### 已实现功能

- 手动打开水泵；
- 手动关闭水泵；
- 查询控制命令列表；
- 查询命令状态；
- 灌溉记录生成；
- 灌溉统计；
- 灌溉趋势；
- 灌溉时长分布；
- 用水统计。

### 主要接口

```http
POST /api/control/send
POST /api/control/irrigation
GET  /api/control/list
GET  /api/control/commands/{commandNo}

GET  /api/irrigation/list
GET  /api/irrigation/stats
GET  /api/irrigation/daily-trend
GET  /api/irrigation/duration-distribution
GET  /api/irrigation/water-usage
```

### 实现思路

前端发送水泵命令后，后端会：

1. 校验设备是否存在；
2. 校验设备是否停用；
3. 写入 `control_command` 表；
4. 调用硬件控制网关或 MQTT 下发命令；
5. 更新命令状态；
6. 如果是 `PUMP_ON`，创建 RUNNING 灌溉记录；
7. 如果是 `PUMP_OFF`，结束最近一条 RUNNING 灌溉记录。

命令状态含义：

```text
PENDING  已创建，待发送
SENT     已发送到 Broker 或硬件网关
SUCCESS  设备确认成功
FAILED   下发失败
TIMEOUT  超时未收到回执
```

## 九、自动灌溉策略模块

### 已实现功能

- 查询灌溉策略；
- 新增或修改灌溉策略；
- 删除灌溉策略；
- 根据土壤湿度自动判断是否灌溉。

### 主要接口

```http
GET    /api/strategies
GET    /api/strategies/{plotId}
PUT    /api/strategies/{plotId}
DELETE /api/strategies/{plotId}
```

### 策略字段

```json
{
  "moistureMin": 30,
  "moistureMax": 60,
  "consecutiveThreshold": 3,
  "autoMode": true,
  "maxDuration": 300,
  "cooldownMinutes": 10
}
```

### 实现思路

自动灌溉只依据土壤湿度，不使用温度上下限。

当某地块连续多次土壤湿度低于 `moistureMin`，且自动模式开启、设备在线、冷却时间满足时，后端自动下发 `PUMP_ON`。

当土壤湿度达到 `moistureMax` 或灌溉超过最大时长时，后端自动下发 `PUMP_OFF`。

## 十、智能补光模块

### 已实现功能

- 手动控制补光灯；
- 查询补光状态；
- 查询补光策略；
- 保存补光策略；
- 根据照度自动开关补光。

### 主要接口

```http
POST /api/light/control
GET  /api/light/status

GET  /api/light-strategies
GET  /api/light-strategies/{plotId}
PUT  /api/light-strategies/{plotId}
```

### 实现思路

补光策略基于照度和时间窗口。

当照度低于 `illuminanceMin` 且当前时间在允许补光时间段内时，后端可以自动下发补光开启命令。

当照度高于 `illuminanceMax` 或不在允许时间段内时，后端可以自动关闭补光。

补光命令最终通过硬件控制网关或 MQTT 发送到 BearPi 设备。

## 十一、告警管理模块

### 已实现功能

- 查询告警；
- 查看告警详情；
- 确认告警；
- 关闭告警；
- 恢复告警；
- 遥测异常自动生成告警；
- 设备离线自动生成告警；
- 用水超限自动生成告警。

### 主要接口

```http
GET  /api/alarms
GET  /api/alarms/{id}
POST /api/alarms/{id}/ack
POST /api/alarms/{id}/close
POST /api/alarms/{id}/recover
```

### 阈值范围

```text
soilMoisture     0 ~ 100
airTemperature  -10 ~ 80
airHumidity      0 ~ 100
illuminance      0 ~ 1000
```

### 实现思路

后端收到遥测数据后立即判断是否超出正常范围。

如果数据异常，会生成对应告警：

```text
SOIL_MOISTURE_ABNORMAL
AIR_TEMPERATURE_ABNORMAL
AIR_HUMIDITY_ABNORMAL
ILLUMINANCE_ABNORMAL
DEVICE_OFFLINE
WATER_DAILY_LIMIT
WATER_MONTHLY_LIMIT
WATER_SINGLE_LIMIT
```

告警状态：

```text
ACTIVE        未处理
ACKNOWLEDGED  已确认
CLOSED        已关闭
RECOVERED     已恢复
```

## 十二、用水限制模块

### 已实现功能

- 查询用水限制；
- 查询某地块用水限制；
- 保存用水限制；
- 灌溉前检查用水上限；
- 灌溉结束后统计用水并判断是否超限。

### 主要接口

```http
GET /api/water-limits
GET /api/water-limits/{plotId}
PUT /api/water-limits/{plotId}
```

### 实现思路

用水限制用于约束单次、单日、单月灌溉水量。

后端在水泵开启前检查当前用水情况，避免超出限制。

灌溉结束后，后端根据灌溉记录计算用水量，并触发提醒或超限告警。

## 十三、WebSocket 实时推送模块

### 已实现功能

- 遥测数据实时推送；
- 告警变化实时推送；
- WebSocket 测试推送；
- 在线连接数统计。

### 主要接口

```http
POST /api/ws/push-test
GET  /api/ws/online-count
```

### 实现思路

前端建立 WebSocket 连接后，后端在收到新遥测或生成告警时推送消息。

前端可用该能力实现数据看板实时刷新。

## 十四、AI 智能农事问答模块

### 已实现功能

- 文本智能问答；
- 后端自动注入业务上下文；
- AI 识别页面跳转意图；
- AI 识别硬件控制意图；
- AI 返回动作建议；
- AI 服务不可用时后端规则兜底。

### 主要接口

```http
POST /api/ai/chat
```

### 请求示例

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

### 实现思路

前端调用 Java 后端 `/api/ai/chat`。

Java 后端会自动补充：

- 地块信息；
- 设备信息；
- 最新遥测；
- 活跃告警；
- 灌溉策略；
- 补光策略；
- 前端页面路由；
- 可执行动作协议。

然后 Java 后端再请求 `smart-agriculture-ai`：

```text
Java 后端 → smart-agriculture-ai → DeepSeek / RAG → Java 后端 → 前端
```

如果 AI 识别到跳转意图，会返回：

```json
{
  "type": "NAVIGATE",
  "route": "/devices"
}
```

如果 AI 识别到硬件控制意图，会返回：

```json
{
  "type": "CONTROL_DEVICE",
  "requiresConfirmation": true,
  "api": "/api/control/send",
  "payload": {
    "deviceCode": "xxx",
    "commandType": "PUMP_ON",
    "commandValue": "ON"
  }
}
```

前端必须让用户确认后再执行硬件控制。

## 十五、害虫识别模块

### 已实现功能

- 上传图片识别害虫；
- 调用 AI 图像识别服务；
- 保存识别记录；
- 查询识别记录；
- 查询害虫治理建议；
- 查询害虫趋势；
- 查询害虫分布。

### 主要接口

```http
POST /api/ai/pest/detect
GET  /api/ai/pest/suggestions/{pestId}
GET  /api/ai/pest/suggestions
GET  /api/ai/pest/records
GET  /api/ai/pest/trend
GET  /api/ai/pest/distribution
```

### 实现思路

前端上传图片到后端，后端再调用 `smart-agriculture-ai` 的图像识别接口。

AI 返回害虫名称、置信度、观测结果和治理建议。

后端保存识别记录，并结合害虫知识库返回更适合前端展示的结构化结果。

## 十六、操作日志模块

### 已实现功能

- 记录用户操作；
- 查询操作日志；
- 记录操作类型、目标对象、操作者、结果、错误信息。

### 主要接口

```http
GET /api/operation-logs
```

### 实现思路

后端通过 AOP 切面拦截带有 `@OperationLogRecord` 注解的接口。

接口执行成功或失败后，统一写入 `operation_log` 表，便于后期追踪用户操作和排查问题。

## 十七、硬件对接方式

### 当前支持两种方式

#### 方式一：MQTT

```text
设备/模拟器 → EMQX → 后端 MQTT 订阅 → 数据入库
后端 → EMQX → 设备订阅控制 Topic → 执行命令
```

#### 方式二：HTTP 硬件网关

```text
前端发控制请求 → Java 后端 → HTTP 网关 → BearPi 设备
```

硬件网关配置：

```yaml
hardware:
  control:
    enabled: true
    base-url: http://192.168.20.84:3000
    device: bearpi_001
```

### 实现思路

后端控制命令统一先写入 `control_command` 表，再根据配置选择：

- 通过 MQTT 下发；
- 或通过 HTTP 硬件网关下发。

这样前端接口保持不变，底层硬件对接方式可以切换。

## 十八、前端联调支持

### 已提供简易前端

项目中已新增：

```text
frontend/index.html
frontend/app.js
frontend/styles.css
frontend/server.js
frontend/README.md
```

### 启动方式

```powershell
node frontend/server.js
```

访问：

```text
http://localhost:5173
```

### 前端已接入能力

- 登录注册；
- 地块管理；
- 设备管理；
- 遥测数据；
- 水泵与补光控制；
- 策略配置；
- 告警管理；
- 用水限制；
- 操作日志；
- AI 问答；
- 害虫识别。

## 十九、核心业务流程总结

### 遥测上报流程

```text
设备/模拟器上报数据
→ 后端解析 deviceCode
→ 找到设备和地块
→ 保存 telemetry_data
→ 更新设备心跳
→ 判断阈值告警
→ 判断自动灌溉/补光
→ WebSocket 推送前端
```

### 水泵控制流程

```text
前端发送控制请求
→ 后端校验设备
→ 写入 control_command
→ 下发到 MQTT 或硬件网关
→ 更新命令状态
→ 生成或结束 irrigation_record
→ 前端查询命令状态
```

### AI 问答流程

```text
前端提问
→ Java 后端补充业务上下文
→ 调用 smart-agriculture-ai
→ AI 根据项目数据生成回答
→ Java 后端统一包装
→ 前端展示回答或执行 actionProposal
```

## 二十、项目当前完成度

目前后端已经具备完整智慧农业系统的主要能力：

- 基础管理：用户、地块、设备；
- 数据采集：HTTP、MQTT、WebSocket；
- 农业控制：水泵、补光灯；
- 自动化策略：自动灌溉、智能补光；
- 风险处理：阈值告警、设备离线、用水超限；
- 智能能力：AI 问答、害虫识别；
- 联调能力：简易前端、Postman、模拟器、硬件网关。

整体已经可以支撑前端页面展示、硬件联调和项目答辩演示。
