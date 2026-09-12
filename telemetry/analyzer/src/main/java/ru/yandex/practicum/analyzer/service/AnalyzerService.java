package ru.yandex.practicum.analyzer.service;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.model.entity.Action;
import ru.yandex.practicum.analyzer.model.entity.Condition;
import ru.yandex.practicum.analyzer.model.entity.Scenario;
import ru.yandex.practicum.analyzer.model.entity.ScenarioAction;
import ru.yandex.practicum.analyzer.model.entity.ScenarioCondition;
import ru.yandex.practicum.analyzer.model.enums.ConditionType;
import ru.yandex.practicum.analyzer.repository.ScenarioActionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioConditionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.grpc.telemetry.event.ActionTypeProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionRequest;
import ru.yandex.practicum.grpc.telemetry.hubrouter.HubRouterControllerGrpc;
import ru.yandex.practicum.kafka.telemetry.event.ClimateSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.LightSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.MotionSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorStateAvro;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;
import ru.yandex.practicum.kafka.telemetry.event.SwitchSensorAvro;
import ru.yandex.practicum.kafka.telemetry.event.TemperatureSensorAvro;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;

@Slf4j
@Service
public class AnalyzerService {

    private final ScenarioRepository scenarioRepository;
    private final ScenarioConditionRepository scenarioConditionRepository;
    private final ScenarioActionRepository scenarioActionRepository;
    private final HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient;

    public AnalyzerService(ScenarioRepository scenarioRepository,
                           ScenarioConditionRepository scenarioConditionRepository,
                           ScenarioActionRepository scenarioActionRepository,
                           @GrpcClient("hub-router")
                           HubRouterControllerGrpc.HubRouterControllerBlockingStub hubRouterClient) {
        this.scenarioRepository = scenarioRepository;
        this.scenarioConditionRepository = scenarioConditionRepository;
        this.scenarioActionRepository = scenarioActionRepository;
        this.hubRouterClient = hubRouterClient;
    }

    @Transactional(readOnly = true)
    public void analyze(SensorsSnapshotAvro snapshot) {
        String hubId = snapshot.getHubId();
        List<Scenario> scenarios = scenarioRepository.findByHubId(hubId);
        for (Scenario scenario : scenarios) {
            if (matchesConditions(scenario, snapshot)) {
                log.info("Сценарий '{}' хаба {} выполняется", scenario.getName(), hubId);
                executeActions(scenario, snapshot);
            }
        }
    }

    private boolean matchesConditions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        List<ScenarioCondition> scenarioConditions = scenarioConditionRepository.findByScenarioId(scenario.getId());
        for (ScenarioCondition scenarioCondition : scenarioConditions) {
            if (!matchesCondition(scenarioCondition, snapshot)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesCondition(ScenarioCondition scenarioCondition, SensorsSnapshotAvro snapshot) {
        SensorStateAvro state = snapshot.getSensorsState().get(scenarioCondition.getSensor().getId());
        if (state == null || state.getData() == null) {
            return false;
        }
        Condition condition = scenarioCondition.getCondition();
        OptionalInt actualValue = extractSensorValue(state.getData(), condition.getType());
        if (actualValue.isEmpty() || condition.getValue() == null) {
            return false;
        }
        int expectedValue = condition.getValue();
        return switch (condition.getOperation()) {
            case EQUALS -> actualValue.getAsInt() == expectedValue;
            case GREATER_THAN -> actualValue.getAsInt() > expectedValue;
            case LOWER_THAN -> actualValue.getAsInt() < expectedValue;
        };
    }

    private OptionalInt extractSensorValue(Object data, ConditionType type) {
        switch (type) {
            case TEMPERATURE -> {
                if (data instanceof ClimateSensorAvro climate) {
                    return OptionalInt.of(climate.getTemperatureC());
                }
                if (data instanceof TemperatureSensorAvro temperature) {
                    return OptionalInt.of(temperature.getTemperatureC());
                }
            }
            case HUMIDITY -> {
                if (data instanceof ClimateSensorAvro climate) {
                    return OptionalInt.of(climate.getHumidity());
                }
            }
            case CO2LEVEL -> {
                if (data instanceof ClimateSensorAvro climate) {
                    return OptionalInt.of(climate.getCo2Level());
                }
            }
            case LUMINOSITY -> {
                if (data instanceof LightSensorAvro light) {
                    return OptionalInt.of(light.getLuminosity());
                }
            }
            case MOTION -> {
                if (data instanceof MotionSensorAvro motion) {
                    return OptionalInt.of(motion.getMotion() ? 1 : 0);
                }
            }
            case SWITCH -> {
                if (data instanceof SwitchSensorAvro switchSensor) {
                    return OptionalInt.of(switchSensor.getState() ? 1 : 0);
                }
            }
        }
        return OptionalInt.empty();
    }

    private void executeActions(Scenario scenario, SensorsSnapshotAvro snapshot) {
        List<ScenarioAction> scenarioActions = scenarioActionRepository.findByScenarioId(scenario.getId());
        for (ScenarioAction scenarioAction : scenarioActions) {
            Action action = scenarioAction.getAction();
            DeviceActionProto.Builder actionBuilder = DeviceActionProto.newBuilder()
                    .setSensorId(scenarioAction.getSensor().getId())
                    .setType(ActionTypeProto.valueOf(action.getType().name()));
            if (action.getValue() != null) {
                actionBuilder.setValue(action.getValue());
            }
            DeviceActionRequest request = DeviceActionRequest.newBuilder()
                    .setHubId(snapshot.getHubId())
                    .setScenarioName(scenario.getName())
                    .setAction(actionBuilder.build())
                    .setTimestamp(toProtoTimestamp(snapshot.getTimestamp()))
                    .build();
            hubRouterClient.handleDeviceAction(request);
            log.info("Отправлено действие {} устройства {} по сценарию '{}'",
                    action.getType(), scenarioAction.getSensor().getId(), scenario.getName());
        }
    }

    private Timestamp toProtoTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }
}