package ru.yandex.practicum.analyzer.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.yandex.practicum.analyzer.model.id.ScenarioConditionId;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenario_conditions")
public class ScenarioCondition {

    @EmbeddedId
    private ScenarioConditionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("scenarioId")
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sensorId")
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("conditionId")
    @JoinColumn(name = "condition_id")
    private Condition condition;

    public ScenarioCondition(Scenario scenario, Sensor sensor, Condition condition) {
        this.scenario = scenario;
        this.sensor = sensor;
        this.condition = condition;
        this.id = new ScenarioConditionId(scenario.getId(), sensor.getId(), condition.getId());
    }
}