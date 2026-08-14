package ru.yandex.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.kafka.core.KafkaTemplate;
import ru.yandex.practicum.collector.util.AvroSerializer;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    private static final String SENSOR_EVENTS_TOPIC = "telemetry.sensors.v1";
    private static final String HUBS_EVENTS_TOPIC = "telemetry.hubs.v1";

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final AvroSerializer avroSerializer;

    public void send(SensorEventAvro event) {
        kafkaTemplate.send(SENSOR_EVENTS_TOPIC, event.getId(), avroSerializer.serialize(event));
    }

    public void send(HubEventAvro event) {
        kafkaTemplate.send(HUBS_EVENTS_TOPIC, event.getHubId(), avroSerializer.serialize(event));
    }
}