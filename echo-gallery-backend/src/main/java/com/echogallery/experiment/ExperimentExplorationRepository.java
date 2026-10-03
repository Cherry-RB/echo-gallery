package com.echogallery.experiment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentExplorationRepository extends JpaRepository<ExperimentExploration, Long> {
    Optional<ExperimentExploration> findByExperimentId(Long experimentId);

    List<ExperimentExploration> findByExperimentIdIn(Collection<Long> experimentIds);

    void deleteByExperimentId(Long experimentId);
}
