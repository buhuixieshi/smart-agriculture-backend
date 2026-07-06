package com.agriculture.service;

import com.agriculture.entity.Device;
import com.agriculture.entity.TelemetryData;

public interface AlarmRuleService {

    void checkTelemetry(TelemetryData telemetryData);

    void handleDeviceOffline(Device device);
}
