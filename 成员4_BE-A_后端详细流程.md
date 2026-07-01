# 智慧农业系统：成员4（后端A / BE-A）详细开发流程

> 适用角色：成员4，后端A，业务与数据方向  
> 技术栈建议：Spring Boot 3.5.16 + JDK 17 + Maven + MySQL 8.0 + MyBatis-Plus + Spring Security + JWT + WebSocket  
> 项目主线：前端页面请求 → 后端 REST API → 业务校验 → MySQL 数据库 → MQTT/硬件联动 → WebSocket 实时反馈

---

## 1. 你的角色定位

你不是单独完成整个后端，也不是只写几个接口。你的职责是把智慧农业系统中的“业务数据层”和“前端接口层”做出来。

你主要负责：

1. Spring Boot 后端项目骨架搭建。
2. MySQL 数据库表设计和建表。
3. MyBatis-Plus 实体、Mapper、Service 编写。
4. 用户注册、登录、JWT 鉴权。
5. 地块管理接口。
6. 设备管理接口。
7. 实时数据查询接口。
8. 历史趋势查询接口。
9. 灌溉控制接口入口。
10. 补光控制接口入口。
11. 告警查询、确认、关闭接口。
12. 操作日志 AOP 记录与查询。
13. 灌溉统计、用水统计接口。
14. 害虫识别记录和防治建议接口。
15. 与成员5对接 MQTT 命令下发、命令回执、自动规则、设备心跳。

成员5主要负责：

1. MQTT Broker 连接。
2. 遥测 Topic 订阅。
3. 心跳 Topic 订阅。
4. 命令 Topic 发布。
5. 命令回复 Topic 处理。
6. 自动灌溉规则。
7. 自动补光规则。
8. 阈值策略执行。
9. 告警自动生成。
10. 设备离线判定。

你们两个人共用一个后端项目，不要各写一个项目。

---

## 2. 后端整体架构

### 2.1 调用链路

```text
前端页面
  ↓ HTTP / WebSocket
Controller 控制层
  ↓
Service 业务层
  ↓
Mapper 数据访问层
  ↓
MySQL 数据库
```

控制硬件时：

```text
前端点击按钮
  ↓
ControlController
  ↓
ControlService 校验并生成命令
  ↓
control_command 表写入命令记录
  ↓
调用 MqttCommandService
  ↓
成员5发布 MQTT 命令
  ↓
硬件执行
  ↓
硬件回复 MQTT
  ↓
成员5更新命令状态
  ↓
WebSocket 推送给前端
```

遥测数据链路：

```text
BearPi / 传感器
  ↓
MQTT 上报数据
  ↓
成员5订阅并解析
  ↓
写入 telemetry_data 表
  ↓
你的查询接口返回最新数据 / 历史数据
  ↓
前端展示地块总览、详情、历史趋势
```

---

## 3. 项目目录骨架

项目名建议：

```text
smart-agriculture-backend
```

完整目录建议：

```text
smart-agriculture-backend
├── pom.xml
├── README.md
├── docs
│   ├── 数据库设计DDL.sql
│   ├── REST_API接口文档.md
│   ├── MQTT协议规范.md
│   └── 后端启动说明.md
│
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com
│   │   │       └── agriculture
│   │   │           ├── SmartAgricultureApplication.java
│   │   │           │
│   │   │           ├── common
│   │   │           │   ├── Result.java
│   │   │           │   ├── PageResult.java
│   │   │           │   ├── BusinessException.java
│   │   │           │   └── GlobalExceptionHandler.java
│   │   │           │
│   │   │           ├── config
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── CorsConfig.java
│   │   │           │   ├── MybatisPlusConfig.java
│   │   │           │   └── WebSocketConfig.java
│   │   │           │
│   │   │           ├── security
│   │   │           │   ├── JwtUtil.java
│   │   │           │   ├── JwtAuthenticationFilter.java
│   │   │           │   └── LoginUser.java
│   │   │           │
│   │   │           ├── controller
│   │   │           │   ├── HealthController.java
│   │   │           │   ├── AuthController.java
│   │   │           │   ├── PlotController.java
│   │   │           │   ├── DeviceController.java
│   │   │           │   ├── TelemetryController.java
│   │   │           │   ├── ControlController.java
│   │   │           │   ├── AlarmController.java
│   │   │           │   ├── OperationLogController.java
│   │   │           │   ├── IrrigationStatsController.java
│   │   │           │   └── PestController.java
│   │   │           │
│   │   │           ├── entity
│   │   │           │   ├── User.java
│   │   │           │   ├── Plot.java
│   │   │           │   ├── Device.java
│   │   │           │   ├── TelemetryData.java
│   │   │           │   ├── ControlCommand.java
│   │   │           │   ├── Alarm.java
│   │   │           │   ├── OperationLog.java
│   │   │           │   ├── IrrigationRecord.java
│   │   │           │   ├── ThresholdStrategy.java
│   │   │           │   └── PestRecord.java
│   │   │           │
│   │   │           ├── mapper
│   │   │           │   ├── UserMapper.java
│   │   │           │   ├── PlotMapper.java
│   │   │           │   ├── DeviceMapper.java
│   │   │           │   ├── TelemetryDataMapper.java
│   │   │           │   ├── ControlCommandMapper.java
│   │   │           │   ├── AlarmMapper.java
│   │   │           │   ├── OperationLogMapper.java
│   │   │           │   ├── IrrigationRecordMapper.java
│   │   │           │   ├── ThresholdStrategyMapper.java
│   │   │           │   └── PestRecordMapper.java
│   │   │           │
│   │   │           ├── service
│   │   │           │   ├── AuthService.java
│   │   │           │   ├── PlotService.java
│   │   │           │   ├── DeviceService.java
│   │   │           │   ├── TelemetryService.java
│   │   │           │   ├── ControlService.java
│   │   │           │   ├── AlarmService.java
│   │   │           │   ├── OperationLogService.java
│   │   │           │   ├── IrrigationStatsService.java
│   │   │           │   ├── PestService.java
│   │   │           │   └── MqttCommandService.java
│   │   │           │
│   │   │           ├── service
│   │   │           │   └── impl
│   │   │           │       ├── AuthServiceImpl.java
│   │   │           │       ├── PlotServiceImpl.java
│   │   │           │       ├── DeviceServiceImpl.java
│   │   │           │       ├── TelemetryServiceImpl.java
│   │   │           │       ├── ControlServiceImpl.java
│   │   │           │       ├── AlarmServiceImpl.java
│   │   │           │       ├── OperationLogServiceImpl.java
│   │   │           │       ├── IrrigationStatsServiceImpl.java
│   │   │           │       ├── PestServiceImpl.java
│   │   │           │       └── MqttCommandServiceImpl.java
│   │   │           │
│   │   │           ├── dto
│   │   │           │   ├── LoginDTO.java
│   │   │           │   ├── RegisterDTO.java
│   │   │           │   ├── PlotDTO.java
│   │   │           │   ├── DeviceDTO.java
│   │   │           │   ├── IrrigationControlDTO.java
│   │   │           │   ├── LightingControlDTO.java
│   │   │           │   ├── AlarmHandleDTO.java
│   │   │           │   └── PestDetectDTO.java
│   │   │           │
│   │   │           ├── vo
│   │   │           │   ├── LoginVO.java
│   │   │           │   ├── UserVO.java
│   │   │           │   ├── PlotOverviewVO.java
│   │   │           │   ├── PlotLatestDataVO.java
│   │   │           │   ├── DeviceVO.java
│   │   │           │   ├── TelemetryHistoryVO.java
│   │   │           │   ├── CommandVO.java
│   │   │           │   ├── AlarmVO.java
│   │   │           │   ├── IrrigationStatsVO.java
│   │   │           │   └── PestDetectVO.java
│   │   │           │
│   │   │           ├── aspect
│   │   │           │   ├── LogOperation.java
│   │   │           │   └── OperationLogAspect.java
│   │   │           │
│   │   │           ├── websocket
│   │   │           │   ├── RealtimeWebSocketHandler.java
│   │   │           │   └── WebSocketMessageSender.java
│   │   │           │
│   │   │           ├── mqtt
│   │   │           │   ├── MqttTopicConstant.java
│   │   │           │   ├── MqttCommandPublisher.java
│   │   │           │   └── MqttMessageHandler.java
│   │   │           │
│   │   │           └── enums
│   │   │               ├── DeviceStatusEnum.java
│   │   │               ├── AlarmStatusEnum.java
│   │   │               ├── CommandStatusEnum.java
│   │   │               └── CommandTypeEnum.java
│   │   │
│   │   └── resources
│   │       ├── application.yml
│   │       ├── mapper
│   │       │   ├── TelemetryDataMapper.xml
│   │       │   ├── PlotMapper.xml
│   │       │   ├── AlarmMapper.xml
│   │       │   └── IrrigationRecordMapper.xml
│   │       └── sql
│   │           ├── schema.sql
│   │           └── data.sql
```

---

## 4. 环境配置

### 4.1 application.yml

路径：

```text
src/main/resources/application.yml
```

内容：

```yaml
spring:
  application:
    name: smart-agriculture-backend

  datasource:
    url: jdbc:mysql://localhost:3306/smart_agriculture?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456
    driver-class-name: com.mysql.cj.jdbc.Driver

server:
  port: 8080

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

jwt:
  secret: smart-agriculture-secret-key-change-this
  expire-hours: 24
```

如果连接队友数据库，把 `localhost` 换成队友 IP：

```yaml
spring:
  datasource:
    url: jdbc:mysql://队友IP:3306/smart_agriculture?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: 队友给你的用户名
    password: 队友给你的密码
```

---

## 5. Maven 依赖

`pom.xml` 里建议至少有这些依赖：

```xml
<dependencies>
    <!-- Web 接口 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- 参数校验 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- WebSocket -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- AOP 操作日志 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- MySQL -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- MyBatis-Plus -->
    <dependency>
        <groupId>com.baomidou</groupId>
        <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
        <version>3.5.12</version>
    </dependency>

    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.12.6</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.12.6</version>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 测试 -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

MQTT 依赖由成员5主导加入：

```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-mqtt</artifactId>
</dependency>
```

---

## 6. 数据库设计

### 6.1 创建数据库

```sql
CREATE DATABASE smart_agriculture
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
```

---

### 6.2 用户表 user

```sql
CREATE TABLE user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(50),
  role VARCHAR(20) DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

用途：登录、鉴权、操作日志记录操作人。

---

### 6.3 地块表 plot

```sql
CREATE TABLE plot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  crop_type VARCHAR(50),
  location VARCHAR(100),
  area DECIMAL(10,2),
  status VARCHAR(20) DEFAULT 'ONLINE',
  description VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

用途：地块总览、地块详情、历史趋势筛选、设备绑定。

---

### 6.4 设备表 device

```sql
CREATE TABLE device (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  device_code VARCHAR(50) NOT NULL UNIQUE,
  device_name VARCHAR(100) NOT NULL,
  device_type VARCHAR(50) NOT NULL,
  plot_id BIGINT,
  status VARCHAR(20) DEFAULT 'OFFLINE',
  last_heartbeat DATETIME,
  signal_strength INT,
  battery INT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

用途：设备列表、设备绑定、控制命令设备定位、设备在线状态展示。

设备类型建议：

```text
BEARPI_MAIN
SOIL_SENSOR
PUMP_CONTROLLER
LIGHT_CONTROLLER
CAMERA
```

---

### 6.5 遥测数据表 telemetry_data

```sql
CREATE TABLE telemetry_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL,
  device_id BIGINT,
  soil_moisture DECIMAL(5,2),
  air_temperature DECIMAL(5,2),
  air_humidity DECIMAL(5,2),
  illuminance DECIMAL(10,2),
  pump_status VARCHAR(20),
  light_status VARCHAR(20),
  collected_at DATETIME NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

用途：地块详情实时指标、历史趋势、灌溉建议、阈值判断。

---

### 6.6 控制命令表 control_command

```sql
CREATE TABLE control_command (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(64) NOT NULL UNIQUE,
  plot_id BIGINT NOT NULL,
  device_id BIGINT,
  command_type VARCHAR(30) NOT NULL,
  action VARCHAR(30) NOT NULL,
  source VARCHAR(20) NOT NULL,
  duration_minutes INT,
  brightness INT,
  status VARCHAR(20) DEFAULT 'PENDING',
  operator_id BIGINT,
  error_message VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  replied_at DATETIME
);
```

字段建议：

```text
command_type: IRRIGATION / LIGHTING
action: START / STOP
source: MANUAL / AUTO
status: PENDING / SENT / SUCCESS / FAILED / TIMEOUT
```

---

### 6.7 告警表 alarm

```sql
CREATE TABLE alarm (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT,
  device_id BIGINT,
  alarm_type VARCHAR(50) NOT NULL,
  level VARCHAR(20) NOT NULL,
  title VARCHAR(100),
  content VARCHAR(255),
  status VARCHAR(20) DEFAULT 'UNHANDLED',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  handled_at DATETIME,
  handler_id BIGINT
);
```

状态建议：

```text
UNHANDLED 未处理
ACKED 已确认
CLOSED 已关闭
RECOVERED 已恢复
```

---

### 6.8 操作日志表 operation_log

```sql
CREATE TABLE operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT,
  username VARCHAR(50),
  module VARCHAR(50),
  operation VARCHAR(100),
  method VARCHAR(10),
  request_uri VARCHAR(255),
  request_param TEXT,
  result VARCHAR(20),
  error_message TEXT,
  ip VARCHAR(50),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

用途：记录登录、控制设备、修改地块、修改设备、确认告警等操作。

---

### 6.9 灌溉记录表 irrigation_record

```sql
CREATE TABLE irrigation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL,
  command_id BIGINT,
  mode VARCHAR(20),
  start_time DATETIME,
  end_time DATETIME,
  duration_minutes INT,
  water_amount DECIMAL(10,2),
  before_moisture DECIMAL(5,2),
  after_moisture DECIMAL(5,2),
  trigger_reason VARCHAR(255),
  operator_id BIGINT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

用途：灌溉统计、用水量统计、答辩演示数据。

---

### 6.10 害虫识别记录表 pest_record

```sql
CREATE TABLE pest_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT,
  image_url VARCHAR(255),
  pest_name VARCHAR(100),
  confidence DECIMAL(5,2),
  risk_level VARCHAR(20),
  suggestion TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

用途：害虫识别结果保存、历史记录展示、处理建议展示。

---

## 7. 统一返回格式

### 7.1 Result.java

```java
package com.agriculture.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }

    public static <T> Result<T> unauthorized() {
        return new Result<>(401, "unauthorized", null);
    }
}
```

前端统一收到：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

---

## 8. 模块一：健康检查接口

### 8.1 目标

确认项目能正常启动。

### 8.2 接口

```http
GET /api/health
```

### 8.3 Controller

```java
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("backend ok");
    }
}
```

### 8.4 验收

访问：

```text
http://localhost:8080/api/health
```

返回：

```json
{
  "code": 200,
  "message": "success",
  "data": "backend ok"
}
```

---

## 9. 模块二：用户注册、登录、JWT 鉴权

### 9.1 你要做什么

1. 用户注册。
2. 用户登录。
3. 密码 BCrypt 加密。
4. 登录成功生成 JWT。
5. 其他业务接口需要携带 Token。
6. 无 Token 或 Token 错误返回 401。

### 9.2 接口清单

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

### 9.3 注册请求

```json
{
  "username": "admin",
  "password": "123456",
  "nickname": "管理员"
}
```

### 9.4 登录请求

```json
{
  "username": "admin",
  "password": "123456"
}
```

### 9.5 登录返回

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": {
    "id": 1,
    "username": "admin",
    "nickname": "管理员",
    "role": "ADMIN"
  }
}
```

### 9.6 实现步骤

1. 创建 `User` 实体。
2. 创建 `UserMapper`。
3. 创建 `RegisterDTO`、`LoginDTO`、`LoginVO`。
4. 写 `AuthController`。
5. 写 `AuthService`。
6. 写 `JwtUtil`。
7. 写 `JwtAuthenticationFilter`。
8. 写 `SecurityConfig`。
9. 放行 `/api/auth/login`、`/api/auth/register`、`/api/health`。
10. 其他接口统一拦截。

### 9.7 验收标准

1. 用户能注册成功。
2. 用户能登录成功。
3. 密码不是明文存储。
4. 登录后能拿到 token。
5. 不带 token 访问业务接口返回 401。
6. 带 token 访问业务接口正常。

---

## 10. 模块三：地块管理

### 10.1 你要做什么

地块是整个系统的核心对象，设备、遥测数据、告警、灌溉记录都要关联地块。

你要完成：

1. 地块列表。
2. 地块详情。
3. 新增地块。
4. 修改地块。
5. 删除地块。
6. 地块总览。
7. 查询某个地块最新遥测数据。

### 10.2 接口清单

```http
GET    /api/plots
GET    /api/plots/{id}
POST   /api/plots
PUT    /api/plots/{id}
DELETE /api/plots/{id}
GET    /api/plots/overview
GET    /api/plots/{id}/latest
```

### 10.3 地块总览返回示例

```json
[
  {
    "plotId": 1,
    "plotName": "一号温室 - 番茄区",
    "cropType": "番茄",
    "status": "ONLINE",
    "soilMoisture": 32,
    "airTemperature": 27,
    "airHumidity": 65,
    "illuminance": 850,
    "pumpStatus": "OFF",
    "lightStatus": "OFF",
    "alarmCount": 1,
    "lastUpdateTime": "2026-06-30 10:28:15"
  }
]
```

### 10.4 实现步骤

1. 创建 `Plot` 实体。
2. 创建 `PlotMapper extends BaseMapper<Plot>`。
3. 创建 `PlotDTO`。
4. 创建 `PlotOverviewVO`。
5. 创建 `PlotLatestDataVO`。
6. 创建 `PlotService`。
7. 创建 `PlotServiceImpl`。
8. 创建 `PlotController`。
9. `GET /api/plots` 调用 MyBatis-Plus 查询所有地块。
10. `GET /api/plots/overview` 查询所有地块，再为每个地块查最新遥测数据、未处理告警数量。
11. `GET /api/plots/{id}/latest` 查询 `telemetry_data` 最新一条。

### 10.5 核心 SQL

查询最新遥测数据：

```sql
SELECT *
FROM telemetry_data
WHERE plot_id = ?
ORDER BY collected_at DESC
LIMIT 1;
```

查询未处理告警数量：

```sql
SELECT COUNT(*)
FROM alarm
WHERE plot_id = ?
  AND status = 'UNHANDLED';
```

### 10.6 验收标准

1. Postman 调 `/api/plots` 能返回地块列表。
2. Postman 调 `/api/plots/overview` 能返回总览卡片数据。
3. 前端地块总览页面能显示地块名称、湿度、温度、设备状态。
4. 没有遥测数据时接口不报错，而是返回空值或提示“暂无数据”。

---

## 11. 模块四：设备管理

### 11.1 你要做什么

设备管理负责展示和维护设备信息，包括主控板、传感器、水泵、补光灯、摄像头等。

### 11.2 接口清单

```http
GET    /api/devices
GET    /api/devices/{id}
POST   /api/devices
PUT    /api/devices/{id}
DELETE /api/devices/{id}
POST   /api/devices/{id}/bind
POST   /api/devices/{id}/unbind
```

### 11.3 查询参数

```http
GET /api/devices?plotId=1&status=ONLINE&deviceType=PUMP_CONTROLLER
```

### 11.4 绑定请求

```json
{
  "plotId": 1
}
```

### 11.5 实现步骤

1. 创建 `Device` 实体。
2. 创建 `DeviceMapper`。
3. 创建 `DeviceDTO`。
4. 创建 `DeviceVO`。
5. 创建 `DeviceService`。
6. 创建 `DeviceController`。
7. 实现设备列表查询，支持按地块、状态、类型筛选。
8. 实现新增、修改、删除。
9. 实现绑定地块，把 `plot_id` 更新为指定地块。
10. 实现解绑地块，把 `plot_id` 置空。

### 11.6 你和成员5的边界

你负责：

```text
设备信息 CRUD
设备绑定/解绑地块
设备列表查询
设备状态展示接口
```

成员5负责：

```text
接收心跳
更新 last_heartbeat
心跳超时标记 OFFLINE
```

### 11.7 验收标准

1. 前端设备列表能显示设备编号、设备名称、类型、绑定地块、状态、最后心跳。
2. 可以新增设备。
3. 可以编辑设备。
4. 可以绑定地块。
5. 可以解绑地块。
6. 设备离线状态能被前端看到。

---

## 12. 模块五：遥测数据查询与历史趋势

### 12.1 你要做什么

你不用负责传感器读取，也不用负责 MQTT 接收。你负责从 `telemetry_data` 表里查数据给前端。

### 12.2 接口清单

```http
GET /api/telemetry/latest?plotId=1
GET /api/telemetry/history?plotId=1&startTime=2026-06-30 00:00:00&endTime=2026-06-30 23:59:59
GET /api/telemetry/page?plotId=1&page=1&pageSize=10
```

也可以保留：

```http
GET /api/plots/{id}/latest
```

### 12.3 历史趋势返回示例

```json
[
  {
    "time": "10:00",
    "soilMoisture": 36,
    "airTemperature": 26,
    "airHumidity": 64,
    "illuminance": 820,
    "pumpStatus": "OFF",
    "alarm": false
  },
  {
    "time": "10:05",
    "soilMoisture": 32,
    "airTemperature": 27,
    "airHumidity": 65,
    "illuminance": 850,
    "pumpStatus": "OFF",
    "alarm": true
  }
]
```

### 12.4 实现步骤

1. 创建 `TelemetryData` 实体。
2. 创建 `TelemetryDataMapper`。
3. 创建 `TelemetryHistoryVO`。
4. 创建 `TelemetryService`。
5. 创建 `TelemetryController`。
6. 写最新数据查询。
7. 写历史趋势查询。
8. 写分页明细查询。
9. 与前端约定时间格式：`yyyy-MM-dd HH:mm:ss`。

### 12.5 核心 SQL

历史查询：

```sql
SELECT *
FROM telemetry_data
WHERE plot_id = ?
  AND collected_at BETWEEN ? AND ?
ORDER BY collected_at ASC;
```

分页查询：

```sql
SELECT *
FROM telemetry_data
WHERE plot_id = ?
ORDER BY collected_at DESC
LIMIT ?, ?;
```

### 12.6 验收标准

1. 地块详情页能显示最新湿度、温度、空气湿度、光照。
2. 历史趋势页能显示折线图数据。
3. 数据明细表能分页展示。
4. 没数据时返回空数组，不要报错。

---

## 13. 模块六：灌溉控制接口

### 13.1 你要做什么

你负责接收前端的灌溉控制请求，并生成控制命令。真正通过 MQTT 发给硬件的部分，由成员5负责。

### 13.2 接口清单

```http
POST /api/control/irrigation
GET  /api/control/commands/{commandNo}
```

### 13.3 开启灌溉请求

```json
{
  "plotId": 1,
  "action": "START",
  "mode": "MANUAL",
  "durationMinutes": 15
}
```

### 13.4 停止灌溉请求

```json
{
  "plotId": 1,
  "action": "STOP",
  "mode": "MANUAL"
}
```

### 13.5 返回示例

```json
{
  "commandNo": "CMD202606301030001",
  "status": "SENT",
  "message": "灌溉命令已下发"
}
```

### 13.6 你的处理流程

```text
1. 校验用户是否登录。
2. 校验 plotId 是否存在。
3. 查找该地块绑定的水泵控制器设备。
4. 判断水泵设备是否在线。
5. 判断灌溉时长是否合法，例如不能超过 15 分钟。
6. 判断是否存在未完成的灌溉命令，避免重复点击。
7. 生成 commandNo。
8. 写入 control_command 表，状态为 PENDING。
9. 调用 MqttCommandService.publishIrrigationCommand(command)。
10. 如果发布成功，更新命令状态为 SENT。
11. 返回 commandNo 给前端。
```

### 13.7 和成员5约定接口

```java
public interface MqttCommandService {
    void publishIrrigationCommand(ControlCommand command);
    void publishLightingCommand(ControlCommand command);
}
```

你调用这个接口，成员5实现具体 MQTT 发布。

### 13.8 命令状态查询

```http
GET /api/control/commands/CMD202606301030001
```

返回：

```json
{
  "commandNo": "CMD202606301030001",
  "commandType": "IRRIGATION",
  "action": "START",
  "status": "SUCCESS",
  "createdAt": "2026-06-30 10:30:00",
  "repliedAt": "2026-06-30 10:30:02"
}
```

### 13.9 验收标准

1. 前端点击“手动开启灌溉”，后端能生成命令。
2. `control_command` 表有命令记录。
3. 成员5能拿到命令并发布 MQTT。
4. 硬件回复后命令状态能从 `SENT` 更新为 `SUCCESS`。
5. 前端能查询命令状态。
6. 设备离线时不允许下发命令。
7. 灌溉时长超过限制时不允许下发命令。

---

## 14. 模块七：补光控制接口

### 14.1 你要做什么

补光控制和灌溉控制类似。你负责接口入口、业务校验、命令记录，成员5负责 MQTT 下发。

### 14.2 接口

```http
POST /api/control/lighting
```

### 14.3 开启补光请求

```json
{
  "plotId": 1,
  "action": "START",
  "mode": "MANUAL",
  "durationMinutes": 120,
  "brightness": 75
}
```

### 14.4 关闭补光请求

```json
{
  "plotId": 1,
  "action": "STOP",
  "mode": "MANUAL"
}
```

### 14.5 安全校验

开启补光前要检查：

1. 地块是否存在。
2. 补光灯设备是否绑定。
3. 补光灯设备是否在线。
4. 亮度只能是 `25 / 50 / 75 / 100`。
5. 单次补光时长不能超过系统限制。
6. 单日补光总时长不能超过 4 小时。
7. 当前温度如果大于 40℃，拒绝开启补光。

### 14.6 返回示例

```json
{
  "commandNo": "CMD_LIGHT_202606301900001",
  "status": "SENT",
  "message": "补光命令已下发"
}
```

### 14.7 验收标准

1. 补光命令能写入 `control_command` 表。
2. 补光命令能交给成员5发布 MQTT。
3. 设备离线时拒绝。
4. 亮度非法时拒绝。
5. 温度过高时拒绝。
6. 超过单日补光时长时拒绝。

---

## 15. 模块八：告警管理

### 15.1 你要做什么

成员5负责根据规则自动生成告警，你负责给前端提供告警列表、详情、确认、关闭接口。

### 15.2 接口清单

```http
GET /api/alarms?plotId=1&status=UNHANDLED&page=1&pageSize=10
GET /api/alarms/{id}
PUT /api/alarms/{id}/ack
PUT /api/alarms/{id}/close
```

### 15.3 告警状态

```text
UNHANDLED：未处理
ACKED：已确认
CLOSED：已关闭
RECOVERED：已恢复
```

### 15.4 实现步骤

1. 创建 `Alarm` 实体。
2. 创建 `AlarmMapper`。
3. 创建 `AlarmVO`。
4. 创建 `AlarmService`。
5. 创建 `AlarmController`。
6. 实现分页查询。
7. 实现按地块筛选。
8. 实现按状态筛选。
9. 实现确认告警。
10. 实现关闭告警。
11. 确认和关闭时记录处理人和处理时间。

### 15.5 验收标准

1. 告警管理页面能显示告警列表。
2. 可以按状态筛选。
3. 可以确认告警。
4. 可以关闭告警。
5. 操作后状态正确变化。
6. 操作会写入操作日志。

---

## 16. 模块九：操作日志 AOP

### 16.1 你要做什么

操作日志用于答辩展示“系统可追溯”。你要用 AOP 自动记录关键操作。

### 16.2 需要记录的操作

1. 用户登录。
2. 新增地块。
3. 修改地块。
4. 删除地块。
5. 新增设备。
6. 修改设备。
7. 绑定设备。
8. 解绑设备。
9. 手动开启灌溉。
10. 手动停止灌溉。
11. 手动开启补光。
12. 手动停止补光。
13. 确认告警。
14. 关闭告警。

### 16.3 自定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
    String module();
    String operation();
}
```

### 16.4 使用示例

```java
@LogOperation(module = "灌溉控制", operation = "手动开启灌溉")
@PostMapping("/irrigation")
public Result<?> irrigation(@RequestBody IrrigationControlDTO dto) {
    return Result.ok(controlService.controlIrrigation(dto));
}
```

### 16.5 日志查询接口

```http
GET /api/logs?module=灌溉控制&username=admin&page=1&pageSize=10
```

### 16.6 验收标准

1. 执行关键操作后，`operation_log` 表自动出现记录。
2. 日志里包含用户、模块、操作、请求路径、请求参数、结果、时间。
3. 前端操作日志页面能分页查询。

---

## 17. 模块十：灌溉统计与用水统计

### 17.1 你要做什么

给“灌溉统计”页面提供数据。

### 17.2 接口清单

```http
GET /api/irrigation/stats?plotId=1&startDate=2026-06-01&endDate=2026-06-30
GET /api/irrigation/daily-trend?plotId=1&startDate=2026-06-01&endDate=2026-06-30
GET /api/irrigation/duration-distribution?plotId=1
GET /api/irrigation/water-usage?plotId=1
```

### 17.3 统计总览返回

```json
{
  "totalCount": 18,
  "totalDuration": 230,
  "averageDuration": 12.8,
  "waterAmount": 460,
  "autoCount": 12,
  "manualCount": 6,
  "autoRatio": 66.7,
  "manualRatio": 33.3
}
```

### 17.4 每日趋势返回

```json
[
  {
    "date": "2026-06-01",
    "autoCount": 2,
    "manualCount": 1,
    "totalDuration": 35,
    "waterAmount": 70
  }
]
```

### 17.5 用水量返回

```json
{
  "todayUsage": 45,
  "dailyLimit": 100,
  "usageRatio": 45,
  "remaining": 55,
  "suggestion": "今日用水正常，可保持当前策略"
}
```

### 17.6 实现步骤

1. 创建 `IrrigationRecord` 实体。
2. 创建 `IrrigationRecordMapper`。
3. 创建 `IrrigationStatsVO`。
4. 创建 `IrrigationStatsController`。
5. 创建 `IrrigationStatsService`。
6. 按时间范围统计总次数、总时长、平均时长。
7. 按 `mode` 统计自动/手动次数。
8. 按日期分组统计每日趋势。
9. 估算用水量。
10. 返回前端图表需要的数据结构。

### 17.7 验收标准

1. 前端能显示灌溉次数。
2. 前端能显示总时长。
3. 前端能显示自动/手动占比。
4. 前端能显示每日趋势图。
5. 前端能显示用水提醒。

---

## 18. 模块十一：害虫识别接口

### 18.1 你要做什么

如果成员6已经有模型，你负责接收上传图片并调用模型接口。如果模型暂时没有，你可以先返回模拟结果，保证前端页面能跑通。

### 18.2 接口清单

```http
POST /api/pest/detect
GET  /api/pest/records?plotId=1&page=1&pageSize=10
```

### 18.3 上传参数

```text
plotId
image
```

### 18.4 模拟返回

```json
{
  "pestName": "蚜虫",
  "confidence": 0.87,
  "riskLevel": "MEDIUM",
  "suggestion": "建议及时检查叶片背面，可使用黄色粘虫板诱捕，严重时采用低毒药剂防治。"
}
```

### 18.5 实现步骤

1. 创建 `PestRecord` 实体。
2. 创建 `PestRecordMapper`。
3. 创建 `PestDetectVO`。
4. 创建 `PestController`。
5. 接收 multipart 文件。
6. 保存图片到本地或对象存储。
7. 调用模型接口，或先写模拟结果。
8. 保存识别记录到 `pest_record`。
9. 返回识别结果和建议。
10. 提供历史记录分页查询。

### 18.6 验收标准

1. 前端能上传图片。
2. 后端能返回害虫名称、置信度、风险等级、防治建议。
3. 识别记录能保存。
4. 前端能查看害虫识别历史。

---

## 19. WebSocket 实时推送

### 19.1 你要做什么

WebSocket 可以由成员5主导，但你要保证结构和数据格式能支持前端。

### 19.2 推送地址

```text
/ws/realtime
```

### 19.3 遥测数据推送格式

```json
{
  "type": "TELEMETRY",
  "plotId": 1,
  "data": {
    "soilMoisture": 32,
    "airTemperature": 27,
    "airHumidity": 65,
    "illuminance": 850,
    "pumpStatus": "OFF",
    "lightStatus": "OFF",
    "collectedAt": "2026-06-30 10:28:15"
  }
}
```

### 19.4 告警推送格式

```json
{
  "type": "ALARM",
  "plotId": 1,
  "data": {
    "alarmType": "LOW_SOIL_MOISTURE",
    "level": "WARNING",
    "title": "土壤湿度过低",
    "content": "一号温室土壤湿度 32%，低于阈值 40%"
  }
}
```

### 19.5 命令反馈推送格式

```json
{
  "type": "COMMAND_RESULT",
  "commandNo": "CMD202606301030001",
  "status": "SUCCESS",
  "message": "水泵已开启"
}
```

---

## 20. 你和成员5必须提前约定的内容

### 20.1 设备编码

示例：

```text
DEV-A001 一号温室主控
DEV-A002 一号温室湿度传感器
DEV-A003 一号温室水泵控制器
DEV-A004 一号温室补光灯控制器
DEV-B001 二号温室主控
DEV-B002 二号温室湿度传感器
```

数据库、MQTT、前端必须用同一套设备编码。

---

### 20.2 MQTT 命令 JSON

灌溉命令建议：

```json
{
  "commandNo": "CMD202606301030001",
  "plotId": 1,
  "deviceCode": "DEV-A003",
  "commandType": "IRRIGATION",
  "action": "START",
  "source": "MANUAL",
  "durationMinutes": 15,
  "timestamp": "2026-06-30 10:30:00"
}
```

补光命令建议：

```json
{
  "commandNo": "CMD_LIGHT_202606301900001",
  "plotId": 1,
  "deviceCode": "DEV-A004",
  "commandType": "LIGHTING",
  "action": "START",
  "source": "MANUAL",
  "durationMinutes": 120,
  "brightness": 75,
  "timestamp": "2026-06-30 19:00:00"
}
```

---

### 20.3 硬件回复 JSON

```json
{
  "commandNo": "CMD202606301030001",
  "deviceCode": "DEV-A003",
  "status": "SUCCESS",
  "message": "水泵已开启",
  "timestamp": "2026-06-30 10:30:02"
}
```

---

### 20.4 命令状态责任边界

你负责：

```text
PENDING：命令刚创建
SENT：命令已交给 MQTT 发布
```

成员5负责：

```text
SUCCESS：硬件执行成功
FAILED：硬件执行失败
TIMEOUT：超时未回复
```

---

## 21. 每日开发计划

### Day 1：项目骨架与环境

你要做：

1. 安装 JDK 17。
2. 安装 Maven。
3. 安装 MySQL 8.0。
4. 创建 Spring Boot 3.5.16 项目。
5. 创建基础包结构。
6. 配置 `application.yml`。
7. 添加 MyBatis-Plus、MySQL、Lombok、Validation 依赖。
8. 写 `/api/health` 测试接口。

交付物：

```text
backend/ Spring Boot 项目骨架
项目可启动
/api/health 可访问
```

验收：

```text
http://localhost:8080/api/health 返回 backend ok
```

---

### Day 2：建表与第一个业务接口

你要做：

1. 创建 `smart_agriculture` 数据库。
2. 执行基础 DDL。
3. 创建 `User`、`Plot`、`Device`、`TelemetryData` 实体。
4. 创建对应 Mapper。
5. 配置 `@MapperScan`。
6. 写 `GET /api/plots`。
7. 写 `GET /api/plots/overview` 初版。

交付物：

```text
schema.sql
基础实体类
基础 Mapper
地块列表接口
```

验收：

```text
Postman 调 GET /api/plots 能返回地块列表
```

---

### Day 3：登录、JWT、最新数据接口

你要做：

1. 写注册接口。
2. 写登录接口。
3. 加 BCrypt 密码加密。
4. 写 JWT 工具类。
5. 写 JWT 过滤器。
6. 配置 Spring Security。
7. 写 `GET /api/plots/{id}/latest`。
8. 准备 WebSocket 推送结构。

交付物：

```text
登录注册可用
JWT 鉴权可用
最新遥测数据接口可用
```

验收：

```text
未带 token 访问业务接口返回 401
带 token 访问业务接口正常
GET /api/plots/1/latest 返回最新数据
```

---

### Day 4：灌溉控制接口

你要做：

1. 创建 `ControlCommand` 实体。
2. 创建 `ControlCommandMapper`。
3. 创建 `IrrigationControlDTO`。
4. 创建 `CommandVO`。
5. 写 `POST /api/control/irrigation`。
6. 写 `GET /api/control/commands/{commandNo}`。
7. 和成员5对接 `MqttCommandService`。
8. 控制命令写入数据库。
9. 调用 MQTT 命令发布服务。

交付物：

```text
灌溉控制接口
命令状态查询接口
control_command 表有记录
```

验收：

```text
前端点击手动灌溉后，后端生成 commandNo
成员5能通过 MQTT 看到命令消息
```

---

### Day 5：地块 CRUD 与设备 CRUD

你要做：

1. 完成 `/api/plots` 增删改查。
2. 完成 `/api/devices` 增删改查。
3. 完成设备绑定地块。
4. 完成设备解绑地块。
5. 完成按地块查询设备。

交付物：

```text
地块管理接口
设备管理接口
设备绑定/解绑接口
```

验收：

```text
Postman 全通
前端设备管理页面能用真实接口
```

---

### Day 6：告警查询与操作日志

你要做：

1. 创建 `Alarm` 实体。
2. 创建 `OperationLog` 实体。
3. 写告警列表接口。
4. 写告警详情接口。
5. 写确认告警接口。
6. 写关闭告警接口。
7. 写 `@LogOperation` 注解。
8. 写 `OperationLogAspect`。
9. 写操作日志查询接口。

交付物：

```text
告警查询接口
告警处理接口
操作日志 AOP
日志查询接口
```

验收：

```text
确认告警后 alarm 状态变化
控制灌溉后 operation_log 自动产生记录
```

---

### Day 7：害虫识别与灌溉统计

你要做：

1. 写 `POST /api/pest/detect`。
2. 写 `GET /api/pest/records`。
3. 写 `GET /api/irrigation/stats`。
4. 写 `GET /api/irrigation/daily-trend`。
5. 写 `GET /api/irrigation/duration-distribution`。
6. 写 `GET /api/irrigation/water-usage`。
7. 如果模型未完成，先用模拟结果。

交付物：

```text
害虫识别接口
害虫历史记录接口
灌溉统计接口
用水统计接口
```

验收：

```text
前端上传图片能返回识别结果
灌溉统计页面能显示图表数据
```

---

### Day 8：补光控制与全页面联调

你要做：

1. 写 `POST /api/control/lighting`。
2. 加补光安全校验。
3. 补光命令写入 `control_command`。
4. 调用成员5 MQTT 下发。
5. 和前端对接所有页面接口。
6. 修字段名不一致问题。

交付物：

```text
补光控制接口
补光安全保护逻辑
前端页面接口联调完成
```

验收：

```text
补光命令可下发
亮度、时长、温度、设备状态校验正常
```

---

### Day 9：异常测试与 Bug 修复

你要测：

1. 数据库连接失败。
2. Token 缺失。
3. Token 过期。
4. plotId 不存在。
5. deviceId 不存在。
6. 水泵设备离线。
7. 重复点击灌溉。
8. 命令下发后硬件不回复。
9. 历史数据为空。
10. 告警状态重复处理。
11. 补光亮度非法。
12. 补光时长超限。

交付物：

```text
Bug 修复记录
异常测试结果
稳定版本后端
```

---

### Day 10：演示数据与答辩准备

你要做：

1. 插入演示地块数据。
2. 插入演示设备数据。
3. 插入 7 天遥测数据。
4. 插入几条告警。
5. 插入几条操作日志。
6. 插入几条灌溉记录。
7. 准备后端架构讲解。
8. 准备接口说明。

演示数据至少包括：

```text
3 个地块
5 个设备
50 条以上遥测数据
2 条未处理告警
若干操作日志
若干灌溉记录
若干害虫识别记录
```

---

### Day 11：答辩现场保障

你要做：

1. 启动后端服务。
2. 确认数据库连接正常。
3. 确认 MQTT 服务正常。
4. 确认前端能调通接口。
5. 确认接口日志无报错。
6. 演示时盯控制台和数据库。
7. 如果硬件不稳定，保证前端仍能显示预置数据。

答辩时你可以这样介绍：

```text
我负责后端业务与数据模块，主要完成了用户鉴权、地块管理、设备管理、遥测数据查询、灌溉控制入口、补光控制入口、告警管理、操作日志和统计分析接口。前端的操作会先进入后端接口，后端完成参数校验、权限校验、数据库记录，然后通过 MQTT 服务把控制命令交给硬件执行，硬件执行结果再回写命令状态并推送给前端，实现了从数据采集到远程控制再到日志追溯的完整闭环。
```

---

## 22. 你的接口总清单

### 22.1 基础接口

```http
GET /api/health
```

### 22.2 用户接口

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

### 22.3 地块接口

```http
GET    /api/plots
GET    /api/plots/{id}
POST   /api/plots
PUT    /api/plots/{id}
DELETE /api/plots/{id}
GET    /api/plots/overview
GET    /api/plots/{id}/latest
```

### 22.4 设备接口

```http
GET    /api/devices
GET    /api/devices/{id}
POST   /api/devices
PUT    /api/devices/{id}
DELETE /api/devices/{id}
POST   /api/devices/{id}/bind
POST   /api/devices/{id}/unbind
```

### 22.5 遥测接口

```http
GET /api/telemetry/latest
GET /api/telemetry/history
GET /api/telemetry/page
```

### 22.6 控制接口

```http
POST /api/control/irrigation
POST /api/control/lighting
GET  /api/control/commands/{commandNo}
```

### 22.7 告警接口

```http
GET /api/alarms
GET /api/alarms/{id}
PUT /api/alarms/{id}/ack
PUT /api/alarms/{id}/close
```

### 22.8 日志接口

```http
GET /api/logs
```

### 22.9 灌溉统计接口

```http
GET /api/irrigation/stats
GET /api/irrigation/daily-trend
GET /api/irrigation/duration-distribution
GET /api/irrigation/water-usage
```

### 22.10 害虫识别接口

```http
POST /api/pest/detect
GET  /api/pest/records
```

---

## 23. 最小可交付版本

如果时间不够，你至少要完成这些：

```text
1. Spring Boot 项目能启动。
2. MySQL 能连接。
3. user、plot、device、telemetry_data、control_command、alarm、operation_log 表能用。
4. 登录注册能用。
5. JWT 鉴权能用。
6. 地块列表能查。
7. 地块总览能查。
8. 设备列表能查。
9. 最新遥测数据能查。
10. 历史趋势能查。
11. 灌溉控制接口能生成命令。
12. 命令状态能查询。
13. 告警列表能查。
14. 操作日志能记录。
```

这条主线跑通后，答辩就比较稳。

---

## 24. 你每天提交 Git 的建议

提交信息可以这样写：

```text
day1: init spring boot backend structure
day2: add database schema and plot api
day3: add auth jwt and telemetry latest api
day4: add irrigation control command api
day5: add plot and device crud api
day6: add alarm api and operation log aspect
day7: add pest detect mock api and irrigation statistics api
day8: add lighting control api and integration fixes
day9: fix bugs and improve exception handling
day10: add demo data and api docs
```

---

## 25. 最终交付物

你最终应该交付：

```text
backend/ 后端项目代码
src/main/resources/application.yml
src/main/resources/sql/schema.sql
src/main/resources/sql/data.sql
docs/数据库设计DDL.sql
docs/REST_API接口文档.md
docs/后端启动说明.md
docs/Postman测试说明.md
```

最终验收标准：

```text
1. 项目能正常启动。
2. 数据库能正常连接。
3. 登录鉴权可用。
4. 地块和设备 CRUD 可用。
5. 前端能展示实时数据和历史趋势。
6. 灌溉命令能生成并交给 MQTT 下发。
7. 补光命令能生成并交给 MQTT 下发。
8. 告警能查询和处理。
9. 操作日志能自动记录。
10. 灌溉统计能返回图表数据。
11. 害虫识别接口能返回结果。
12. 演示数据完整，答辩时页面不空。
```

---

## 26. 开发优先级

优先级最高：

```text
登录注册 → 地块总览 → 设备管理 → 最新数据 → 灌溉控制 → 告警日志
```

第二优先级：

```text
历史趋势 → 操作日志 → 灌溉统计 → 补光控制
```

第三优先级：

```text
害虫识别 → AI 问答 → 更复杂的统计图表
```

不要一上来就做 AI 和害虫识别，先把主链路打通。
