package ru.yandex.practicum.collector.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducer {

    public static final String SENSOR_EVENTS_TOPIC = "telemetry.sensors.v1";
    public static final String HUBS_EVENTS_TOPIC = "telemetry.hubs.v1";

    private final KafkaTemplate<String, SpecificRecordBase> kafkaTemplate;

    public void send(SpecificRecordBase event, String topic) {
        ProducerRecord<String, SpecificRecordBase> record = new ProducerRecord<>(
                topic,
                null,
                resolveTimestamp(event).toEpochMilli(),
                resolveHubId(event),
                event
        );
        SendResult<String, SpecificRecordBase> result = sendAndGetResult(record);
        log.info("Событие {} успешно сохранено в топик {} в партицию {} со смещением {}",
                event.getClass().getSimpleName(), result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
    }

    private String resolveHubId(SpecificRecordBase event) {
        if (event instanceof SensorEventAvro sensorEvent) {
            return sensorEvent.getHubId();
        }
        if (event instanceof HubEventAvro hubEvent) {
            return hubEvent.getHubId();
        }
        throw new IllegalArgumentException("Неизвестный тип события: " + event.getClass().getSimpleName());
    }

    private Instant resolveTimestamp(SpecificRecordBase event) {
        if (event instanceof SensorEventAvro sensorEvent) {
            return sensorEvent.getTimestamp();
        }
        if (event instanceof HubEventAvro hubEvent) {
            return hubEvent.getTimestamp();
        }
        throw new IllegalArgumentException("Неизвестный тип события: " + event.getClass().getSimpleName());
    }

    private SendResult<String, SpecificRecordBase> sendAndGetResult(ProducerRecord<String, SpecificRecordBase> record) {
        try {
            SendResult<String, SpecificRecordBase> result = kafkaTemplate.send(record).get();
            kafkaTemplate.flush();
            return result;
        } catch (InterruptedException | ExecutionException e) {
            log.warn("Не удалось записать событие в топик {}", record.topic(), e);
            throw new RuntimeException("Ошибка отправки события в Kafka", e);
        }
    }
}
