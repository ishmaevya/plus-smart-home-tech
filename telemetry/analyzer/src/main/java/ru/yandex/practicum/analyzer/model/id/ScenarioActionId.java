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
public class ScenarioActionId implements Serializable {

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "sensor_id")
    private String sensorId;

    @Column(name = "action_id")
    private Long actionId;

    public ScenarioActionId(Long scenarioId, String sensorId, Long actionId) {
        this.scenarioId = scenarioId;
        this.sensorId = sensorId;
        this.actionId = actionId;
    }
}