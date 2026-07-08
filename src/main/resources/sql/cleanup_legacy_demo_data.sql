USE smart_agriculture;

-- Clean legacy demo plots/devices that are not connected to the real BearPi hardware.
-- Run this once in Navicat/MySQL after backing up local test data if the frontend should only see the real BearPi plot.

DELETE FROM telemetry_data WHERE plot_id IN (2, 3) OR device_id IN (1, 2, 3, 4, 5);
DELETE FROM control_command WHERE device_code IN ('DEV-A001', 'DEV-A002', 'DEV-A003', 'DEV-B001', 'DEV-C001');
DELETE FROM irrigation_record WHERE plot_id IN (2, 3) OR device_code IN ('DEV-A001', 'DEV-A002', 'DEV-A003', 'DEV-B001', 'DEV-C001');
DELETE FROM irrigation_strategy WHERE plot_id IN (2, 3);
DELETE FROM alarm WHERE plot_id IN (2, 3) OR device_id IN (1, 2, 3, 4, 5);
DELETE FROM water_usage_limit WHERE plot_id IN (2, 3);
DELETE FROM light_strategy WHERE plot_id IN (2, 3);
DELETE FROM pest_detection_record WHERE plot_id IN (2, 3);
DELETE FROM device WHERE id IN (1, 2, 3, 4, 5) OR device_code IN ('DEV-A001', 'DEV-A002', 'DEV-A003', 'DEV-B001', 'DEV-C001');
DELETE FROM plot WHERE id IN (2, 3);

INSERT INTO plot (id, name, crop_type, location, area, status, description)
VALUES (1, 'BearPi Smart Agriculture Plot', 'Tomato', 'Area A', 120.00, 'ONLINE', 'Real BearPi hardware integration plot')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  crop_type = VALUES(crop_type),
  location = VALUES(location),
  area = VALUES(area),
  status = VALUES(status),
  description = VALUES(description);

INSERT INTO device (id, device_code, device_name, device_type, plot_id, status, last_heartbeat, signal_strength, battery)
VALUES (7, '6a44b8fdcbb0cf6bb96ad1a1_bearpi_001', 'BearPi-001', 'BEARPI', 1, 'ONLINE', NOW(), 86, 92)
ON DUPLICATE KEY UPDATE
  device_code = VALUES(device_code),
  device_name = VALUES(device_name),
  device_type = VALUES(device_type),
  plot_id = VALUES(plot_id),
  status = VALUES(status),
  last_heartbeat = VALUES(last_heartbeat),
  signal_strength = VALUES(signal_strength),
  battery = VALUES(battery);
