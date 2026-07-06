# Day7 BE-B 补充说明

本轮已经把 Day7 BE-B 的后端结构补到项目中：

- 害虫识别模型服务接入入口
- 害虫防治知识库数据库表
- 用水上限配置表
- PUMP_ON 前置用水上限检查
- PUMP_OFF 后单次用水超限告警

## 1. 需要补充执行的数据库 SQL

如果你不是重建数据库，而是在现有 `smart_agriculture` 库上继续开发，请在 Navicat 执行：

```sql
USE smart_agriculture;

CREATE TABLE IF NOT EXISTS pest_knowledge (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pest_id VARCHAR(50) NOT NULL UNIQUE,
  pest_name VARCHAR(100) NOT NULL,
  danger_level VARCHAR(20),
  description VARCHAR(500),
  physical_control TEXT,
  biological_control TEXT,
  chemical_control TEXT,
  prevention TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS water_usage_limit (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL UNIQUE,
  daily_limit DECIMAL(10,2) DEFAULT 200.00,
  monthly_limit DECIMAL(10,2) DEFAULT 3000.00,
  single_limit DECIMAL(10,2) DEFAULT 50.00,
  alert_percent DECIMAL(5,2) DEFAULT 80.00,
  min_effective_duration INT DEFAULT 10,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

`src/main/resources/sql/data.sql` 已经补了默认害虫知识和用水上限种子数据。重建库时会自动插入；不重建库时可以从该文件复制对应 INSERT 执行。

## 2. 害虫模型服务对接要求

后端默认配置：

```yaml
pest:
  model:
    enabled: false
    url: http://127.0.0.1:5001/pest/detect
```

现在默认关闭模型服务，接口会用模拟识别兜底。等模型服务准备好后，把 `enabled` 改成 `true`。

模型服务需要提供：

```text
POST /pest/detect
Content-Type: multipart/form-data
file=图片
plotId=地块ID，可选
```

返回 JSON：

```json
{
  "pestId": "aphid",
  "pestName": "蚜虫",
  "dangerLevel": "MEDIUM",
  "confidence": 0.91,
  "message": "ok"
}
```

其中 `pestId` 必须能在 `pest_knowledge.pest_id` 中查到，才能返回完整防治建议。

## 3. 用水上限逻辑

当后端准备下发：

```text
PUMP_ON
```

会先检查：

- 今日用水量是否超过 `daily_limit`
- 本月用水量是否超过 `monthly_limit`
- 今日用水量是否达到 `alert_percent`

如果超过日/月上限：

- 拒绝下发水泵开启命令
- 生成告警：
  - `WATER_DAILY_LIMIT`
  - `WATER_MONTHLY_LIMIT`

如果只是接近上限：

- 允许继续灌溉
- 生成提醒告警：
  - `WATER_DAILY_WARNING`

当 `PUMP_OFF` 成功后，会检查本次灌溉估算用水量是否超过 `single_limit`，超过则生成：

```text
WATER_SINGLE_LIMIT
```

## 4. 可测试接口

害虫知识：

```text
GET /api/pest/suggestions
GET /api/pest/suggestions/aphid
```

害虫识别：

```text
POST /api/pest/detect
form-data:
  file: 图片
  plotId: 1
```

用水限制：

```text
GET /api/water-limits
GET /api/water-limits/1
PUT /api/water-limits/1
```

PUT 示例：

```json
{
  "dailyLimit": 200,
  "monthlyLimit": 3000,
  "singleLimit": 50,
  "alertPercent": 80,
  "minEffectiveDuration": 10
}
```

控制命令测试：

```text
POST /api/control/send?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001&commandType=PUMP_ON&commandValue=ON
```

如果当前地块用水已经超过上限，会返回业务错误，不会继续下发开泵命令。
