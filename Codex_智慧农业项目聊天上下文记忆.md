# Codex 上下文记忆：智慧农业后端项目（成员4 / BE-A）

> 用途：把本聊天框里已经确定的项目背景、技术选型、分工、后端骨架、配置方式、数据库连接方式、开发顺序等整理给 Codex，避免重复解释。

---

## 1. 项目基本信息

项目名称：**智慧农业监测系统 / smart-agriculture-backend**

项目目标：做一个智慧农业系统，包含地块监测、设备管理、阈值策略、告警日志、灌溉控制、补光控制、历史趋势、灌溉统计、害虫识别、智能问答等功能。

前端原型中已经出现的页面模块：

- 登录页
- 地块总览
- 地块详情
- 历史趋势
- 设备列表
- 阈值策略
- 告警管理
- 操作日志
- 智能问答
- 害虫识别
- 灌溉统计

后端要服务这些页面，提供 REST API、数据库读写、JWT 鉴权、控制命令入口、统计查询、日志记录等能力。

---

## 2. 用户身份与分工

当前用户是团队里的：

```text
成员4：后端A / BE-A / 业务与数据
```

另一个后端是：

```text
成员5：后端B / BE-B / 物联网与规则
```

核心分工：

| 模块 | 负责人 | 说明 |
|---|---|---|
| 项目初始化 | 成员4 + 成员5 | Spring Boot、pom、application.yml、基础包结构 |
| 数据库表设计 | 成员4主导 | user、plot、device、telemetry_data、alarm 等 |
| 登录注册 | 成员4 | AuthController、JWT、Spring Security |
| 地块管理 | 成员4 | PlotController、PlotService、PlotMapper |
| 设备管理 | 成员4 | DeviceController、设备 CRUD、绑定/解绑地块 |
| 遥测数据查询 | 成员4 | 最新数据、历史趋势、分页明细 |
| 灌溉控制接口 | 成员4写入口，成员5写 MQTT 下发 | ControlController 调用 MqttCommandService |
| 补光控制接口 | 成员4写入口，成员5写 MQTT 下发 | 与灌溉控制同结构 |
| MQTT 订阅与发布 | 成员5 | 遥测、心跳、命令回复 |
| 遥测数据入库 | 成员5主导，成员4配合表结构 | MQTT → telemetry_data |
| 设备心跳与离线判断 | 成员5 | 更新 device.last_heartbeat、device.status |
| 自动灌溉 / 自动补光 | 成员5 | 根据阈值、冷却时间、连续超限判断 |
| 告警规则生成 | 成员5 | 自动插入 alarm 表 |
| 告警查询与处理 | 成员4 | 查询、确认、关闭 |
| 操作日志 | 成员4 | AOP 自动记录 |
| 灌溉统计 | 成员4 | irrigation_record 查询统计 |
| WebSocket 推送 | 成员5主导，成员4配合 | 实时数据、告警、命令结果推送 |

一句话：

```text
成员4负责：业务接口 + 数据库 + 鉴权 + CRUD + 查询统计 + 日志
成员5负责：MQTT + 硬件通信 + 规则引擎 + 自动控制 + 心跳
```

---

## 3. 技术选型已确定

最终选择：

```text
Spring Boot：3.5.16
Language：Java
Type：Maven
Packaging：Jar
Java：17
Group：com.agriculture
Artifact：smart-agriculture-backend
Name：smart-agriculture-backend
Package name：com.agriculture
```

选择 Spring Boot 3.5.16 的原因：

- 课程项目优先稳定、教程多、依赖兼容好。
- 不选 SNAPSHOT 版本。
- 不建议选 Spring Boot 4.x，因为大版本升级，依赖生态和教程适配成本更高。
- 3.5.16 更适合 MyBatis-Plus、Spring Security、JWT、WebSocket、MQTT 等组合。

建议依赖：

```text
Spring Web
Spring Security
Spring Boot DevTools
Lombok
Validation
MySQL Driver
WebSocket
MyBatis-Plus
AOP
JWT
后续 MQTT：spring-integration-mqtt
```

---

## 4. application.yml 配置方式

之前出现过一个问题：用户把 `.properties` 和 `.yml` 两种配置格式混用了，例如：

```properties
spring.application.name=smart-agriculture-backend
```

后面又写成：

```yaml
server:
port: 8080
```

正确做法：**二选一**。推荐使用 `application.yml`。

路径：

```text
src/main/resources/application.yml
```

推荐配置：

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

注意：

- YAML 必须缩进。
- `server.port` 在 YAML 中必须写成：

```yaml
server:
  port: 8080
```

- `spring.datasource.url` 在 YAML 中必须写成：

```yaml
spring:
  datasource:
    url: xxx
```

---

## 5. 连接队友数据库的规则

如果用户要连接别人电脑上的 MySQL，需要问队友要：

```text
数据库主机 IP
端口，通常 3306
数据库名，例如 smart_agriculture
用户名
密码
```

Navicat 截图中出现过一个可能的主机：

```text
192.168.20.111:3306
MySQL 版本：8.0.46
```

但实际连接参数以队友提供为准。

连接队友数据库时，把 `localhost` 改成队友 IP：

```yaml
spring:
  datasource:
    url: jdbc:mysql://192.168.20.111:3306/smart_agriculture?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: 队友给你的用户名
    password: 队友给你的密码
    driver-class-name: com.mysql.cj.jdbc.Driver
```

队友电脑必须满足：

1. MySQL 允许远程连接。
2. 防火墙放行 3306。
3. 用户账号允许远程访问。
4. 双方在同一个局域网 / 校园网 / VPN。

推荐让队友创建单独项目用户，不要直接远程用 root：

```sql
CREATE USER 'agri_user'@'%' IDENTIFIED BY '123456';
GRANT ALL PRIVILEGES ON smart_agriculture.* TO 'agri_user'@'%';
FLUSH PRIVILEGES;
```

如果数据库不存在：

```sql
CREATE DATABASE smart_agriculture DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

安全提醒：`123456` 只是示例密码，不建议提交到 Git。实际项目可改用环境变量或本地配置。

---

## 6. 后端项目目录骨架

推荐完整目录：

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
│   │   │           ├── common
│   │   │           │   ├── Result.java
│   │   │           │   ├── PageResult.java
│   │   │           │   ├── ErrorCode.java
│   │   │           │   ├── BusinessException.java
│   │   │           │   └── GlobalExceptionHandler.java
│   │   │           ├── config
│   │   │           │   ├── SecurityConfig.java
│   │   │           │   ├── CorsConfig.java
│   │   │           │   ├── MybatisPlusConfig.java
│   │   │           │   ├── WebSocketConfig.java
│   │   │           │   └── MqttConfig.java
│   │   │           ├── security
│   │   │           │   ├── JwtUtil.java
│   │   │           │   ├── JwtAuthenticationFilter.java
│   │   │           │   └── LoginUser.java
│   │   │           ├── controller
│   │   │           │   ├── HealthController.java
│   │   │           │   ├── AuthController.java
│   │   │           │   ├── PlotController.java
│   │   │           │   ├── DeviceController.java
│   │   │           │   ├── TelemetryController.java
│   │   │           │   ├── ControlController.java
│   │   │           │   ├── AlarmController.java
│   │   │           │   ├── ThresholdController.java
│   │   │           │   ├── OperationLogController.java
│   │   │           │   ├── IrrigationStatsController.java
│   │   │           │   ├── PestController.java
│   │   │           │   └── AiChatController.java
│   │   │           ├── entity
│   │   │           │   ├── User.java
│   │   │           │   ├── Plot.java
│   │   │           │   ├── Device.java
│   │   │           │   ├── TelemetryData.java
│   │   │           │   ├── ControlCommand.java
│   │   │           │   ├── Alarm.java
│   │   │           │   ├── ThresholdStrategy.java
│   │   │           │   ├── OperationLog.java
│   │   │           │   ├── IrrigationRecord.java
│   │   │           │   └── PestRecord.java
│   │   │           ├── mapper
│   │   │           │   ├── UserMapper.java
│   │   │           │   ├── PlotMapper.java
│   │   │           │   ├── DeviceMapper.java
│   │   │           │   ├── TelemetryDataMapper.java
│   │   │           │   ├── ControlCommandMapper.java
│   │   │           │   ├── AlarmMapper.java
│   │   │           │   ├── ThresholdStrategyMapper.java
│   │   │           │   ├── OperationLogMapper.java
│   │   │           │   ├── IrrigationRecordMapper.java
│   │   │           │   └── PestRecordMapper.java
│   │   │           ├── service
│   │   │           │   ├── AuthService.java
│   │   │           │   ├── PlotService.java
│   │   │           │   ├── DeviceService.java
│   │   │           │   ├── TelemetryService.java
│   │   │           │   ├── ControlService.java
│   │   │           │   ├── AlarmService.java
│   │   │           │   ├── ThresholdService.java
│   │   │           │   ├── OperationLogService.java
│   │   │           │   ├── IrrigationStatsService.java
│   │   │           │   ├── PestService.java
│   │   │           │   ├── AiChatService.java
│   │   │           │   └── MqttCommandService.java
│   │   │           ├── service
│   │   │           │   └── impl
│   │   │           │       ├── AuthServiceImpl.java
│   │   │           │       ├── PlotServiceImpl.java
│   │   │           │       ├── DeviceServiceImpl.java
│   │   │           │       ├── TelemetryServiceImpl.java
│   │   │           │       ├── ControlServiceImpl.java
│   │   │           │       ├── AlarmServiceImpl.java
│   │   │           │       ├── ThresholdServiceImpl.java
│   │   │           │       ├── OperationLogServiceImpl.java
│   │   │           │       ├── IrrigationStatsServiceImpl.java
│   │   │           │       ├── PestServiceImpl.java
│   │   │           │       ├── AiChatServiceImpl.java
│   │   │           │       └── MqttCommandServiceImpl.java
│   │   │           ├── dto
│   │   │           │   ├── LoginDTO.java
│   │   │           │   ├── RegisterDTO.java
│   │   │           │   ├── PlotDTO.java
│   │   │           │   ├── DeviceDTO.java
│   │   │           │   ├── IrrigationControlDTO.java
│   │   │           │   ├── LightingControlDTO.java
│   │   │           │   ├── ThresholdStrategyDTO.java
│   │   │           │   └── AlarmHandleDTO.java
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
│   │   │           │   ├── PestDetectVO.java
│   │   │           │   └── AiChatVO.java
│   │   │           ├── aspect
│   │   │           │   ├── LogOperation.java
│   │   │           │   └── OperationLogAspect.java
│   │   │           ├── websocket
│   │   │           │   ├── RealtimeWebSocketHandler.java
│   │   │           │   └── WebSocketMessageSender.java
│   │   │           ├── mqtt
│   │   │           │   ├── MqttMessageHandler.java
│   │   │           │   ├── MqttTopicConstant.java
│   │   │           │   └── MqttCommandPublisher.java
│   │   │           ├── rule
│   │   │           │   ├── IrrigationRuleEngine.java
│   │   │           │   └── LightingRuleEngine.java
│   │   │           ├── scheduler
│   │   │           │   └── DeviceHeartbeatScheduler.java
│   │   │           └── enums
│   │   │               ├── DeviceStatusEnum.java
│   │   │               ├── AlarmStatusEnum.java
│   │   │               ├── CommandStatusEnum.java
│   │   │               └── CommandTypeEnum.java
│   │   └── resources
│   │       ├── application.yml
│   │       ├── mapper
│   │       │   ├── UserMapper.xml
│   │       │   ├── PlotMapper.xml
│   │       │   ├── DeviceMapper.xml
│   │       │   ├── TelemetryDataMapper.xml
│   │       │   ├── AlarmMapper.xml
│   │       │   └── IrrigationRecordMapper.xml
│   │       └── sql
│   │           ├── schema.sql
│   │           └── data.sql
│   └── test
│       └── java
│           └── com
│               └── agriculture
│                   └── SmartAgricultureApplicationTests.java
```

前期最小骨架可以先只建：

```text
com.agriculture
├── SmartAgricultureApplication.java
├── common
│   └── Result.java
├── controller
│   ├── HealthController.java
│   ├── AuthController.java
│   ├── PlotController.java
│   ├── DeviceController.java
│   └── ControlController.java
├── entity
│   ├── User.java
│   ├── Plot.java
│   ├── Device.java
│   └── TelemetryData.java
├── mapper
│   ├── UserMapper.java
│   ├── PlotMapper.java
│   ├── DeviceMapper.java
│   └── TelemetryDataMapper.java
├── service
│   ├── PlotService.java
│   └── impl
│       └── PlotServiceImpl.java
└── config
    └── MybatisPlusConfig.java
```

---

## 7. Maven pom.xml 核心依赖

建议先放这些：

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

MQTT 后续由成员5加入：

```xml
<dependency>
    <groupId>org.springframework.integration</groupId>
    <artifactId>spring-integration-mqtt</artifactId>
</dependency>
```

---

## 8. 数据库表设计

推荐先建核心表，后续再扩展。

### 8.1 user

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

### 8.2 plot

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

### 8.3 device

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

设备类型建议：

```text
BEARPI_MAIN
SOIL_SENSOR
PUMP_CONTROLLER
LIGHT_CONTROLLER
CAMERA
```

### 8.4 telemetry_data

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

### 8.5 control_command

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

字段枚举：

```text
command_type: IRRIGATION / LIGHTING
action: START / STOP
source: MANUAL / AUTO
status: PENDING / SENT / SUCCESS / FAILED / TIMEOUT
```

### 8.6 alarm

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

### 8.7 operation_log

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

### 8.8 irrigation_record

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

### 8.9 pest_record

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

---

## 9. 统一返回格式

所有接口统一返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

Java 类：

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
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

---

## 10. 核心 API 清单

### 10.1 健康检查

```http
GET /api/health
```

返回：

```text
backend ok
```

### 10.2 登录注册

```http
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

登录请求：

```json
{
  "username": "admin",
  "password": "123456"
}
```

登录返回：

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

### 10.3 地块管理

```http
GET    /api/plots
GET    /api/plots/{id}
POST   /api/plots
PUT    /api/plots/{id}
DELETE /api/plots/{id}
GET    /api/plots/overview
GET    /api/plots/{id}/latest
```

地块总览返回示例：

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
    "lastUpdateTime": "2026-06-30 10:28:15",
    "alarmCount": 1
  }
]
```

### 10.4 设备管理

```http
GET    /api/devices
GET    /api/devices/{id}
POST   /api/devices
PUT    /api/devices/{id}
DELETE /api/devices/{id}
GET    /api/devices?plotId=1
POST   /api/devices/{id}/bind
POST   /api/devices/{id}/unbind
```

绑定请求：

```json
{
  "plotId": 1
}
```

### 10.5 遥测数据 / 历史趋势

```http
GET /api/plots/{id}/latest
GET /api/telemetry/history?plotId=1&startTime=2026-06-30 00:00:00&endTime=2026-06-30 23:59:59
GET /api/telemetry/page?plotId=1&page=1&pageSize=10
```

最新数据返回：

```json
{
  "plotId": 1,
  "soilMoisture": 32,
  "airTemperature": 27,
  "airHumidity": 65,
  "illuminance": 850,
  "pumpStatus": "OFF",
  "lightStatus": "OFF",
  "collectedAt": "2026-06-30 10:28:15"
}
```

### 10.6 灌溉控制

```http
POST /api/control/irrigation
GET  /api/control/commands/{commandNo}
```

开启请求：

```json
{
  "plotId": 1,
  "action": "START",
  "mode": "MANUAL",
  "durationMinutes": 15
}
```

停止请求：

```json
{
  "plotId": 1,
  "action": "STOP",
  "mode": "MANUAL"
}
```

处理流程：

```text
1. 校验用户是否登录
2. 校验 plotId 是否存在
3. 查找该地块绑定的水泵控制器
4. 检查设备是否在线
5. 检查灌溉时长是否超过上限，例如 15 分钟
6. 生成 command_no
7. 写入 control_command 表，状态 PENDING
8. 调用成员5提供的 MqttCommandService.publishIrrigationCommand()
9. 更新命令状态为 SENT
10. 返回 commandNo 给前端
```

返回：

```json
{
  "commandNo": "CMD202606301030001",
  "status": "SENT",
  "message": "灌溉命令已下发"
}
```

### 10.7 补光控制

```http
POST /api/control/lighting
```

请求：

```json
{
  "plotId": 1,
  "action": "START",
  "mode": "MANUAL",
  "durationMinutes": 120,
  "brightness": 75
}
```

安全校验：

```text
单日补光时长不能超过 4 小时
brightness 只能是 25 / 50 / 75 / 100
如果当前温度 > 40℃，拒绝开启补光
如果设备离线，拒绝命令
```

### 10.8 告警管理

```http
GET /api/alarms?plotId=1&status=UNHANDLED&page=1&pageSize=10
GET /api/alarms/{id}
PUT /api/alarms/{id}/ack
PUT /api/alarms/{id}/close
```

状态流转：

```text
UNHANDLED → ACKED → CLOSED
UNHANDLED → RECOVERED
ACKED → RECOVERED
```

### 10.9 操作日志

```http
GET /api/logs?module=灌溉控制&username=admin&page=1&pageSize=10
```

使用 AOP 注解：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
    String module();
    String operation();
}
```

### 10.10 灌溉统计

```http
GET /api/irrigation/stats?plotId=1&startDate=2026-06-01&endDate=2026-06-30
GET /api/irrigation/daily-trend?plotId=1&startDate=2026-06-01&endDate=2026-06-30
GET /api/irrigation/duration-distribution?plotId=1
GET /api/irrigation/water-usage?plotId=1
```

### 10.11 害虫识别

```http
POST /api/pest/detect
Content-Type: multipart/form-data
```

参数：

```text
plotId
image
```

如果成员6模型没完成，可先返回模拟结果：

```json
{
  "pestName": "蚜虫",
  "confidence": 0.87,
  "riskLevel": "MEDIUM",
  "suggestion": "建议及时检查叶片背面，可使用黄色粘虫板诱捕，严重时采用低毒药剂防治。"
}
```

历史记录：

```http
GET /api/pest/records?plotId=1&page=1&pageSize=10
```

---

## 11. 与成员5对接的关键 Java 接口

成员4写控制入口，成员5写 MQTT 具体实现。

建议接口：

```java
public interface MqttCommandService {
    void publishIrrigationCommand(ControlCommand command);
    void publishLightingCommand(ControlCommand command);
}
```

成员4负责：

```text
校验参数
查设备
生成 commandNo
写入 control_command 表
调用 MqttCommandService
返回给前端
```

成员5负责：

```text
把 command 转成 MQTT JSON
发布到硬件 Topic
接收硬件 reply
更新 command 状态
```

---

## 12. WebSocket 推送格式

推送地址建议：

```text
/ws/realtime
```

遥测推送：

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
    "collectedAt": "2026-06-30 10:28:15"
  }
}
```

告警推送：

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

命令反馈推送：

```json
{
  "type": "COMMAND_RESULT",
  "commandNo": "CMD202606301030001",
  "status": "SUCCESS",
  "message": "水泵已开启"
}
```

---

## 13. 开发顺序建议

不要一上来全写，按这个顺序：

```text
第 1 步：项目能启动
第 2 步：/api/health 能访问
第 3 步：application.yml 连接 MySQL 成功
第 4 步：建 user、plot、device、telemetry_data 表
第 5 步：写 Result 统一返回类
第 6 步：写 PlotController + PlotService + PlotMapper
第 7 步：Postman 测 /api/plots
第 8 步：写 AuthController 登录注册
第 9 步：接 JWT
第 10 步：写 DeviceController
第 11 步：写 TelemetryController
第 12 步：写 ControlController，和成员5对接 MQTT
第 13 步：写 AlarmController
第 14 步：写 OperationLog AOP
第 15 步：写 IrrigationStatsController
第 16 步：写 PestController / AiChatController 的简化版
```

---

## 14. 每日计划中成员4要做的事

### Day 1

```text
安装 JDK 17 + Maven + MySQL 8.0
创建 Spring Boot 项目骨架
引入 MyBatis-Plus 等依赖
项目可启动
```

验收：

```text
GET /api/health 返回 backend ok
```

### Day 2

```text
执行 DDL 建表
MyBatis-Plus 代码生成或手动创建 Entity / Mapper / Service
编写第一个 REST API：地块列表
```

验收：

```text
GET /api/plots 能返回地块列表
```

### Day 3

```text
编写登录 API + JWT 生成
编写 GET /api/plots/{id}/latest
配置 WebSocket 推送
```

验收：

```text
登录可用
带 token 可访问接口
最新遥测数据可查
```

### Day 4

```text
完成 POST /api/control/irrigation
完成用户注册/登录全流程
Spring Security + JWT 过滤器
```

验收：

```text
灌溉命令可以写入 control_command
并调用成员5的 MQTT 下发服务
```

### Day 5

```text
地块 CRUD API：/api/plots
设备 CRUD API：/api/devices
绑定/解绑地块 API
```

### Day 6

```text
操作日志 AOP 切面
告警查询 API
日志查询 API
```

### Day 7

```text
害虫识别 API + 防治建议 API
用水量统计 API
```

### Day 8

```text
补光控制 API
补光安全保护逻辑
前端所有页面接口联调
```

### Day 9 - Day 10

```text
后端 Bug 修复
接口性能优化
配合模拟答辩
```

---

## 15. 验收主线

最终最重要的演示主线：

```text
登录
→ 地块总览
→ 地块详情实时数据
→ 手动开启灌溉
→ 后端生成控制命令
→ 成员5 MQTT 下发
→ 硬件执行
→ 命令回复
→ 告警/日志/统计可追踪
```

成员4的核心贡献：

```text
后端业务接口稳定
数据库表清晰
JWT 鉴权可用
地块/设备/遥测数据可查询
灌溉/补光控制入口可用
告警与操作日志可查
灌溉统计可用于图表
```

---

## 16. 给 Codex 的下一步任务建议

如果让 Codex 继续写代码，优先让它按这个顺序生成：

1. `pom.xml`
2. `application.yml`
3. `common/Result.java`
4. `controller/HealthController.java`
5. `entity/Plot.java`
6. `mapper/PlotMapper.java`
7. `service/PlotService.java`
8. `service/impl/PlotServiceImpl.java`
9. `controller/PlotController.java`
10. `resources/sql/schema.sql`
11. `resources/sql/data.sql`

第一个目标不要贪多：

```text
让 Spring Boot 启动成功，并能访问：
GET /api/health
GET /api/plots
```

然后再接登录、JWT、设备、遥测、控制命令。

---

## 17. 特别注意

1. 一个团队只维护一个后端项目，不要成员4和成员5各建一套。
2. 先统一数据库表和接口返回格式。
3. Controller 不要直接操作 Mapper，应该走 Service。
4. 控制命令不要在成员4这里直接写 MQTT 细节，而是调用 `MqttCommandService`。
5. 成员5收到硬件数据后写 `telemetry_data`，成员4负责查询展示。
6. 成员5生成 `alarm`，成员4负责查询、确认、关闭。
7. 配置文件不要混用 `.properties` 和 `.yml`。
8. 不要把真实数据库密码提交到 Git。
9. 开发初期可以先用模拟数据，保证前端页面不空。
10. 主线通了以后，再做 AI 问答和害虫识别增强功能。
