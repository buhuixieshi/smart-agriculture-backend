# 智慧农业后端项目合并说明

本文档用于给新的 AI 窗口快速了解当前后端项目状态，避免重复分析或误覆盖代码。

## 一、当前项目定位

当前项目是智慧农业后端 Spring Boot 项目，包名为：

```text
com.agriculture
```

技术栈：

```text
JDK 17
Spring Boot 3.5.16
Maven
MySQL 8
MyBatis-Plus
Spring Security + JWT
Spring Integration MQTT
WebSocket
```

当前项目已经不是单一后端版本，而是合并后的版本：

```text
以当前 BE-A 后端 Day5 代码为主体
选择性吸收另一个已成功连接硬件端后端的 MQTT 联通能力
```

注意：没有整包覆盖另一个后端，因为当前项目里的 Day5 CRUD、权限配置、DTO、Controller 更完整。

## 二、两个后端是怎么合并的

另一个后端压缩包路径曾是：

```text
C:\Users\86178\Downloads\smart agriculture backend (2).zip
```

解压后做了结构对比，发现另一个后端的优势主要在硬件 MQTT 联通部分：

```text
device/data/upload      遥测数据上报
device/heartbeat        设备心跳
device/control/cmd      后端下发控制命令
device/control/reply    硬件控制回执
```

本项目合并策略：

```text
保留当前项目 Day5 CRUD 和权限规则
保留当前项目 ControlController / ControlService 设计
保留当前项目 DTO / VO 结构
吸收另一个后端的 MQTT 入站处理能力
吸收另一个后端的命令回执处理逻辑
吸收设备心跳、遥测入库、离线检测能力
不合并另一个后端里依赖告警模块的部分
```

也就是说：当前项目不是两个后端文件的机械叠加，而是功能融合版。

## 三、当前已完成的主要功能

### 1. 基础项目骨架

已有目录：

```text
common
config
security
controller
entity
mapper
service
service.impl
dto
vo
aspect
websocket
mqtt
enums
task
```

项目可以通过：

```bash
mvn test
```

当前最近一次验证结果：

```text
BUILD SUCCESS
```

### 2. 用户认证与 JWT

已完成：

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
JWT 工具类
JWT 过滤器
BCrypt 密码加密
Spring Security 配置
```

### 3. 地块 Plot Day5 CRUD

已完成：

```text
GET    /api/plots
GET    /api/plots/{id}
POST   /api/plots
PUT    /api/plots/{id}
DELETE /api/plots/{id}
GET    /api/plots/{id}/latest
```

相关文件：

```text
src/main/java/com/agriculture/entity/Plot.java
src/main/java/com/agriculture/dto/PlotDTO.java
src/main/java/com/agriculture/mapper/PlotMapper.java
src/main/java/com/agriculture/service/PlotService.java
src/main/java/com/agriculture/service/impl/PlotServiceImpl.java
src/main/java/com/agriculture/controller/PlotController.java
```

关键点：

```text
Plot.java 使用字段 name，不是 plotName
数据库字段是 plot.name，不是 plot.plot_name
删除地块前会检查该地块下是否还有设备
```

### 4. 设备 Device Day5 CRUD

已完成：

```text
GET    /api/devices
GET    /api/devices?plotId=1
GET    /api/devices/{id}
POST   /api/devices
PUT    /api/devices/{id}
DELETE /api/devices/{id}
PUT    /api/devices/{id}/bind?plotId=1
PUT    /api/devices/{id}/unbind
```

相关文件：

```text
src/main/java/com/agriculture/entity/Device.java
src/main/java/com/agriculture/dto/DeviceDTO.java
src/main/java/com/agriculture/mapper/DeviceMapper.java
src/main/java/com/agriculture/service/DeviceService.java
src/main/java/com/agriculture/service/impl/DeviceServiceImpl.java
src/main/java/com/agriculture/controller/DeviceController.java
```

关键点：

```text
新增设备会检查 deviceCode 是否重复
绑定地块会检查 plotId 是否存在
心跳会更新 status=ONLINE 和 lastHeartbeat
90 秒无心跳会被离线检测任务改成 OFFLINE
```

### 5. 遥测数据

已完成：

```text
GET /api/plots/{id}/latest
GET /api/telemetry/latest?plotId=1
GET /api/telemetry/history
```

MQTT 收到 `device/data/upload` 后，会解析数据并写入：

```text
telemetry_data
```

并通过 WebSocket 推送给前端。

### 6. 控制命令 Day4 + MQTT 下发

已完成接口：

```text
POST /api/control/send
POST /api/control/irrigation
GET  /api/control/commands/{commandNo}
GET  /api/control/list?deviceCode=DEV-A003
```

控制命令状态流转：

```text
PENDING  刚写入数据库
SENT     已成功发布到 MQTT Broker
SUCCESS  硬件回执执行成功
FAILED   MQTT 下发失败或硬件回执失败
```

注意：

```text
SENT 只代表发到 MQTT Broker，不代表硬件执行成功
SUCCESS / FAILED 需要硬件端向 device/control/reply 发回执
```

### 7. MQTT 与硬件端联通能力

当前配置在：

```text
src/main/resources/application.yml
```

当前 MQTT 配置：

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

当前后端订阅：

```text
device/data/upload
device/heartbeat
device/control/reply
```

当前后端发布：

```text
device/control/cmd
```

关键文件：

```text
src/main/java/com/agriculture/mqtt/MqttProperties.java
src/main/java/com/agriculture/mqtt/MqttGateway.java
src/main/java/com/agriculture/mqtt/MqttBizMessageHandler.java
src/main/java/com/agriculture/config/MqttIntegrationConfig.java
src/main/java/com/agriculture/service/MqttCommandService.java
src/main/java/com/agriculture/service/impl/MqttCommandServiceImpl.java
```

硬件回执示例：

```json
{
  "commandNo": "CMD20260703143000123",
  "status": "SUCCESS",
  "message": "ok"
}
```

兼容字段：

```text
commandNo / commandId / command_no
status / result
message / msg / errorMessage / error_message
```

状态兼容：

```text
OK / SUCCESS / SUCCEED / TRUE     -> SUCCESS
FAIL / FAILED / ERROR / FALSE     -> FAILED
```

### 8. WebSocket

已完成：

```text
ws://localhost:8080/ws/realtime
```

用于推送遥测数据。

相关文件：

```text
src/main/java/com/agriculture/config/WebSocketConfig.java
src/main/java/com/agriculture/websocket/RealtimeWebSocketHandler.java
src/main/java/com/agriculture/service/RealtimePushService.java
src/main/java/com/agriculture/service/impl/RealtimePushServiceImpl.java
```

## 四、权限规则现状

当前 SecurityConfig 规则大意：

```text
OPTIONS 全部放行
/api/health 放行
/api/auth/register 放行
/api/auth/login 放行
/api/auth/me 需要登录

GET /api/plots/** 放行
GET /api/devices/** 放行
/api/plots/** 其他方法需要登录
/api/devices/** 其他方法需要登录

/api/telemetry/** 放行
/api/irrigation/** 放行
/api/mqtt/** 放行
/api/control/** 需要登录
```

也就是说：

```text
查询地块和设备不需要 token
新增、修改、删除、绑定、解绑需要 token
控制接口需要 token
```

## 五、数据库与运行环境注意事项

当前项目没有采用另一个后端的 MySQL 端口。

当前 `application.yml` 保持：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3309/smart_agriculture
    username: root
    password: 123456
```

另一个后端曾使用：

```text
3306
```

不要随便覆盖为 3306，除非确认本机 MySQL 服务就是 3306。

## 六、不要随便覆盖的地方

其他 AI 窗口接手时，请特别注意：

```text
不要用另一个后端的旧 ControlController 覆盖当前 ControlController
不要用另一个后端的旧 SecurityConfig 覆盖当前 SecurityConfig
不要删掉 PlotDTO / DeviceDTO / CommandVO / IrrigationControlDTO
不要删掉 /api/control/irrigation 和 /api/control/commands/{commandNo}
不要删掉 device/control/reply 的订阅
不要把 Plot.name 改成 plotName
不要把数据库端口随便从 3309 改成 3306
```

## 七、目前已知的非致命警告

运行 `mvn test` 时会看到 MyBatis mapper 重复扫描 WARN，例如：

```text
Skipping MapperFactoryBean ... Bean already defined with the same name
No MyBatis mapper was found in '[com.agriculture.mapper]' package
```

目前不影响：

```text
编译
Spring 上下文启动
mvn test
```

后续可以再单独优化 MapperScan / MybatisPlusConfig，暂时不建议为此大改。

## 八、下一步建议

建议后续优先做：

```text
1. 启动 EMQX
2. 启动后端
3. 用硬件端或 MQTTX 发布 heartbeat
4. 检查 device 表 status 是否变 ONLINE
5. 用前端调用 /api/control/irrigation
6. 检查 device/control/cmd 是否收到控制命令
7. 硬件端向 device/control/reply 回 SUCCESS
8. 检查 control_command 状态是否从 SENT 变 SUCCESS
```

这套流程通过后，可以认为当前合并版已经保留了另一个后端的硬件联通能力，同时也保留了当前项目的 Day5 CRUD 能力。
