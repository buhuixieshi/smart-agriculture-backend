package com.agriculture.service;

import com.agriculture.entity.Alarm;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AlarmService extends IService<Alarm> {

    List<Alarm> query(Long plotId, Long deviceId, String alarmType, String status, String severity);

    Alarm acknowledge(Long id);

    Alarm close(Long id);

    Alarm recover(Long id);

    void recoverActiveDeviceOffline(Long deviceId);
}
