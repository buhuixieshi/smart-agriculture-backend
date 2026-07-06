# 当前设备遥测直写数据库与 MQTT 控制说明

本文档记录当前项目的实际联调方案。

当前后端由一人负责维护，设备端已调整为：

```text
遥测数据：设备端直接写 MySQL 数据库
控制命令：后端通过 MQTT 下发给设备端
命令回复：设备端通过 MQTT 回复后端
前端数据：前端通过后端接口查询数据库
```

## 一、当前实际架构

```text
BearPi / 设备端
  -> 直接写入 MySQL telemetry_data 表

Spring Boot 后端
  -> 查询 MySQL telemetry_data 表
  -> 给前端提供遥测接口
  -> 通过 MQTT 发布控制命令
  -> 接收设备端 MQTT 命令回复

前端
  -> 调用后端 HTTP API
```

也就是说，当前不是所有数据都经过后端。

遥测数据绕过后端，直接进入数据库。

控制命令仍然必须经过后端。

## 二、设备端不需要调用后端遥测接口

当前设备端遥测数据不走：

```http
POST /api/iotda/report
```

也不走：

```text
device/data/upload
```

而是由设备端直接写入数据库表：

```text
telemetry_data
```

后端只负责从数据库读取这些数据。

## 三、设备端需要写入的数据库字段

设备端写入 `telemetry_data` 时，建议至少保证这些字段正确：

```sql
plot_id
device_id
soil_moisture
air_temperature
air_humidity
illuminance
pump_status
light_status
collected_at
created_at
```

其中最关键的是：

```text
plot_id
device_id
pump_status
light_status
collected_at
```

原因：

- `plot_id` 用于前端按地块查询最新遥测。
- `device_id` 用于关联设备。
- `pump_status` 用于展示水泵状态。
- `light_status` 用于展示补光灯状态。
- `collected_at` 用于判断哪条数据是最新数据。

## 四、推荐写入示例

示例 SQL：

```sql
INSERT INTO telemetry_data (
  plot_id,
  device_id,
  soil_moisture,
  air_temperature,
  air_humidity,
  illuminance,
  pump_status,
  light_status,
  collected_at,
  created_at
) VALUES (
  1,
  7,
  NULL,
  28.00,
  58.00,
  493.00,
  'ON',
  'OFF',
  NOW(),
  NOW()
);
```

注意：

`plot_id` 和 `device_id` 必须和后端数据库中的真实地块、设备记录一致。

当前 BearPi 设备编号是：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

设备端如果只知道 `device_code`，需要先确认它对应的 `device.id` 和 `plot_id`。

## 五、后端查询遥测数据

后端会从数据库查询最新遥测。

常用接口：

```http
GET /api/telemetry/latest?plotId=1
```

或：

```http
GET /api/plots/{id}/latest
```

只要设备端正确写入 `telemetry_data`，这些接口就能查到最新数据。

## 六、控制命令仍然走 MQTT

设备端虽然直接写数据库，但控制命令仍然由后端通过 MQTT 下发。

后端发布控制命令到：

```text
device/control/cmd
```

设备端执行后回复：

```text
device/control/reply
```

## 七、后端控制接口

### 1. 打开水泵

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
```

### 2. 关闭水泵

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_OFF&commandValue=OFF
```

### 3. 打开补光灯

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
```

### 4. 关闭补光灯

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_OFF&commandValue=OFF
```

这些接口需要后端登录 token。

## 八、后端下发 MQTT Payload

设备端订阅：

```text
device/control/cmd
```

收到的 Payload 类似：

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

设备端可以优先按：

```text
commandType
commandValue
```

来判断执行动作。

也可以兼容：

```text
command_name
paras
```

## 九、设备端命令回复格式

设备端执行成功后，发布到：

```text
device/control/reply
```

Payload：

```json
{
  "commandId": "CMD20260705094002408",
  "status": "SUCCESS",
  "message": "pump on ok"
}
```

执行失败时：

```json
{
  "commandId": "CMD20260705094002408",
  "status": "FAILED",
  "message": "pump gpio error"
}
```

后端收到后会更新 `control_command` 表中的命令状态。

## 十、当前方案的影响

因为遥测数据绕过后端直接写数据库，所以这些后端逻辑不会自动触发：

```text
WebSocket 实时推送
告警规则检测
设备心跳更新时间
后端日志记录
数据格式校验
```

因此当前更适合：

```text
前端轮询后端接口查询最新遥测
```

例如前端每隔几秒调用：

```http
GET /api/telemetry/latest?plotId=1
```

如果前端依赖 WebSocket 实时推送，可能看不到设备端直接写库后的实时变化。

## 十一、当前测试重点

### 1. 测遥测查询

设备端直接写入一条 `telemetry_data` 后，调用：

```http
GET /api/telemetry/latest?plotId=1
```

期望能查到最新数据。

### 2. 测水泵控制

调用：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
```

后端返回 `SENT` 后，设备端应收到 MQTT 命令并打开水泵。

设备端回复 `SUCCESS` 后，查询：

```http
GET /api/control/commands/{commandNo}
```

期望状态变为：

```text
SUCCESS
```

### 3. 测补光灯控制

调用：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
```

设备端收到命令后打开补光灯，并回复 `SUCCESS`。

## 十二、后续可优化方向

当前方案能满足联调，但长期来看更推荐：

```text
设备端 -> MQTT/HTTP -> 后端 -> 数据库
```

这样后端可以统一处理：

```text
数据校验
告警判断
实时推送
设备在线状态
日志记录
权限控制
```

如果后续时间允许，可以把设备端直写数据库改为调用后端接口或发布 MQTT 遥测 Topic。

## 十三、当前结论

当前项目采用：

```text
遥测：设备端直写数据库
控制：后端 MQTT 下发
回复：设备端 MQTT 回复
展示：前端通过后端接口查数据库
```

设备端不需要连接后端遥测接口。

前端不直接访问数据库。

后端负责提供查询接口和控制接口。

