USE smart_agriculture;

ALTER TABLE control_command
  ADD COLUMN IF NOT EXISTS duration_seconds INT NULL AFTER command_value,
  ADD COLUMN IF NOT EXISTS brightness INT NULL AFTER duration_seconds;

