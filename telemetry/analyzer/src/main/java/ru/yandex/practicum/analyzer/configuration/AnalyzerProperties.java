package ru.yandex.practicum.analyzer.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "analyzer.kafka")
public class AnalyzerProperties {

    private String snapshotsTopic;
    private String hubEventsTopic;
    private String snapshotsGroup;
    private String hubEventsGroup;
    private String autoOffsetReset = "earliest";
    private boolean enableAutoCommit = false;
}