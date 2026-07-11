CREATE DATABASE IF NOT EXISTS smart_agriculture
DEFAULT CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE smart_agriculture;

CREATE TABLE IF NOT EXISTS user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(50),
  role VARCHAR(20) DEFAULT 'USER',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plot (
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

CREATE TABLE IF NOT EXISTS device (
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

CREATE TABLE IF NOT EXISTS telemetry_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL,
  device_id BIGINT,
  device_code VARCHAR(50),
  soil_moisture DECIMAL(5,2),
  air_temperature DECIMAL(5,2),
  air_humidity DECIMAL(5,2),
  illuminance DECIMAL(10,2),
  pump_status VARCHAR(20),
  light_status VARCHAR(20),
  collected_at DATETIME NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS control_command (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  command_no VARCHAR(50) NOT NULL UNIQUE,
  plot_id BIGINT,
  device_id BIGINT,
  device_code VARCHAR(50) NOT NULL,
  command_type VARCHAR(50) NOT NULL,
  command_value VARCHAR(100),
  duration_seconds INT,
  brightness INT,
  status VARCHAR(20) DEFAULT 'PENDING',
  request_source VARCHAR(20) DEFAULT 'WEB',
  error_message VARCHAR(255),
  sent_at DATETIME,
  ack_at DATETIME,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS irrigation_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT,
  device_id BIGINT,
  device_code VARCHAR(50),
  command_id BIGINT,
  start_time DATETIME NOT NULL,
  end_time DATETIME,
  duration_seconds INT,
  water_amount DECIMAL(10,2),
  status VARCHAR(20) DEFAULT 'RUNNING',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS irrigation_strategy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL COMMENT 'plot id',
  moisture_min DECIMAL(10,2) DEFAULT 40.00 COMMENT 'minimum soil moisture',
  moisture_max DECIMAL(10,2) DEFAULT 70.00 COMMENT 'maximum soil moisture',
  consecutive_threshold INT DEFAULT 3 COMMENT 'continuous threshold count',
  auto_mode TINYINT(1) DEFAULT 1 COMMENT 'enable automatic rule',
  max_duration INT DEFAULT 900 COMMENT 'maximum irrigation duration in seconds',
  cooldown_minutes INT DEFAULT 10 COMMENT 'cooldown minutes',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_irrigation_strategy_plot_id (plot_id)
);

CREATE TABLE IF NOT EXISTS alarm (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT,
  device_id BIGINT,
  alarm_type VARCHAR(50) NOT NULL,
  severity VARCHAR(20) DEFAULT 'WARNING',
  trigger_value DECIMAL(10,2),
  threshold_value DECIMAL(10,2),
  status VARCHAR(20) DEFAULT 'ACTIVE',
  message VARCHAR(255),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  resolve_time DATETIME
);

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

CREATE TABLE IF NOT EXISTS light_strategy (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT NOT NULL UNIQUE,
  illuminance_min DECIMAL(10,2) DEFAULT 500.00,
  illuminance_max DECIMAL(10,2) DEFAULT 800.00,
  auto_mode TINYINT(1) DEFAULT 1,
  start_time TIME DEFAULT '06:00:00',
  end_time TIME DEFAULT '20:00:00',
  cooldown_minutes INT DEFAULT 5,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS pest_detection_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  plot_id BIGINT,
  file_name VARCHAR(255),
  pest_id VARCHAR(50),
  pest_name VARCHAR(100),
  danger_level VARCHAR(20),
  confidence DECIMAL(6,4),
  model_status VARCHAR(50),
  detected_at DATETIME NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_pest_detection_plot_time (plot_id, detected_at),
  INDEX idx_pest_detection_pest_id (pest_id)
);
