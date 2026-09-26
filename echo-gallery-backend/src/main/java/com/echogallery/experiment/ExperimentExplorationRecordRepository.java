package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentExplorationRecordRepository extends JpaRepository<ExperimentExplorationRecord, Long> {

    @EntityGraph(attributePaths = { "exports", "exports.card" })
    List<ExperimentExplorationRecord> findByExperimentIdOrderByCreatedAtDescIdDesc(Long experimentId);

    Optional<ExperimentExplorationRecord> findByIdAndExperimentId(Long id, Long experimentId);

    List<ExperimentExplorationRecord> findByExperimentIdAndIdIn(Long experimentId, Collection<Long> ids);

    long countByExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    void deleteByExperimentId(Long experimentId);
}
