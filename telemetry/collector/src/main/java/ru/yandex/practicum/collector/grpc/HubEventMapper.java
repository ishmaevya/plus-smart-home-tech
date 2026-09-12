package ru.yandex.practicum.collector.grpc;

import com.google.protobuf.Timestamp;
import ru.yandex.practicum.collector.model.hub.ActionType;
import ru.yandex.practicum.collector.model.hub.ConditionOperation;
import ru.yandex.practicum.collector.model.hub.ConditionType;
import ru.yandex.practicum.collector.model.hub.DeviceAction;
import ru.yandex.practicum.collector.model.hub.DeviceAddedEvent;
import ru.yandex.practicum.collector.model.hub.DeviceRemovedEvent;
import ru.yandex.practicum.collector.model.hub.DeviceType;
import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.model.hub.ScenarioAddedEvent;
import ru.yandex.practicum.collector.model.hub.ScenarioCondition;
import ru.yandex.practicum.collector.model.hub.ScenarioRemovedEvent;
import ru.yandex.practicum.grpc.telemetry.event.DeviceActionProto;
import ru.yandex.practicum.grpc.telemetry.event.HubEventProto;
import ru.yandex.practicum.grpc.telemetry.event.ScenarioConditionProto;

import java.time.Instant;
import java.util.List;

public final class HubEventMapper {

    private HubEventMapper() {
    }

    public static HubEvent toHubEvent(HubEventProto proto) {
        HubEvent event;
        switch (proto.getPayloadCase()) {
            case DEVICE_ADDED -> {
                event = new DeviceAddedEvent();
                var payload = proto.getDeviceAdded();
                ((DeviceAddedEvent) event).setId(payload.getId());
                ((DeviceAddedEvent) event).setDeviceType(DeviceType.valueOf(payload.getType().name()));
            }
            case DEVICE_REMOVED -> {
                event = new DeviceRemovedEvent();
                ((DeviceRemovedEvent) event).setId(proto.getDeviceRemoved().getId());
            }
            case SCENARIO_ADDED -> {
                event = new ScenarioAddedEvent();
                var payload = proto.getScenarioAdded();
                ((ScenarioAddedEvent) event).setName(payload.getName());
                ((ScenarioAddedEvent) event).setConditions(
                        payload.getConditionList().stream().map(HubEventMapper::toScenarioCondition).toList());
                ((ScenarioAddedEvent) event).setActions(
                        payload.getActionList().stream().map(HubEventMapper::toDeviceAction).toList());
            }
            case SCENARIO_REMOVED -> {
                event = new ScenarioRemovedEvent();
                ((ScenarioRemovedEvent) event).setName(proto.getScenarioRemoved().getName());
            }
            default -> throw new IllegalArgumentException(
                    "Неизвестный тип события хаба: " + proto.getPayloadCase());
        }
        event.setHubId(proto.getHubId());
        event.setTimestamp(resolveTimestamp(proto));
        return event;
    }

    private static ScenarioCondition toScenarioCondition(ScenarioConditionProto proto) {
        ScenarioCondition condition = new ScenarioCondition();
        condition.setSensorId(proto.getSensorId());
        condition.setType(ConditionType.valueOf(proto.getType().name()));
        condition.setOperation(ConditionOperation.valueOf(proto.getOperation().name()));
        condition.setValue(resolveValue(proto));
        return condition;
    }

    private static Integer resolveValue(ScenarioConditionProto proto) {
        if (proto.hasBoolValue()) {
            return proto.getBoolValue() ? 1 : 0;
        }
        if (proto.hasIntValue()) {
            return proto.getIntValue();
        }
        return null;
    }

    private static DeviceAction toDeviceAction(DeviceActionProto proto) {
        DeviceAction action = new DeviceAction();
        action.setSensorId(proto.getSensorId());
        action.setType(ActionType.valueOf(proto.getType().name()));
        action.setValue(proto.hasValue() ? proto.getValue() : null);
        return action;
    }

    private static Instant resolveTimestamp(HubEventProto proto) {
        if (proto.hasTimestamp()) {
            return toInstant(proto.getTimestamp());
        }
        return Instant.now();
    }

    private static Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}