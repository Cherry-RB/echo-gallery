package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentExplorationRecordRepository extends JpaRepository<ExperimentExplorationRecord, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<ExperimentExplorationRecord> {
    @Override @EntityGraph(attributePaths = {"experiment"})
    org.springframework.data.domain.Page<ExperimentExplorationRecord> findAll(org.springframework.data.jpa.domain.Specification<ExperimentExplorationRecord> spec, org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = { "exports", "exports.card" })
    List<ExperimentExplorationRecord> findByExperimentIdOrderByCreatedAtDescIdDesc(Long experimentId);

    Optional<ExperimentExplorationRecord> findByIdAndExperimentId(Long id, Long experimentId);

    List<ExperimentExplorationRecord> findByExperimentIdAndIdIn(Long experimentId, Collection<Long> ids);

    long countByExperimentId(Long experimentId);

    @Query("""
            SELECT explorationRecord.experiment.id AS experimentId,
                   COUNT(explorationRecord.id) AS recordCount
            FROM ExperimentExplorationRecord explorationRecord
            WHERE explorationRecord.experiment.id IN :experimentIds
            GROUP BY explorationRecord.experiment.id
            """)
    List<ExperimentExplorationRecordCount> countByExperimentIds(@Param("experimentIds") Collection<Long> experimentIds);

    long countByExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    void deleteByExperimentId(Long experimentId);
}
