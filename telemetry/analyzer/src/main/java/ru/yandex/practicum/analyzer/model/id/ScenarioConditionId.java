package ru.yandex.practicum.analyzer.model.id;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ScenarioConditionId implements Serializable {

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "condition_id")
    private Long conditionId;

    public ScenarioConditionId(Long scenarioId, String sensorId, Long conditionId) {
        this.scenarioId = scenarioId;
        this.sensorId = sensorId;
        this.conditionId = conditionId;
    }
}