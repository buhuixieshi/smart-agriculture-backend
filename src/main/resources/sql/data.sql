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
