package com.echogallery.experiment;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardRelationRepository extends JpaRepository<CardRelation, Long> {

    @EntityGraph(attributePaths = { "experiment", "sourceCard", "derivedCard" })
    List<CardRelation> findBySourceCardIdAndExperimentUserIdOrderByCreatedAtDesc(Long cardId, Long userId);

    @EntityGraph(attributePaths = { "experiment", "sourceCard", "derivedCard" })
    List<CardRelation> findByDerivedCardIdAndExperimentUserIdOrderByCreatedAtDesc(Long cardId, Long userId);

    @Modifying
    @Query("""
            DELETE FROM CardRelation relation
            WHERE relation.experiment.id = :experimentId
              AND (relation.sourceCard.id = :cardId OR relation.derivedCard.id = :cardId)
            """)
    void deleteByExperimentIdAndCardId(@Param("experimentId") Long experimentId, @Param("cardId") Long cardId);

    void deleteByExperimentId(Long experimentId);
}
