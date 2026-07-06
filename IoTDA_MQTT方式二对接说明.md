# IoTDA MQTT 方式二对接说明

日期：2026-07-04

## 1. 当前采用方案

根据设备端最新说明，当前后端采用：

```text
方式二：MQTT Publish 直发 IoTDA 命令 Topic
```

也就是：

```text
前端/Postman
-> 后端 /api/control/send
-> 后端 MQTT publish 到 IoTDA 命令 Topic
-> IoTDA 推送命令给 BearPi
-> BearPi 执行灯/泵动作
```

之前的 `server.js HTTP 控制` 已保留为备用代码，但默认关闭。

当前配置：

```yaml
hardware:
  control:
    enabled: false
```

含义：

```text
enabled=false 时，后端不调用 server.js /api/control，而是走 MQTT 方式二。
```

## 2. 设备信息

真实设备 ID：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

后端控制接口中的 `deviceCode` 必须使用：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

## 3. 后端 MQTT 下发 Topic

后端会 publish 到：

```text
$oc/devices/{deviceCode}/sys/commands/request_id={commandNo}
```

示例：

```text
$oc/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/sys/commands/request_id=CMD20260704153000123
```

其中：

```text
deviceCode = 后端控制接口传入的设备码
commandNo  = 后端生成的命令编号
```

## 4. 后端 MQTT 下发 Payload

### 4.1 打开水泵

调用后端：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer <token>
```

后端发布 payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

### 4.2 关闭水泵

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_OFF&commandValue=OFF
Authorization: Bearer <token>
```

后端发布 payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

### 4.3 打开补光灯

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
Authorization: Bearer <token>
```

后端发布 payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

### 4.4 关闭补光灯

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_OFF&commandValue=OFF
Authorization: Bearer <token>
```

后端发布 payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "OFF"
  }
}
```

## 5. 后端配置注意事项

方式二要求后端连接的是 IoTDA MQTT Broker，而不是本地 EMQX。

当前项目仍保留这些 MQTT 配置：

```yaml
mqtt:
  enabled: true
  broker-url: tcp://127.0.0.1:1883
  client-id: smart-agriculture-backend
  username: fasong
  password: fasong123
```

如果要真实发给 BearPi，必须把这些配置改成 IoTDA MQTT 的真实连接信息：

```text
broker-url：IoTDA MQTT Broker 地址
client-id：IoTDA 要求的 clientId
username：IoTDA MQTT username
password：IoTDA MQTT password
```

如果仍然连接 `tcp://127.0.0.1:1883`，命令只会发到本地 MQTT Broker，BearPi 收不到。

## 6. 设备上报兼容

后端 `/api/iotda/report` 已兼容设备端最新属性格式：

```json
{
  "services": [{
    "service_id": "Agriculture",
    "properties": {
      "Temperature": 28,
      "Humidity": 58,
      "Luminance": 493,
      "LightStatus": "OFF",
      "MotorStatus": "OFF"
    }
  }]
}
```

后端会映射为：

```text
Temperature -> airTemperature
Humidity    -> airHumidity
Luminance   -> illuminance
LightStatus -> lightStatus
MotorStatus -> pumpStatus
```

如果上报中没有设备码，后端默认归属到：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

## 7. 测试步骤

### 7.1 登录获取 token

```http
POST /api/auth/login
Content-Type: application/json
```

拿到：

```text
data.token
```

### 7.2 开泵

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer <token>
```

如果 MQTT publish 成功，后端返回状态通常是：

```text
SENT
```

注意：

```text
SENT 表示后端已 publish 到 MQTT Broker，不等于 BearPi 已执行。
```

### 7.3 查询命令状态

```http
GET /api/control/commands/{commandNo}
Authorization: Bearer <token>
```

如果后续没有 IoTDA 命令回执接入后端，命令可能在 30 秒后变为：

```text
TIMEOUT
```

但 BearPi 仍可能已经执行。最终硬件动作建议通过后续属性上报确认：

```text
MotorStatus = ON / OFF
LightStatus = ON / OFF
```

### 7.4 查最新遥测

```http
GET /api/telemetry/latest?plotId=1
```

确认：

```text
pumpStatus / lightStatus 是否与控制动作一致
```

## 8. 当前限制

当前后端已经完成：

```text
1. 按方式二生成 IoTDA MQTT 命令 Topic。
2. 按方式二生成 IoTDA 命令 payload。
3. 控制接口可触发 MQTT publish。
4. /api/iotda/report 兼容 services[0].properties 属性格式。
```

仍需确认：

```text
1. 后端 MQTT 是否已经改成真实 IoTDA Broker。
2. IoTDA MQTT 鉴权参数是否正确。
3. IoTDA 命令执行结果是否会通过某个 Topic 或规则引擎回传后端。
4. 如果没有命令回执，前端应以属性上报中的 MotorStatus / LightStatus 判断最终状态。
```
