# Day8 后端功能完成说明

## 已完成内容

1. 补光控制 API
   - `POST /api/light/control`
   - 请求体：`{"deviceCode":"6a44b8fdcbb0cf6bb96ad1a1_bearpi_001","action":"ON","force":false}`
   - `action` 支持 `ON`、`OFF`
   - 开灯时会检查当前光照，达到关闭阈值时默认禁止开灯；如确实要强制开灯，传 `force=true`

2. 补光状态与策略接口
   - `GET /api/light/status?plotId=1`
   - `GET /api/light/status?deviceCode=6a44b8fdcbb0cf6bb96ad1a1_bearpi_001`
   - `GET /api/light-strategies`
   - `GET /api/light-strategies/{plotId}`
   - `PUT /api/light-strategies/{plotId}`

3. 自动补光引擎
   - 新遥测数据进入后，后端会扫描 `telemetry_data`
   - 默认低于 `500 lux` 自动下发 `LIGHT_ON`
   - 默认高于 `800 lux` 自动下发 `LIGHT_OFF`
   - 默认有效时段为 `06:00-20:00`
   - 默认冷却时间为 `5` 分钟，防止重复下发

4. 害虫历史与趋势统计
   - `POST /api/pest/detect` 会保存识别记录
   - `GET /api/pest/records`
   - `GET /api/pest/trend?plotId=1&startDate=2026-07-01&endDate=2026-07-07`
   - `GET /api/pest/distribution?plotId=1&startDate=2026-07-01&endDate=2026-07-07`

5. 用水提醒推送
   - 用水达到提醒线或上限时，会写入 `alarm`
   - 同时通过 `/ws/realtime` 推送：
     - `type = WATER_USAGE_REMINDER`
   - 后端也会每 60 秒主动检查一次用水提醒

## 需要执行的数据库 SQL

执行 `src/main/resources/sql/schema.sql` 中新增的：

- `light_strategy`
- `pest_detection_record`

然后执行 `src/main/resources/sql/data.sql` 中新增的 `light_strategy` 初始化数据。

## 前端联调重点

补光查询接口可以不带 token：

- `GET /api/light/status`
- `GET /api/light-strategies`

补光控制和策略修改需要 Bearer Token：

- `POST /api/light/control`
- `PUT /api/light-strategies/{plotId}`

## 设备端联调重点

后端补光命令仍然走已有控制命令通道：

- `LIGHT_ON` 表示开补光灯
- `LIGHT_OFF` 表示关补光灯
- 命令值分别是 `ON` / `OFF`

如果设备端已经适配后端控制命令，只需要确认灯命令也能被识别并执行。
