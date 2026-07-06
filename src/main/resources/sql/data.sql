USE smart_agriculture;

INSERT INTO plot (id, name, crop_type, location, area, status, description)
VALUES
  (1, 'Greenhouse A - Tomato Area', 'Tomato', 'Area A', 120.00, 'ONLINE', 'Tomato planting and irrigation demo area'),
  (2, 'Greenhouse B - Cucumber Area', 'Cucumber', 'Area B', 100.00, 'ONLINE', 'Cucumber planting and lighting demo area'),
  (3, 'Open Field C - Corn Area', 'Corn', 'Area C', 320.00, 'OFFLINE', 'Open field under maintenance')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  crop_type = VALUES(crop_type),
  location = VALUES(location),
  area = VALUES(area),
  status = VALUES(status),
  description = VALUES(description);

INSERT INTO device (id, device_code, device_name, device_type, plot_id, status, last_heartbeat, signal_strength, battery)
VALUES
  (1, 'DEV-A001', 'Greenhouse A Main Controller', 'BEARPI_MAIN', 1, 'ONLINE', NOW(), 86, 92),
  (2, 'DEV-A002', 'Greenhouse A Soil Sensor', 'SOIL_SENSOR', 1, 'ONLINE', NOW(), 82, 88),
  (3, 'DEV-A003', 'Greenhouse A Pump Controller', 'PUMP_CONTROLLER', 1, 'ONLINE', NOW(), 78, 90),
  (4, 'DEV-B001', 'Greenhouse B Main Controller', 'BEARPI_MAIN', 2, 'ONLINE', NOW(), 80, 85),
  (5, 'DEV-C001', 'Open Field C Camera', 'CAMERA', 3, 'OFFLINE', NULL, 0, 35)
ON DUPLICATE KEY UPDATE
  device_code = VALUES(device_code),
  device_name = VALUES(device_name),
  device_type = VALUES(device_type),
  plot_id = VALUES(plot_id),
  status = VALUES(status),
  last_heartbeat = VALUES(last_heartbeat),
  signal_strength = VALUES(signal_strength),
  battery = VALUES(battery);

INSERT INTO device (device_code, device_name, device_type, plot_id, status, last_heartbeat)
VALUES
  ('6a44b8fdcbb0cf6bb96ad1a1_bearpi_001', 'BearPi-001', 'BEARPI', 1, 'ONLINE', NOW())
ON DUPLICATE KEY UPDATE
  device_name = VALUES(device_name),
  device_type = VALUES(device_type),
  plot_id = VALUES(plot_id),
  status = VALUES(status),
  last_heartbeat = VALUES(last_heartbeat);

INSERT INTO telemetry_data (
  plot_id,
  device_id,
  soil_moisture,
  air_temperature,
  air_humidity,
  illuminance,
  pump_status,
  light_status,
  collected_at
)
VALUES
  (1, NULL, 32.00, 27.00, 65.00, 850.00, 'OFF', 'OFF', NOW()),
  (1, NULL, 35.00, 26.50, 66.00, 830.00, 'OFF', 'OFF', DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
  (2, NULL, 68.00, 24.00, 70.00, 900.00, 'ON', 'OFF', NOW());

INSERT INTO irrigation_strategy (
  plot_id,
  moisture_min,
  moisture_max,
  consecutive_threshold,
  auto_mode,
  max_duration,
  cooldown_minutes
)
VALUES
  (1, 40.00, 70.00, 3, 1, 900, 10)
ON DUPLICATE KEY UPDATE
  moisture_min = VALUES(moisture_min),
  moisture_max = VALUES(moisture_max),
  consecutive_threshold = VALUES(consecutive_threshold),
  auto_mode = VALUES(auto_mode),
  max_duration = VALUES(max_duration),
  cooldown_minutes = VALUES(cooldown_minutes);

INSERT INTO pest_knowledge (
  pest_id,
  pest_name,
  danger_level,
  description,
  physical_control,
  biological_control,
  chemical_control,
  prevention
)
VALUES
  (
    'aphid',
    '蚜虫',
    'MEDIUM',
    '常聚集在嫩叶和新梢吸食汁液，容易诱发叶片卷曲和病毒病传播。',
    '剪除虫量集中的嫩梢\n悬挂黄色粘虫板诱捕有翅蚜',
    '保护瓢虫、草蛉等天敌\n释放蚜茧蜂进行生物控制',
    '虫口密度较高时选用吡虫啉或啶虫脒，按说明低浓度喷施',
    '避免氮肥过量\n加强通风，降低植株郁闭度'
  ),
  (
    'whitefly',
    '白粉虱',
    'MEDIUM',
    '成虫和若虫在叶背吸汁，分泌蜜露并诱发煤污病。',
    '悬挂黄色粘虫板\n及时清除老叶和病残体',
    '释放丽蚜小蜂\n保护捕食性天敌',
    '可选用噻虫嗪或螺虫乙酯轮换防治',
    '定植前清洁棚室\n控制温湿度，减少虫源积累'
  ),
  (
    'thrips',
    '蓟马',
    'MEDIUM',
    '危害花器和嫩叶，造成银白色斑纹、畸形和落花。',
    '使用蓝色粘虫板监测诱捕\n摘除受害严重花叶',
    '释放小花蝽等天敌\n保护捕食螨',
    '可选用乙基多杀菌素或虫螨腈，注意轮换用药',
    '清除杂草寄主\n花期加强巡查'
  ),
  (
    'spider_mite',
    '红蜘蛛',
    'HIGH',
    '高温干旱条件下暴发快，叶片出现失绿斑点和蛛网。',
    '增加叶面湿度\n摘除虫量高的老叶',
    '释放捕食螨\n保护草蛉等天敌',
    '可选用阿维菌素、乙螨唑等药剂轮换防治',
    '避免长期干旱\n定期检查叶背'
  ),
  (
    'armyworm',
    '粘虫',
    'HIGH',
    '幼虫啃食叶片，虫龄增大后食量明显增加。',
    '人工摘除卵块和低龄幼虫\n安装诱虫灯监测',
    '使用苏云金杆菌制剂防治低龄幼虫',
    '高虫量时可选用甲维盐类药剂',
    '加强田间巡查\n集中处理杂草和残株'
  )
ON DUPLICATE KEY UPDATE
  pest_name = VALUES(pest_name),
  danger_level = VALUES(danger_level),
  description = VALUES(description),
  physical_control = VALUES(physical_control),
  biological_control = VALUES(biological_control),
  chemical_control = VALUES(chemical_control),
  prevention = VALUES(prevention);

INSERT INTO water_usage_limit (
  plot_id,
  daily_limit,
  monthly_limit,
  single_limit,
  alert_percent,
  min_effective_duration
)
VALUES
  (1, 200.00, 3000.00, 50.00, 80.00, 10),
  (2, 180.00, 2600.00, 45.00, 80.00, 10),
  (3, 300.00, 5000.00, 80.00, 85.00, 10)
ON DUPLICATE KEY UPDATE
  daily_limit = VALUES(daily_limit),
  monthly_limit = VALUES(monthly_limit),
  single_limit = VALUES(single_limit),
  alert_percent = VALUES(alert_percent),
  min_effective_duration = VALUES(min_effective_duration);

INSERT INTO light_strategy (
  plot_id,
  illuminance_min,
  illuminance_max,
  auto_mode,
  start_time,
  end_time,
  cooldown_minutes
)
VALUES
  (1, 500.00, 800.00, 1, '06:00:00', '20:00:00', 5),
  (2, 450.00, 780.00, 1, '06:00:00', '20:00:00', 5),
  (3, 400.00, 750.00, 0, '06:00:00', '20:00:00', 10)
ON DUPLICATE KEY UPDATE
  illuminance_min = VALUES(illuminance_min),
  illuminance_max = VALUES(illuminance_max),
  auto_mode = VALUES(auto_mode),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  cooldown_minutes = VALUES(cooldown_minutes);
