package ru.yandex.practicum.analyzer.model.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.yandex.practicum.analyzer.model.id.ScenarioActionId;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "scenario_actions")
public class ScenarioAction {

    @EmbeddedId
    private ScenarioActionId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("scenarioId")
    @JoinColumn(name = "scenario_id")
    private Scenario scenario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("sensorId")
    @JoinColumn(name = "sensor_id")
    private Sensor sensor;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("actionId")
    @JoinColumn(name = "action_id")
    private Action action;

    public ScenarioAction(Scenario scenario, Sensor sensor, Action action) {
        this.scenario = scenario;
        this.sensor = sensor;
        this.action = action;
        this.id = new ScenarioActionId(scenario.getId(), sensor.getId(), action.getId());
    }
}