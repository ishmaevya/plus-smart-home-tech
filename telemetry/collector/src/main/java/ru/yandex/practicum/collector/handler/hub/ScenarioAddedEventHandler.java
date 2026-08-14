package ru.yandex.practicum.collector.handler.hub;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.model.hub.HubEventType;
import ru.yandex.practicum.collector.model.hub.ScenarioAddedEvent;
import ru.yandex.practicum.collector.model.hub.ScenarioCondition;
import ru.yandex.practicum.collector.model.hub.DeviceAction;
import ru.yandex.practicum.collector.service.KafkaEventProducer;
import ru.yandex.practicum.kafka.telemetry.event.ActionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionOperationAvro;
import ru.yandex.practicum.kafka.telemetry.event.ConditionTypeAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScenarioAddedEventHandler implements HubEventHandler {

    private final KafkaEventProducer producer;

    @Override
    public HubEventType getMessageType() {
        return HubEventType.SCENARIO_ADDED;
    }

    @Override
    public void handle(HubEvent event) {
        ScenarioAddedEvent hubEvent = (ScenarioAddedEvent) event;
        List<ScenarioConditionAvro> conditions = hubEvent.getConditions().stream()
                .map(this::toAvroCondition)
                .toList();
        List<DeviceActionAvro> actions = hubEvent.getActions().stream()
                .map(this::toAvroAction)
                .toList();
        ScenarioAddedEventAvro payload = ScenarioAddedEventAvro.newBuilder()
                .setName(hubEvent.getName())
                .setConditions(conditions)
                .setActions(actions)
                .build();
        HubEventAvro hubEventAvro = HubEventAvro.newBuilder()
                .setHubId(hubEvent.getHubId())
                .setTimestamp(hubEvent.getTimestamp())
                .setPayload(payload)
                .build();
        producer.send(hubEventAvro);
    }

    private ScenarioConditionAvro toAvroCondition(ScenarioCondition condition) {
        return ScenarioConditionAvro.newBuilder()
                .setSensorId(condition.getSensorId())
                .setType(ConditionTypeAvro.valueOf(condition.getType().name()))
                .setOperation(ConditionOperationAvro.valueOf(condition.getOperation().name()))
                .setValue(condition.getValue())
                .build();
    }

    private DeviceActionAvro toAvroAction(DeviceAction action) {
        return DeviceActionAvro.newBuilder()
                .setSensorId(action.getSensorId())
                .setType(ActionTypeAvro.valueOf(action.getType().name()))
                .setValue(action.getValue())
                .build();
    }
}