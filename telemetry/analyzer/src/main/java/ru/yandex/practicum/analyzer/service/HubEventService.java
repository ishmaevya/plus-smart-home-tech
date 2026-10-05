package ru.yandex.practicum.analyzer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.model.entity.Action;
import ru.yandex.practicum.analyzer.model.entity.Condition;
import ru.yandex.practicum.analyzer.model.entity.Scenario;
import ru.yandex.practicum.analyzer.model.entity.ScenarioAction;
import ru.yandex.practicum.analyzer.model.entity.ScenarioCondition;
import ru.yandex.practicum.analyzer.model.entity.Sensor;
import ru.yandex.practicum.analyzer.model.enums.ActionType;
import ru.yandex.practicum.analyzer.model.enums.ConditionOperation;
import ru.yandex.practicum.analyzer.model.enums.ConditionType;
import ru.yandex.practicum.analyzer.repository.ActionRepository;
import ru.yandex.practicum.analyzer.repository.ConditionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioActionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioConditionRepository;
import ru.yandex.practicum.analyzer.repository.ScenarioRepository;
import ru.yandex.practicum.analyzer.repository.SensorRepository;
import ru.yandex.practicum.kafka.telemetry.event.DeviceActionAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioAddedEventAvro;
import ru.yandex.practicum.kafka.telemetry.event.ScenarioConditionAvro;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HubEventService {

    private final SensorRepository sensorRepository;
    private final ScenarioRepository scenarioRepository;
    private final ConditionRepository conditionRepository;
    private final ActionRepository actionRepository;
    private final ScenarioConditionRepository scenarioConditionRepository;
    private final ScenarioActionRepository scenarioActionRepository;

    @Transactional
    public void addDevice(String hubId, String deviceId) {
        if (sensorRepository.existsById(deviceId)) {
            log.info("Устройство {} уже зарегистрировано, пропускаю", deviceId);
            return;
        }
        sensorRepository.save(new Sensor(deviceId, hubId));
        log.info("Устройство {} добавлено в хаб {}", deviceId, hubId);
    }

    @Transactional
    public void removeDevice(String deviceId) {
        if (sensorRepository.existsById(deviceId)) {
            sensorRepository.deleteById(deviceId);
            log.info("Устройство {} удалено", deviceId);
        }
    }

    @Transactional
    public void addScenario(String hubId, ScenarioAddedEventAvro event) {
        Optional<Scenario> existing = scenarioRepository.findByHubIdAndName(hubId, event.getName());
        if (existing.isPresent()) {
            deleteScenario(existing.get().getId());
        }
        Scenario scenario = scenarioRepository.save(new Scenario(hubId, event.getName()));
        saveConditions(scenario, event.getConditions());
        saveActions(scenario, event.getActions());
        log.info("Сценарий {} хаба {} сохранён", scenario.getName(), hubId);
    }

    @Transactional
    public void removeScenario(String hubId, String name) {
        Optional<Scenario> existing = scenarioRepository.findByHubIdAndName(hubId, name);
        if (existing.isEmpty()) {
            return;
        }
        deleteScenario(existing.get().getId());
        log.info("Сценарий {} хаба {} удалён", name, hubId);
    }

    private void deleteScenario(Long scenarioId) {
        List<Long> conditionIds = scenarioConditionRepository.findConditionIdsByScenarioId(scenarioId);
        List<Long> actionIds = scenarioActionRepository.findActionIdsByScenarioId(scenarioId);
        scenarioConditionRepository.deleteByScenarioId(scenarioId);
        scenarioActionRepository.deleteByScenarioId(scenarioId);
        scenarioRepository.deleteById(scenarioId);
        if (!conditionIds.isEmpty()) {
            conditionRepository.deleteAllById(conditionIds);
        }
        if (!actionIds.isEmpty()) {
            actionRepository.deleteAllById(actionIds);
        }
    }

    private void saveConditions(Scenario scenario, List<ScenarioConditionAvro> conditions) {
        for (ScenarioConditionAvro conditionAvro : conditions) {
            Sensor sensor = requireSensor(scenario.getHubId(), conditionAvro.getSensorId());
            Condition condition = conditionRepository.save(new Condition(
                    ConditionType.valueOf(conditionAvro.getType().name()),
                    ConditionOperation.valueOf(conditionAvro.getOperation().name()),
                    toInt(conditionAvro.getValue())));
            scenarioConditionRepository.save(new ScenarioCondition(scenario, sensor, condition));
        }
    }

    private void saveActions(Scenario scenario, List<DeviceActionAvro> actions) {
        for (DeviceActionAvro actionAvro : actions) {
            Sensor sensor = requireSensor(scenario.getHubId(), actionAvro.getSensorId());
            Action action = actionRepository.save(new Action(
                    ActionType.valueOf(actionAvro.getType().name()),
                    toInt(actionAvro.getValue())));
            scenarioActionRepository.save(new ScenarioAction(scenario, sensor, action));
        }
    }

    private Sensor requireSensor(String hubId, String sensorId) {
        return sensorRepository.findByIdAndHubId(sensorId, hubId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Устройство " + sensorId + " не зарегистрировано в хабе " + hubId));
    }

    private Integer toInt(Object value) {
        if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        return null;
    }
}