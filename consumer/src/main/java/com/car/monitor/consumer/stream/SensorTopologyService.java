package com.car.monitor.consumer.stream;

import org.springframework.stereotype.Service;

@Service
public class SensorTopologyService {

    public String describeTopology() {
        return "Kafka Streams topology: sensor.raw -> sensor.processed";
    }
}
