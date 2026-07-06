package com.agriculture.service;

import com.agriculture.dto.LightControlDTO;
import com.agriculture.entity.TelemetryData;
import com.agriculture.vo.CommandVO;
import com.agriculture.vo.LightStatusVO;

public interface LightControlService {

    CommandVO control(LightControlDTO dto);

    void handleTelemetry(TelemetryData telemetry);

    LightStatusVO getStatus(Long plotId, String deviceCode);
}
