package ru.yandex.practicum.collector.service;

import org.springframework.stereotype.Service;
import ru.yandex.practicum.collector.handler.hub.HubEventHandler;
import ru.yandex.practicum.collector.handler.sensor.SensorEventHandler;
import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.model.hub.HubEventType;
import ru.yandex.practicum.collector.model.sensor.SensorEvent;
import ru.yandex.practicum.collector.model.sensor.SensorEventType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class EventService {

    private final Map<SensorEventType, SensorEventHandler> sensorEventHandlers;
    private final Map<HubEventType, HubEventHandler> hubEventHandlers;

    public EventService(List<SensorEventHandler> sensorEventHandlerList,
                        List<HubEventHandler> hubEventHandlerList) {
        this.sensorEventHandlers = sensorEventHandlerList.stream()
                .collect(Collectors.toMap(SensorEventHandler::getMessageType, Function.identity()));
        this.hubEventHandlers = hubEventHandlerList.stream()
                .collect(Collectors.toMap(HubEventHandler::getMessageType, Function.identity()));
    }

    public void collectSensorEvent(SensorEvent event) {
        SensorEventHandler handler = sensorEventHandlers.get(event.getType());
        if (handler == null) {
            throw new IllegalArgumentException("Не найден обработчик для сенсора типа: " + event.getType());
        }
        handler.handle(event);
    }

    public void collectHubEvent(HubEvent event) {
        HubEventHandler handler = hubEventHandlers.get(event.getType());
        if (handler == null) {
            throw new IllegalArgumentException("Не найден обработчик для события хаба типа: " + event.getType());
        }
        handler.handle(event);
    }
}