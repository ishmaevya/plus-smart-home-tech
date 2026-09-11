package ru.yandex.practicum.aggregator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.avro.specific.SpecificRecordBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.kafka.telemetry.event.SensorEventAvro;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AggregationStarter {

    private final KafkaConsumer<String, SensorEventAvro> consumer;
    private final KafkaProducer<String, SpecificRecordBase> producer;
    private final AggregatorServiceImpl aggregatorService;

    @Value("${aggregator.kafka.topic-in}")
    private String topicIn;

    @Value("${aggregator.kafka.topic-out}")
    private String topicOut;

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(consumer::wakeup));
        consumer.subscribe(List.of(topicIn));
        log.info("Aggregator запущен. Подписка на топик: {}", topicIn);
        try {
            while (true) {
                ConsumerRecords<String, SensorEventAvro> records = consumer.poll(Duration.ofMillis(100));
                for (ConsumerRecord<String, SensorEventAvro> record : records) {
                    aggregatorService.updateState(record.value())
                            .ifPresent(snapshot -> producer.send(
                                    new ProducerRecord<>(topicOut, snapshot.getHubId(), snapshot)));
                }
                producer.flush();
                consumer.commitSync();
            }
        } catch (WakeupException e) {
            log.info("Получен WakeupException — завершаю poll-loop");
        } finally {
            producer.flush();
            consumer.commitSync();
            producer.close();
            consumer.close();
            log.info("Aggregator остановлен");
        }
    }
}
