package ru.yandex.practicum.analyzer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.analyzer.model.entity.ScenarioAction;
import ru.yandex.practicum.analyzer.model.id.ScenarioActionId;

import java.util.List;

public interface ScenarioActionRepository extends JpaRepository<ScenarioAction, ScenarioActionId> {

    @Query("select sc from ScenarioAction sc where sc.scenario.id = :scenarioId")
    List<ScenarioAction> findByScenarioId(@Param("scenarioId") Long scenarioId);

    @Query("select sc.action.id from ScenarioAction sc where sc.scenario.id = :scenarioId")
    List<Long> findActionIdsByScenarioId(@Param("scenarioId") Long scenarioId);

    @Modifying
    @Transactional
    @Query("delete from ScenarioAction sc where sc.scenario.id = :scenarioId")
    void deleteByScenarioId(@Param("scenarioId") Long scenarioId);
}