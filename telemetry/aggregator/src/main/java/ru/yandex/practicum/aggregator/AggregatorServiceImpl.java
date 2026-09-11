package ru.yandex.practicum.aggregator;

import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.kafka.telemetry.event.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class AggregatorServiceImpl {

    private final Map<String, SensorsSnapshotAvro> hubs = new HashMap<>();

    public Optional<SensorsSnapshotAvro> updateState(SensorEventAvro event) {
        String hubId = event.getHubId();
        SensorsSnapshotAvro snapshot = hubs.computeIfAbsent(hubId, id ->
                SensorsSnapshotAvro.newBuilder()
                        .setHubId(id)
                        .setTimestamp(event.getTimestamp())
                        .setSensorsState(new HashMap<>())
                        .build());

        SensorStateAvro sensorState = SensorStateAvro.newBuilder()
                .setTimestamp(event.getTimestamp())
                .setData(extractData(event))
                .build();

        Map<String, SensorStateAvro> updatedSensorsState = new HashMap<>(snapshot.getSensorsState());
        updatedSensorsState.put(event.getId(), sensorState);

        SensorsSnapshotAvro updatedSnapshot = SensorsSnapshotAvro.newBuilder(snapshot)
                .setSensorsState(updatedSensorsState)
                .setTimestamp(event.getTimestamp())
                .build();

        hubs.put(hubId, updatedSnapshot);
        return Optional.of(updatedSnapshot);
    }

    private SpecificRecordBase extractData(SensorEventAvro event) {
        Object payload = event.getPayload();
        if (payload instanceof ClimateSensorAvro climate) return climate;
        if (payload instanceof LightSensorAvro light) return light;
        if (payload instanceof MotionSensorAvro motion) return motion;
        if (payload instanceof SwitchSensorAvro switchSensor) return switchSensor;
        if (payload instanceof TemperatureSensorAvro temperature) return temperature;
        return null;
    }
}
