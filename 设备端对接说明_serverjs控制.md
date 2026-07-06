# 设备端对接说明：后端已改为调用 server.js 控制 BearPi

日期：2026-07-04

## 1. 当前对接结论

当前真实硬件链路为：

```text
BearPi -> 串口 COM10 -> server.js
```

因此后端控制不再优先走 MQTT `device/control/cmd`，而是适配设备端已经提供的 HTTP 控制接口：

```text
POST http://192.168.20.190:3000/api/control
```

后端仍然保留 MQTT 控制代码作为备用，但默认配置已经切换为 HTTP 调用 `server.js`。

## 2. 后端配置

后端配置文件：

```text
src/main/resources/application.yml
```

当前配置：

```yaml
hardware:
  control:
    enabled: true
    base-url: http://192.168.20.190:3000
    device: bearpi_001
```

含义：

```text
enabled=true 表示后端控制命令会调用 server.js。
base-url 是成员6电脑上 server.js 的地址。
device 是 server.js 识别的设备标识。
```

如果成员6电脑 IP 变化，只需要改：

```yaml
hardware.control.base-url
```

## 3. 后端调用 server.js 的请求格式

当前后端调用：

```http
POST /api/control
Content-Type: application/json
```

### 3.1 打开水泵

前端或 Postman 调后端：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
```

后端会转发给 server.js：

```json
{
  "device": "bearpi_001",
  "type": "motor",
  "action": "ON"
}
```

### 3.2 关闭水泵

后端收到：

```text
commandType=PUMP_OFF
commandValue=OFF
```

会转发给 server.js：

```json
{
  "device": "bearpi_001",
  "type": "motor",
  "action": "OFF"
}
```

### 3.3 打开补光灯

后端收到：

```text
commandType=LIGHT_ON
commandValue=ON
```

会转发给 server.js：

```json
{
  "device": "bearpi_001",
  "type": "light",
  "action": "ON"
}
```

### 3.4 关闭补光灯

后端收到：

```text
commandType=LIGHT_OFF
commandValue=OFF
```

会转发给 server.js：

```json
{
  "device": "bearpi_001",
  "type": "light",
  "action": "OFF"
}
```

## 4. server.js 返回要求

后端按设备端说明，认为下面响应是成功：

```json
{
  "code": 200,
  "msg": "ok"
}
```

后端处理逻辑：

```text
code = 200 -> control_command.status = SUCCESS
ackAt = 当前时间
errorMessage = msg
```

如果返回非 200，或请求失败：

```text
control_command.status = FAILED
errorMessage = 失败原因
```

后端请求超时设置：

```text
连接超时：3 秒
读取超时：5 秒
```

所以如果 server.js 没启动、IP 不通、接口无响应，后端不会长时间卡住。

## 5. 遥测上报兼容

设备端当前上报链路：

```text
BearPi -> 串口 COM10 -> server.js -> HTTP POST -> 后端 /api/iotda/report
```

后端已经兼容：

```json
{
  "device_code": "bearpi_001",
  "air_temperature": 28.02,
  "air_humidity": 58.64,
  "illuminance": 493.33,
  "pump_status": "OFF",
  "light_status": "ON"
}
```

其中：

```text
device_code=bearpi_001
```

会被后端映射为数据库中的真实设备码：

```text
6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
```

## 6. 设备端需要确认

设备端只需要保证：

```text
1. server.js 运行在 http://192.168.20.190:3000。
2. POST /api/control 接收 JSON：device/type/action。
3. type=motor 可以控制水泵。
4. type=light 可以控制补光灯。
5. 成功时返回 {"code":200,"msg":"ok"}。
6. 失败时返回非 200 code 或明确错误 msg。
```

如果 IP 改了，请同步给后端修改 `hardware.control.base-url`。
