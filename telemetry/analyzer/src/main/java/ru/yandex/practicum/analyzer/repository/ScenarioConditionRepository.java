package ru.yandex.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.model.entity.ScenarioCondition;
import ru.yandex.practicum.analyzer.model.id.ScenarioConditionId;

import java.util.List;

public interface ScenarioConditionRepository extends JpaRepository<ScenarioCondition, ScenarioConditionId> {

    @Query("select sc from ScenarioCondition sc where sc.scenario.id = :scenarioId")
    List<ScenarioCondition> findByScenarioId(@Param("scenarioId") Long scenarioId);

    @Query("select sc.condition.id from ScenarioCondition sc where sc.scenario.id = :scenarioId")
    List<Long> findConditionIdsByScenarioId(@Param("scenarioId") Long scenarioId);

    @Modifying
    @Transactional
    @Query("delete from ScenarioCondition sc where sc.scenario.id = :scenarioId")
    void deleteByScenarioId(@Param("scenarioId") Long scenarioId);
}