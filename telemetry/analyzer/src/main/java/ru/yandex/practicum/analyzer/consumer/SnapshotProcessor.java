package ru.yandex.practicum.analyzer.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.analyzer.configuration.AnalyzerProperties;
import ru.yandex.practicum.analyzer.service.AnalyzerService;
import ru.yandex.practicum.kafka.telemetry.event.SensorsSnapshotAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotProcessor {

    private final KafkaConsumer<String, SensorsSnapshotAvro> consumer;
    private final AnalyzerService analyzerService;
    private final AnalyzerProperties properties;

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
        consumer.subscribe(List.of(properties.getSnapshotsTopic()));
        log.info("SnapshotProcessor запущен. Топик: {}", properties.getSnapshotsTopic());
        try {
            while (true) {
                ConsumerRecords<String, SensorsSnapshotAvro> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, SensorsSnapshotAvro> record : records) {
                    analyzerService.analyze(record.value());
                }
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            log.info("Получен WakeupException, завершаю обработку снапшотов");
        } finally {
            consumer.close();
            log.info("Consumer снапшотов закрыт");
        }
    }
}