package ru.yandex.practicum.collector.handler.hub;

import ru.yandex.practicum.collector.model.hub.HubEvent;
import ru.yandex.practicum.collector.model.hub.HubEventType;

public interface HubEventHandler {

    HubEventType getMessageType();

    void handle(HubEvent event);
}