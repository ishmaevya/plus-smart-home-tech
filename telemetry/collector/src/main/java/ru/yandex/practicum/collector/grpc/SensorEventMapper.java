package ru.yandex.practicum.collector.grpc;

import com.google.protobuf.Timestamp;
import ru.yandex.practicum.collector.model.sensor.ClimateSensorEvent;
import ru.yandex.practicum.collector.model.sensor.LightSensorEvent;
import ru.yandex.practicum.collector.model.sensor.MotionSensorEvent;
import ru.yandex.practicum.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.collector.model.sensor.SwitchSensorEvent;
import ru.yandex.practicum.collector.model.sensor.TemperatureSensorEvent;
import ru.yandex.practicum.grpc.telemetry.event.SensorEventProto;

import java.time.Instant;

public final class SensorEventMapper {

    private SensorEventMapper() {
    }

    public static SensorEvent toSensorEvent(SensorEventProto proto) {
        SensorEvent event;
        switch (proto.getPayloadCase()) {
            case MOTION_SENSOR -> {
                event = new MotionSensorEvent();
                var payload = proto.getMotionSensor();
                ((MotionSensorEvent) event).setLinkQuality(payload.getLinkQuality());
                ((MotionSensorEvent) event).setMotion(payload.getMotion());
                ((MotionSensorEvent) event).setVoltage(payload.getVoltage());
            }
            case TEMPERATURE_SENSOR -> {
                event = new TemperatureSensorEvent();
                var payload = proto.getTemperatureSensor();
                ((TemperatureSensorEvent) event).setTemperatureC(payload.getTemperatureC());
                ((TemperatureSensorEvent) event).setTemperatureF(payload.getTemperatureF());
            }
            case LIGHT_SENSOR -> {
                event = new LightSensorEvent();
                var payload = proto.getLightSensor();
                ((LightSensorEvent) event).setLinkQuality(payload.getLinkQuality());
                ((LightSensorEvent) event).setLuminosity(payload.getLuminosity());
            }
            case CLIMATE_SENSOR -> {
                event = new ClimateSensorEvent();
                var payload = proto.getClimateSensor();
                ((ClimateSensorEvent) event).setTemperatureC(payload.getTemperatureC());
                ((ClimateSensorEvent) event).setHumidity(payload.getHumidity());
                ((ClimateSensorEvent) event).setCo2Level(payload.getCo2Level());
            }
            case SWITCH_SENSOR -> {
                event = new SwitchSensorEvent();
                ((SwitchSensorEvent) event).setState(proto.getSwitchSensor().getState());
            }
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события датчика: " + proto.getPayloadCase());
        }
        event.setId(proto.getId());
        event.setHubId(proto.getHubId());
        event.setTimestamp(resolveTimestamp(proto));
        return event;
    }

    private static Instant resolveTimestamp(SensorEventProto proto) {
        if (proto.hasTimestamp()) {
            return toInstant(proto.getTimestamp());
        }
        return Instant.now();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}