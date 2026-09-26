package com.echogallery.experiment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentExplorationRepository extends JpaRepository<ExperimentExploration, Long> {
    Optional<ExperimentExploration> findByExperimentId(Long experimentId);

    List<ExperimentExploration> findByExperimentIdIn(Collection<Long> experimentIds);

    @Query("""
            SELECT exploration
            FROM ExperimentExploration exploration
            JOIN FETCH exploration.experiment experiment
            WHERE experiment.user.id = :userId
              AND experiment.isArchived = false
              AND exploration.currentTry IS NOT NULL
              AND TRIM(exploration.currentTry) <> ''
            ORDER BY experiment.updatedAt DESC, experiment.id DESC
            """)
    List<ExperimentExploration> findCurrentTriesByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    void deleteByExperimentId(Long experimentId);
}
