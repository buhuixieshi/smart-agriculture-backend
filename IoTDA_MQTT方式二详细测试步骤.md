# IoTDA MQTT 方式二详细测试步骤

本文档用于测试当前后端与唯一真实硬件设备 BearPi 的连接情况。

当前采用的方案是：

```text
前端 / Postman
  -> 后端 Spring Boot
  -> MQTT 发布 IoTDA 命令
  -> 华为 IoTDA
  -> BearPi
```

也就是说，控制命令不再通过 `server.js` 串口转发，而是由后端直接发布 MQTT 命令到 IoTDA。

## 一、当前对接结论

当前推荐并采用的是“方式二：后端直连 IoTDA MQTT”。

后端发送控制命令时，会发布到 IoTDA 的设备命令 Topic：

```text
$oc/devices/{deviceCode}/sys/commands/request_id={commandNo}
```

BearPi 的真实设备编号为：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

后端控制水泵时，会发送类似：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

后端控制补光灯时，会发送类似：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

## 二、测试前准备

### 1. 确认后端配置

打开：

```text
src/main/resources/application.yml
```

确认当前使用 MQTT 方式，而不是 `server.js` HTTP 方式：

```yaml
hardware:
  control:
    enabled: false

mqtt:
  enabled: true
```

注意：

如果 `mqtt.broker-url` 还是下面这个本地地址：

```yaml
mqtt:
  broker-url: tcp://127.0.0.1:1883
```

那么命令只会发到本机 EMQX，BearPi 不会收到。

真实联调 BearPi 时，必须把 MQTT 配置改成华为 IoTDA 提供的连接信息：

```yaml
mqtt:
  enabled: true
  broker-url: tcp://你的IoTDA_MQTT地址:1883
  client-id: 你的IoTDA客户端ID
  username: 你的IoTDA用户名
  password: 你的IoTDA密码
  qos: 1
  topics:
    telemetry: device/data/upload
    heartbeat: device/heartbeat
    control: device/control/cmd
    control-reply: device/control/reply
```

其中 `broker-url`、`client-id`、`username`、`password` 必须以成员5提供的 IoTDA 信息为准。

### 2. 确认数据库有 BearPi 设备

在数据库中确认 `device` 表里存在 BearPi 设备记录。

推荐检查 SQL：

```sql
SELECT id, device_code, device_name, device_type, plot_id, status
FROM device
WHERE device_code = '6a44b8fdcbb0cf6bb96ad1a1_bearpi_001';
```

如果查不到，需要先插入或修正设备数据，否则控制命令会提示设备不存在。

### 3. 启动后端

方式一：在 IDEA 中运行：

```text
SmartAgricultureApplication
```

方式二：在项目根目录执行：

```bash
mvn spring-boot:run
```

启动成功后，访问健康检查接口：

```http
GET http://localhost:8080/api/health
```

期望返回：

```json
{
  "code": 200,
  "message": "success"
}
```

## 三、先测试 BearPi 数据上报链路

控制之前，建议先确认 BearPi 的数据能进入后端。

当前硬件数据上报链路是：

```text
BearPi
  -> 串口 COM10
  -> server.js
  -> HTTP POST
  -> http://192.168.20.143:8080/api/iotda/report
```

后端已兼容 IoTDA 风格的上报数据。

### 1. 用 Postman 模拟上报

请求：

```http
POST http://localhost:8080/api/iotda/report
Content-Type: application/json
```

Body：

```json
{
  "deviceId": "bearpi_001",
  "services": [
    {
      "service_id": "Agriculture",
      "properties": {
        "Temperature": 28,
        "Humidity": 58,
        "Luminance": 493,
        "LightStatus": "OFF",
        "MotorStatus": "OFF"
      }
    }
  ]
}
```

期望返回：

```json
{
  "code": 200,
  "message": "hardware telemetry saved",
  "data": {
    "telemetryId": 1
  }
}
```

说明：

后端会把 `bearpi_001` 映射为数据库里的真实设备编号：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

### 2. 查询最新遥测数据

请求：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

期望能看到类似字段：

```json
{
  "airTemperature": 28,
  "airHumidity": 58,
  "illuminance": 493,
  "pumpStatus": "OFF",
  "lightStatus": "OFF"
}
```

如果这里能查到数据，说明设备数据上报到后端的链路基本正常。

## 四、登录获取 JWT Token

控制接口 `/api/control/**` 需要登录。

请求：

```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

Body 示例：

```json
{
  "username": "admin",
  "password": "123456"
}
```

返回中复制 token。

后续控制接口都需要加请求头：

```http
Authorization: Bearer 你的token
```

## 五、测试打开水泵

### 1. 发送 PUMP_ON 命令

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer 你的token
```

期望返回类似：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "commandNo": "CMD20260704140554471",
    "deviceCode": "6a44b8fdcbb0cf6bb96ad1a1_bearpi_001",
    "commandType": "PUMP_ON",
    "commandValue": "ON",
    "status": "SENT"
  }
}
```

其中 `commandNo` 要复制下来，后面查询命令状态会用到。

### 2. 后端实际发布的 MQTT 内容

后端会向 IoTDA 发布：

Topic：

```text
$oc/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/sys/commands/request_id=CMD20260704140554471
```

Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

BearPi 收到后应该打开水泵。

### 3. 查询命令状态

请求：

```http
GET http://localhost:8080/api/control/commands/CMD20260704140554471
Authorization: Bearer 你的token
```

可能返回的状态含义：

| status | 含义 |
|---|---|
| SENT | 后端已经成功发布 MQTT 命令 |
| SUCCESS | 后端收到了设备执行成功回复 |
| FAILED | 后端发布失败或业务处理失败 |
| TIMEOUT | 后端发出命令后 30 秒内没有收到回复 |

注意：

`SENT` 只代表后端已经把命令发给 MQTT Broker，不代表 BearPi 一定已经执行。

如果当前 IoTDA 没有把命令执行结果回传给后端，那么命令可能会从 `SENT` 变成 `TIMEOUT`。这种情况下不一定代表 BearPi 没执行，需要继续看设备状态上报。

### 4. 确认 BearPi 是否真的打开水泵

推荐用两个方式确认：

第一，看真实硬件水泵是否打开。

第二，看下一次遥测上报里的状态：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

如果返回中出现：

```json
{
  "pumpStatus": "ON"
}
```

说明 BearPi 已经执行了水泵打开动作，并且状态已经上报到后端。

## 六、测试关闭水泵

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_OFF&commandValue=OFF
Authorization: Bearer 你的token
```

后端发布的 MQTT Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

验证方式：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

期望：

```json
{
  "pumpStatus": "OFF"
}
```

## 七、测试打开补光灯

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
Authorization: Bearer 你的token
```

后端发布的 MQTT Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

验证：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

期望：

```json
{
  "lightStatus": "ON"
}
```

## 八、测试关闭补光灯

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_OFF&commandValue=OFF
Authorization: Bearer 你的token
```

后端发布的 MQTT Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "OFF"
  }
}
```

验证：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

期望：

```json
{
  "lightStatus": "OFF"
}
```

## 九、数据库检查

### 1. 检查控制命令表

```sql
SELECT
  id,
  command_no,
  device_code,
  command_type,
  command_value,
  status,
  sent_at,
  ack_at,
  error_message,
  created_at,
  updated_at
FROM control_command
ORDER BY id DESC
LIMIT 10;
```

重点看：

```text
command_type
command_value
status
sent_at
ack_at
error_message
```

如果 `status = SENT`，说明后端已经发布 MQTT。

如果 `status = SUCCESS`，说明后端收到了设备成功回复。

如果 `status = TIMEOUT`，说明后端没有在 30 秒内收到回复。

### 2. 检查遥测数据表

```sql
SELECT
  id,
  plot_id,
  device_id,
  air_temperature,
  air_humidity,
  illuminance,
  pump_status,
  light_status,
  collected_at
FROM telemetry_data
ORDER BY id DESC
LIMIT 10;
```

重点看：

```text
pump_status
light_status
collected_at
```

判断硬件最终是否执行成功，以最新遥测中的 `pump_status`、`light_status` 为准。

## 十、常见问题排查

### 1. 命令返回 FAILED

可能原因：

```text
MQTT Broker 地址错误
IoTDA client-id 错误
IoTDA username/password 错误
网络无法连接 IoTDA
设备编号 deviceCode 不存在
```

优先检查：

```text
application.yml 里的 mqtt 配置
后端启动日志中的 MQTT 连接错误
device 表里是否有 BearPi 设备
```

### 2. 命令返回 SENT，但 BearPi 没反应

可能原因：

```text
后端仍然连接的是本地 EMQX，不是 IoTDA
BearPi 没有在线
IoTDA Topic 不匹配
IoTDA Profile 里的 service_id / command_name / paras 不匹配
设备编号 deviceCode 和 IoTDA 设备 ID 不一致
```

重点检查后端实际发布的 Topic 是否为：

```text
$oc/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/sys/commands/request_id={commandNo}
```

重点检查 Payload 是否为：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

### 3. 命令从 SENT 变成 TIMEOUT

这通常表示：

```text
后端发出了 MQTT 命令，但是没有收到设备执行结果回复。
```

这不一定代表 BearPi 没执行。

如果 BearPi 实际动作已经发生，并且后续遥测上报里 `MotorStatus` 或 `LightStatus` 已经变化，那么可以认为硬件执行成功。

后续如果要让命令状态稳定变成 `SUCCESS`，需要设备端或 IoTDA 规则把命令执行结果回传给后端。

### 4. 遥测数据没有变化

可能原因：

```text
server.js 没有运行
BearPi 串口 COM10 没有连接成功
server.js 请求的后端 IP 不对
后端没有放行 /api/iotda/**
IoTDA 规则引擎没有转发数据
```

当前硬件数据上报地址应为：

```text
http://192.168.20.143:8080/api/iotda/report
```

如果电脑 IP 变化，需要同步修改 server.js 或 IoTDA 转发地址。

### 5. Postman 请求 `/api/iotda/report` 返回 401

正常情况下，下面这个接口已经在后端放行，不需要 JWT：

```http
POST http://localhost:8080/api/iotda/report
```

如果 Postman 返回：

```text
401 Unauthorized
```

优先按下面顺序排查：

1. 停止当前正在运行的后端。
2. 重新运行当前项目里的 `SmartAgricultureApplication`。
3. 确认 `SecurityConfig.java` 里有：

```java
.requestMatchers("/api/iotda/**").permitAll()
```

4. Postman 的 Authorization 选择 `No Auth`。
5. Headers 里删除旧的 `Authorization` 请求头。
6. 重新发送请求。

如果重新启动当前项目后仍然是 401，说明 8080 端口上可能跑的不是当前这个项目。需要检查当前占用 8080 的 Java 进程，或者换一个端口启动当前后端。

## 十一、完整成功标准

一次完整联调成功，需要同时满足下面几项：

1. 后端可以正常启动。
2. `/api/health` 返回成功。
3. `/api/iotda/report` 能写入 BearPi 遥测数据。
4. `/api/control/send` 能返回 `SENT` 或 `SUCCESS`。
5. BearPi 能真实执行水泵或补光灯动作。
6. 后续遥测数据中的 `pumpStatus` 或 `lightStatus` 能反映最新状态。
7. `control_command` 表中能查到对应命令记录。
8. `telemetry_data` 表中能查到对应设备状态变化。

只要第 4 步是 `SENT`，第 5、6 步也成功，就说明后端到设备的主链路已经基本打通。

如果第 4 步最终变成 `TIMEOUT`，但第 5、6 步成功，说明当前缺的是“设备执行结果回传后端”的闭环，不是控制链路本身失败。

## 十二、当前不使用的备用方案

项目中仍保留了 `server.js` HTTP 控制适配的备用能力。

但当前正式推荐方案是：

```yaml
hardware:
  control:
    enabled: false
```

也就是不通过 `server.js` 控制硬件，而是通过后端 MQTT 直连 IoTDA 控制 BearPi。
