package com.echogallery.experiment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentExplorationRepository extends JpaRepository<ExperimentExploration, Long> {
    interface CurrentTry { Long getExperimentId(); String getCurrentTry(); }
    @Query("select e.experiment.id as experimentId, m.content as currentTry from ExperimentExploration e left join e.currentMethod m where e.experiment.id in :ids")
    List<CurrentTry> currentTries(@Param("ids") Collection<Long> ids);
    Optional<ExperimentExploration> findByExperimentId(Long experimentId);

    List<ExperimentExploration> findByExperimentIdIn(Collection<Long> experimentIds);

    void deleteByExperimentId(Long experimentId);
}
