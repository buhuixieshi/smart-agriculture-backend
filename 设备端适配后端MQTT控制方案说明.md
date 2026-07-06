# 设备端适配后端 MQTT 控制方案说明

当前控制方案已改回后端 MQTT 直发，不再调用华为云 IoTDA REST API。

也就是说：

```text
前端 / Postman
  -> 后端 /api/control/send
  -> MQTT Broker
  -> 设备端订阅控制 Topic
  -> BearPi 执行水泵/补光灯动作
  -> 设备端发布执行结果回复
  -> 后端更新命令状态
```

## 一、后端 MQTT 配置

当前后端配置位于：

```text
src/main/resources/application.yml
```

关键配置：

```yaml
mqtt:
  enabled: true
  broker-url: tcp://127.0.0.1:1883
  client-id: smart-agriculture-backend
  username: fasong
  password: fasong123
  qos: 1
  topics:
    telemetry: device/data/upload
    heartbeat: device/heartbeat
    control: device/control/cmd
    control-reply: device/control/reply
```

说明：

- 后端发布控制命令到 `device/control/cmd`。
- 后端监听设备回复 Topic `device/control/reply`。
- 后端监听遥测 Topic `device/data/upload`。
- 后端监听心跳 Topic `device/heartbeat`。

如果设备端连接的是同一个 MQTT Broker，就需要使用同样的地址、账号、密码和 Topic。

## 二、设备端需要订阅的控制 Topic

设备端需要订阅：

```text
device/control/cmd
```

后端每次调用控制接口后，会向该 Topic 发布一条 JSON 命令。

## 三、后端控制接口

### 1. 打开水泵

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer 后端登录token
```

### 2. 关闭水泵

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_OFF&commandValue=OFF
Authorization: Bearer 后端登录token
```

### 3. 打开补光灯

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
Authorization: Bearer 后端登录token
```

### 4. 关闭补光灯

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_OFF&commandValue=OFF
Authorization: Bearer 后端登录token
```

后端接口成功调用后，命令初始状态一般是：

```text
SENT
```

表示后端已经发布到 MQTT Broker。

设备端执行并回复后，后端会把状态更新为：

```text
SUCCESS
```

或：

```text
FAILED
```

## 四、后端发布给设备端的 MQTT Payload

### 1. 打开水泵示例

Topic：

```text
device/control/cmd
```

Payload：

```json
{
  "commandId": "CMD20260705094002408",
  "commandNo": "CMD20260705094002408",
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "commandType": "PUMP_ON",
  "commandValue": "ON",
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

设备端可以按两种方式解析：

推荐按通用字段：

```text
commandType = PUMP_ON
commandValue = ON
```

或者按 IoTDA 风格字段：

```text
command_name = Agriculture_Control_Motor
paras.Motor = ON
```

### 2. 关闭水泵示例

```json
{
  "commandId": "CMD20260705094002409",
  "commandNo": "CMD20260705094002409",
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "commandType": "PUMP_OFF",
  "commandValue": "OFF",
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

### 3. 打开补光灯示例

```json
{
  "commandId": "CMD20260705094002410",
  "commandNo": "CMD20260705094002410",
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "commandType": "LIGHT_ON",
  "commandValue": "ON",
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

### 4. 关闭补光灯示例

```json
{
  "commandId": "CMD20260705094002411",
  "commandNo": "CMD20260705094002411",
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "commandType": "LIGHT_OFF",
  "commandValue": "OFF",
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "OFF"
  }
}
```

## 五、设备端执行规则

设备端收到 `device/control/cmd` 后：

| commandType | 执行动作 |
|---|---|
| PUMP_ON | 打开水泵 |
| PUMP_OFF | 关闭水泵 |
| LIGHT_ON | 打开补光灯 |
| LIGHT_OFF | 关闭补光灯 |

如果设备端更方便按 IoTDA 风格字段解析：

| command_name | paras | 执行动作 |
|---|---|---|
| Agriculture_Control_Motor | Motor=ON | 打开水泵 |
| Agriculture_Control_Motor | Motor=OFF | 关闭水泵 |
| Agriculture_Control_Light | Light=ON | 打开补光灯 |
| Agriculture_Control_Light | Light=OFF | 关闭补光灯 |

## 六、设备端执行后必须回复

设备端执行完成后，需要向下面 Topic 发布回复：

```text
device/control/reply
```

### 1. 执行成功回复

```json
{
  "commandId": "CMD20260705094002408",
  "status": "SUCCESS",
  "message": "pump on ok"
}
```

说明：

- `commandId` 必须使用后端下发 Payload 里的 `commandId` 或 `commandNo`。
- `status` 成功可以写 `SUCCESS`、`OK`、`TRUE`。
- 后端收到后会把命令状态更新为 `SUCCESS`，并写入 `ackAt`。

### 2. 执行失败回复

```json
{
  "commandId": "CMD20260705094002408",
  "status": "FAILED",
  "message": "pump gpio error"
}
```

说明：

- `status` 失败可以写 `FAILED`、`FAIL`、`ERROR`、`FALSE`。
- 后端收到后会把命令状态更新为 `FAILED`。

## 七、设备端遥测上报

设备端执行后，建议继续上报最新设备状态。

Topic：

```text
device/data/upload
```

Payload 示例：

```json
{
  "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
  "Temperature": 28,
  "Humidity": 58,
  "Luminance": 493,
  "MotorStatus": "ON",
  "LightStatus": "OFF"
}
```

后端会保存为遥测数据，并用于前端实时展示。

## 八、后端测试方式

1. 启动 MQTT Broker。
2. 启动后端。
3. 设备端连接同一个 MQTT Broker。
4. 设备端订阅 `device/control/cmd`。
5. 后端调用 `/api/control/send`。
6. 设备端收到命令并执行。
7. 设备端发布 `device/control/reply`。
8. 后端查询命令状态应为 `SUCCESS`。

查询命令状态：

```http
GET http://localhost:8080/api/control/commands/{commandNo}
Authorization: Bearer 后端登录token
```

成功时应看到：

```json
{
  "status": "SUCCESS",
  "ackAt": "有时间",
  "errorMessage": "pump on ok"
}
```

## 九、当前结论

当前正式采用：

```text
后端 MQTT 控制方案
```

不再要求后端使用 IoTDA REST API。

设备端需要适配：

```text
订阅 device/control/cmd
解析后端 Payload
执行 BearPi 控制
发布 device/control/reply
继续上报 device/data/upload
```

