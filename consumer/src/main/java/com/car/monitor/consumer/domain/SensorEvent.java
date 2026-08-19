package com.car.monitor.consumer.domain;

import java.time.Instant;
import java.util.Map;

public record SensorEvent(
        String sensorId,
        String sensorType,
        Instant timestamp,
        Map<String, Object> measurements,
        String source
) {
}
