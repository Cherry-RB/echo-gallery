package com.echogallery.experiment;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentCardRepository extends JpaRepository<ExperimentCard, Long> {

    boolean existsByExperimentIdAndCardId(Long experimentId, Long cardId);

    Optional<ExperimentCard> findByExperimentIdAndCardId(Long experimentId, Long cardId);

    @EntityGraph(attributePaths = { "card", "card.tags" })
    Page<ExperimentCard> findByExperimentIdAndStageOrderByAddedAtDescIdDesc(Long experimentId, ExperimentStage stage, Pageable pageable);

    @EntityGraph(attributePaths = "experiment")
    List<ExperimentCard> findByCardIdAndExperimentUserIdOrderByAddedAtDesc(Long cardId, Long userId);

    List<ExperimentCard> findByExperimentIdAndCardIdIn(Long experimentId, Collection<Long> cardIds);

    void deleteByExperimentId(Long experimentId);

    @Query("""
            SELECT gc.experiment.id AS experimentId,
                   COALESCE(SUM(CASE WHEN gc.stage = com.echogallery.experiment.ExperimentStage.SEED THEN 1 ELSE 0 END), 0) AS seedCount,
                   COALESCE(SUM(CASE WHEN gc.stage = com.echogallery.experiment.ExperimentStage.GROWING THEN 1 ELSE 0 END), 0) AS growingCount,
                   COALESCE(SUM(CASE WHEN gc.stage = com.echogallery.experiment.ExperimentStage.MATURE THEN 1 ELSE 0 END), 0) AS matureCount
            FROM ExperimentCard gc
            WHERE gc.experiment.id IN :experimentIds
            GROUP BY gc.experiment.id
            """)
    List<ExperimentStageCount> countByExperimentIds(@Param("experimentIds") Collection<Long> experimentIds);
}
