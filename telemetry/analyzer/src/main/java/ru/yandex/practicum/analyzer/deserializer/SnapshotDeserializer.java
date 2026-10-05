package ru.yandex.practicum.analyzer.deserializer;

import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.serialization.BaseAvroDeserializer;

public class SnapshotDeserializer extends BaseAvroDeserializer<SensorsSnapshotAvro> {

    public SnapshotDeserializer() {
        super(SensorsSnapshotAvro.getClassSchema());
    }
}