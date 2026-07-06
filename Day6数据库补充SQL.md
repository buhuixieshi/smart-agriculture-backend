# Day6 数据库补充 SQL

当前代码已经在 `src/main/resources/sql/schema.sql` 中补了 `operation_log` 表。

如果你不是重建数据库，而是在现有 `smart_agriculture` 库上继续开发，请在 Navicat 执行下面 SQL：

```sql
USE smart_agriculture;

CREATE TABLE IF NOT EXISTS operation_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  operator_id BIGINT,
  operator_name VARCHAR(100),
  operation_type VARCHAR(50) NOT NULL,
  target VARCHAR(100),
  detail VARCHAR(500),
  result VARCHAR(20),
  error_message VARCHAR(500),
  request_method VARCHAR(20),
  request_uri VARCHAR(255),
  ip VARCHAR(64),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

如果你的 `irrigation_strategy` 表没有 `create_time` / `update_time` 字段，当前后端代码已经做了兼容，不会因为这两个字段缺失而启动失败。

想让数据库结构和 `schema.sql` 完全一致时，可以手动确认后再补：

```sql
ALTER TABLE irrigation_strategy
  ADD COLUMN create_time DATETIME DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE irrigation_strategy
  ADD COLUMN update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
```

如果执行 `ALTER TABLE` 提示字段已存在，就不用重复执行。
