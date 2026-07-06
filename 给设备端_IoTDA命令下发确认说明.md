# 给设备端：IoTDA 命令下发确认说明

本文档用于请设备端协助确认：当前后端已经发出控制命令，但 BearPi 水泵没有打开，是否需要在 IoTDA 或设备端处理后端下发的命令。

## 一、当前现象

后端调用控制接口后，可以生成控制命令记录：

```http
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
```

后端返回过 `SENT`，说明后端已经执行了命令发送逻辑。

但是 30 秒后查询命令状态：

```http
GET /api/control/commands/{commandNo}
```

返回：

```json
{
  "status": "TIMEOUT",
  "errorMessage": "Command reply timeout after 30 seconds.",
  "ackAt": null
}
```

同时现场确认水泵没有打开。

所以目前判断：

```text
后端接口和命令记录生成基本正常；
但 IoTDA 到 BearPi 的命令下发/设备解析/执行回复链路还没有打通。
```

## 二、当前后端实际发送的命令格式

### 1. 水泵打开

后端控制水泵打开时，发送的业务命令是：

```text
commandType = PUMP_ON
commandValue = ON
```

后端转换成 IoTDA 命令后，Topic 为：

```text
$oc/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/sys/commands/request_id={commandNo}
```

Payload 为：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

### 2. 水泵关闭

Topic：

```text
$oc/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/sys/commands/request_id={commandNo}
```

Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

### 3. 补光灯打开

Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

### 4. 补光灯关闭

Payload：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "OFF"
  }
}
```

## 三、请设备端重点确认的问题

### 1. IoTDA 产品模型是否匹配

请确认华为 IoTDA 产品模型里是否存在以下配置。

服务 ID：

```text
Agriculture
```

水泵命令名：

```text
Agriculture_Control_Motor
```

水泵参数名：

```text
Motor
```

水泵参数值：

```text
ON
OFF
```

补光灯命令名：

```text
Agriculture_Control_Light
```

补光灯参数名：

```text
Light
```

补光灯参数值：

```text
ON
OFF
```

只要其中任意一个字段和设备端/IoTDA 产品模型不一致，BearPi 就可能无法识别后端命令。

### 2. IoTDA 控制台能否直接控制 BearPi

请先绕过后端，直接在 IoTDA 控制台对 BearPi 下发命令。

测试水泵打开：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

测试水泵关闭：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

请确认：

```text
IoTDA 控制台下发后，BearPi 水泵是否会真实打开/关闭？
```

判断方式：

- 如果 IoTDA 控制台下发也无法打开水泵，说明问题在 IoTDA 产品模型、命令格式或 BearPi 设备端解析执行逻辑。
- 如果 IoTDA 控制台下发可以打开水泵，说明 BearPi 与 IoTDA 本身是通的，后端可能需要改成 IoTDA 官方应用侧下发命令 API，而不是直接 MQTT publish。

### 3. BearPi 是否能收到 IoTDA 命令

请设备端查看 BearPi 串口日志或调试日志，确认是否能看到 IoTDA 下发的命令。

重点确认是否能看到类似字段：

```text
service_id = Agriculture
command_name = Agriculture_Control_Motor
Motor = ON
```

如果 BearPi 日志里完全没有收到命令，说明问题在：

```text
IoTDA 没有把命令发到设备
或设备没有正确订阅/监听 IoTDA 命令通道
```

如果 BearPi 收到了命令但水泵没动作，说明问题在：

```text
设备端命令解析或 GPIO/继电器控制逻辑
```

### 4. IoTDA 是否允许后端通过 MQTT Topic 直接下发命令

当前后端是向下面这个 Topic 发布消息：

```text
$oc/devices/{deviceId}/sys/commands/request_id={commandNo}
```

请设备端确认：

```text
华为 IoTDA 是否允许应用侧后端直接 MQTT publish 到这个 Topic 来下发设备命令？
```

如果 IoTDA 不支持这种方式，而是要求后端调用官方应用侧 API，那么需要告诉后端改成类似：

```text
应用侧 REST API 下发设备命令
```

也就是后端应调用 IoTDA 的“创建设备命令 / 下发设备命令”接口，而不是普通 MQTT publish。

## 四、请设备端确认命令回复机制

当前后端命令状态变成 `TIMEOUT` 的直接原因是：

```text
后端 30 秒内没有收到设备命令执行回复。
```

后端之前约定的普通 MQTT 回复 Topic 是：

```text
device/control/reply
```

回复格式为：

```json
{
  "commandId": "{commandNo}",
  "status": "SUCCESS",
  "message": "pump on ok"
}
```

但是如果当前走的是 IoTDA 正式命令通道，请设备端确认 IoTDA 的命令执行结果如何回传：

```text
BearPi 是否需要向 IoTDA 回复命令执行结果？
IoTDA 是否会把执行结果转发给后端？
后端应该监听哪个 Topic 或哪个 HTTP 回调？
返回字段里是否包含后端的 commandNo/request_id？
```

如果没有回传闭环，即使 BearPi 执行成功，后端也只能先显示：

```text
SENT
```

然后 30 秒后变成：

```text
TIMEOUT
```

## 五、请设备端返回的确认结果

请设备端按下面格式回复后端，方便定位问题。

### 1. IoTDA 控制台下发测试结果

```text
IoTDA 控制台下发 Agriculture_Control_Motor / Motor=ON：
水泵是否打开：是 / 否

IoTDA 控制台下发 Agriculture_Control_Motor / Motor=OFF：
水泵是否关闭：是 / 否
```

### 2. BearPi 串口日志结果

```text
BearPi 是否收到命令：是 / 否
收到的 service_id：
收到的 command_name：
收到的参数名：
收到的参数值：
```

### 3. IoTDA 下发方式确认

```text
IoTDA 是否允许后端直接 MQTT publish 到 $oc/devices/{deviceId}/sys/commands/request_id={requestId}：
是 / 否 / 不确定

如果不允许，后端应该使用的下发方式：
IoTDA 应用侧 REST API / 其他方式
```

### 4. 命令回复方式确认

```text
BearPi 执行命令后是否会回复 IoTDA：是 / 否
IoTDA 是否会转发命令执行结果给后端：是 / 否
后端应该接收回复的 Topic 或 HTTP 地址：
回复里是否包含 commandNo/request_id：
```

## 六、当前后端判断

从目前测试结果看：

```text
后端能生成命令记录；
后端能进入发送流程；
但水泵没有打开；
命令最终 TIMEOUT；
说明 IoTDA 到 BearPi 的命令下发、设备解析或命令回复链路需要设备端继续确认。
```

最关键的第一步是：

```text
请设备端先在 IoTDA 控制台直接下发同样的 Motor=ON 命令，看 BearPi 水泵是否能打开。
```

这个测试可以快速区分：

```text
控制台也打不开：设备端/IoTDA 产品模型问题
控制台能打开：后端下发方式需要改为 IoTDA 官方应用侧命令 API
```

