# IoTDA REST 正式下发测试说明

> 当前已不采用本方案。项目已改回“后端 MQTT 发布命令，设备端适配后端 Topic/Payload”的方案。新的测试与对接说明请看：`设备端适配后端MQTT控制方案说明.md`。

本文档对应当前后端最新方案：

```text
前端 / Postman
  -> 后端 /api/control/send
  -> 华为云 IoTDA 应用侧 REST API
  -> BearPi
  -> 水泵 / 补光灯执行
```

设备端已经确认：

```text
IoTDA 控制台可以正常控制 BearPi；
BearPi 已支持 Agriculture_Control_Motor / Agriculture_Control_Light；
后端不能继续直接 MQTT publish 到 $oc/devices/.../sys/commands；
后端需要调用 IoTDA 应用侧 REST API。
```

当前后端已经改为使用华为云官方 Java SDK 调用 IoTDA REST 命令下发。

## 一、后端配置

打开：

```text
src/main/resources/application.yml
```

当前应有：

```yaml
hardware:
  control:
    enabled: false

iotda:
  enabled: true
  endpoint: iotda.cn-north-4.myhuaweicloud.com
  project-id: 23c92537a01e416ba4e24f3f24380a8b
  device-id: 6a44b8fdcbb0cf6bb96ad1a1_bearpi_001
  ak: ${IOTDA_AK:}
  sk: ${IOTDA_SK:}
  token: ${IOTDA_TOKEN:}
  timeout-seconds: 10
```

说明：

- `hardware.control.enabled=false`：不走本地 `server.js` 串口控制。
- `iotda.enabled=true`：启用 IoTDA REST API 下发命令。
- `ak`、`sk` 默认从环境变量读取，避免直接写死到代码里。

## 二、设置 AK/SK

### 方式一：PowerShell 启动后端

在启动后端的同一个 PowerShell 窗口里执行：

```powershell
$env:IOTDA_AK="设备端提供的AK"
$env:IOTDA_SK="设备端提供的SK"
mvn spring-boot:run
```

注意：

必须在同一个 PowerShell 窗口里启动后端，否则环境变量不会生效。

### 方式二：IDEA 启动后端

如果使用 IDEA 启动：

```text
Run/Debug Configurations
  -> SmartAgricultureApplication
  -> Environment variables
```

添加：

```text
IOTDA_AK=设备端提供的AK
IOTDA_SK=设备端提供的SK
```

然后重新启动后端。

### 方式三：临时写入 application.yml

如果只是本地临时测试，也可以把：

```yaml
ak: ${IOTDA_AK:}
sk: ${IOTDA_SK:}
```

临时改成：

```yaml
ak: 设备端提供的AK
sk: 设备端提供的SK
```

注意：

这种方式测试完不要提交到公共仓库。

## 三、启动后端

启动当前项目：

```bash
mvn spring-boot:run
```

或者在 IDEA 中运行：

```text
SmartAgricultureApplication
```

健康检查：

```http
GET http://localhost:8080/api/health
```

期望：

```json
{
  "code": 200,
  "message": "success"
}
```

## 四、登录获取 Token

控制接口仍然需要后端 JWT。

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

复制返回的 token。

后续请求带：

```http
Authorization: Bearer 你的后端JWT
```

## 五、测试打开水泵

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
Authorization: Bearer 你的后端JWT
```

后端会调用 IoTDA REST API：

```http
POST https://iotda.cn-north-4.myhuaweicloud.com/v5/iot/23c92537a01e416ba4e24f3f24380a8b/devices/6a44b8fdcbb0cf6bb96ad1a1_bearpi_001/commands
```

请求体：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "ON"
  }
}
```

如果 IoTDA 返回 `result_code=0`，后端命令状态会直接变成：

```json
{
  "status": "SUCCESS"
}
```

并且 `ackAt` 会有时间。

## 六、测试关闭水泵

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_OFF&commandValue=OFF
Authorization: Bearer 你的后端JWT
```

后端发送给 IoTDA 的命令体：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Motor",
  "paras": {
    "Motor": "OFF"
  }
}
```

## 七、测试打开补光灯

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_ON&commandValue=ON
Authorization: Bearer 你的后端JWT
```

后端发送给 IoTDA 的命令体：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "ON"
  }
}
```

## 八、测试关闭补光灯

请求：

```http
POST http://localhost:8080/api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=LIGHT_OFF&commandValue=OFF
Authorization: Bearer 你的后端JWT
```

后端发送给 IoTDA 的命令体：

```json
{
  "service_id": "Agriculture",
  "command_name": "Agriculture_Control_Light",
  "paras": {
    "Light": "OFF"
  }
}
```

## 九、查询命令状态

请求：

```http
GET http://localhost:8080/api/control/commands/{commandNo}
Authorization: Bearer 你的后端JWT
```

重点看：

```json
{
  "status": "SUCCESS",
  "ackAt": "2026-07-04T16:xx:xx",
  "errorMessage": "IoTDA command executed successfully, command_id=xxx"
}
```

如果是 `SUCCESS`，说明 IoTDA REST API 已经返回成功。

如果是 `FAILED`，看 `errorMessage`：

- `iotda.ak and iotda.sk are required`：没有设置 AK/SK。
- `IoTDA HTTP 401`：AK/SK 或 Token 不正确。
- `IoTDA HTTP 403`：权限不足或项目不匹配。
- `IoTDA HTTP 404`：project-id、device-id 或 endpoint 不匹配。
- `IoTDA SDK command failed`：IoTDA 官方 SDK 请求失败，继续看后面的 HTTP 状态码或错误信息。

## 十、确认硬件真实执行

命令返回 `SUCCESS` 后，还要看真实硬件和遥测。

查询最新遥测：

```http
GET http://localhost:8080/api/telemetry/latest?plotId=1
```

打开水泵后期望：

```json
{
  "pumpStatus": "ON"
}
```

关闭水泵后期望：

```json
{
  "pumpStatus": "OFF"
}
```

打开补光灯后期望：

```json
{
  "lightStatus": "ON"
}
```

关闭补光灯后期望：

```json
{
  "lightStatus": "OFF"
}
```

## 十一、数据库检查

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
  error_message
FROM control_command
ORDER BY id DESC
LIMIT 10;
```

成功时应看到：

```text
status = SUCCESS
sent_at 有值
ack_at 有值
error_message 包含 IoTDA command executed successfully
```

## 十二、成功标准

完整成功需要同时满足：

1. 后端 `/api/control/send` 返回 `code=200`。
2. 命令状态为 `SUCCESS`。
3. BearPi 真实水泵/补光灯动作发生。
4. 后续遥测状态同步变化。
5. `control_command` 表里有命令记录，且 `ack_at` 有值。
