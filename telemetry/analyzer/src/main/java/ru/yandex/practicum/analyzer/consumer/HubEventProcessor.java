package ru.yandex.practicum.analyzer.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.configuration.AnalyzerProperties;
import ru.yandex.practicum.analyzer.service.HubEventService;
import ru.yandex.practicum.kafka.telemetry.event.DeviceAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.DeviceRemovedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.HubEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioRemovedEventAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HubEventProcessor implements Runnable {

    private final KafkaConsumer<String, HubEventAvro> consumer;
    private final HubEventService hubEventService;
    private final AnalyzerProperties properties;

    @Override
    public void run() {
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
        consumer.subscribe(List.of(properties.getHubEventsTopic()));
        log.info("HubEventProcessor запущен. Топик: {}", properties.getHubEventsTopic());
        try {
            while (true) {
                ConsumerRecords<String, HubEventAvro> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, HubEventAvro> record : records) {
                    handleEvent(record.value());
                }
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            log.info("Получен WakeupException, завершаю обработку событий хабов");
        } finally {
            consumer.close();
            log.info("Consumer событий хабов закрыт");
        }
    }

    private void handleEvent(HubEventAvro event) {
        Object payload = event.getPayload();
        log.info("Обрабатываю событие {} от хаба {}", payload.getClass().getSimpleName(), event.getHubId());
        if (payload instanceof DeviceAddedEventAvro added) {
            hubEventService.addDevice(event.getHubId(), added.getId());
        } else if (payload instanceof DeviceRemovedEventAvro removed) {
            hubEventService.removeDevice(removed.getId());
        } else if (payload instanceof ScenarioAddedEventAvro scenarioAdded) {
            hubEventService.addScenario(event.getHubId(), scenarioAdded);
        } else if (payload instanceof ScenarioRemovedEventAvro scenarioRemoved) {
            hubEventService.removeScenario(event.getHubId(), scenarioRemoved.getName());
        }
    }
}