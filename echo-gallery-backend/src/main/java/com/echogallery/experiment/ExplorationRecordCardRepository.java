package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExplorationRecordCardRepository extends JpaRepository<ExplorationRecordCard, Long> {

    interface ExperimentCardKey {
        Long getExperimentId();

        Long getCardId();
    }

    boolean existsByExplorationRecordIdAndCardId(Long explorationRecordId, Long cardId);

    long countByExplorationRecordExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId, ZonedDateTime startAt, ZonedDateTime endAt);

    @Query("""
            SELECT DISTINCT
                link.explorationRecord.experiment.id AS experimentId,
                link.card.id AS cardId
            FROM ExplorationRecordCard link
            WHERE link.card.id IN :cardIds
              AND link.explorationRecord.experiment.user.id = :userId
            """)
    List<ExperimentCardKey> findExplorationExperimentCardKeys(
            @Param("userId") Long userId,
            @Param("cardIds") Collection<Long> cardIds);
}
