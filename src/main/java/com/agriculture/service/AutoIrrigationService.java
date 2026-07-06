package com.agriculture.service;

import com.agriculture.entity.TelemetryData;

public interface AutoIrrigationService {

    void handleTelemetry(TelemetryData telemetryData);

    void checkRunningIrrigationTimeouts();
}
