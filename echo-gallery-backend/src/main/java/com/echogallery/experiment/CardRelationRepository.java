package com.echogallery.experiment;

import java.util.List;
import java.time.ZonedDateTime;

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

    @Query("""
            SELECT COUNT(DISTINCT relation.derivedCard.id)
            FROM CardRelation relation
            WHERE relation.experiment.user.id = :userId
              AND relation.relationType = :relationType
              AND relation.createdAt >= :startAt
              AND relation.createdAt < :endAt
            """)
    long countDistinctDerivedCardsByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("relationType") CardRelationType relationType,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt);

    @Query("""
            SELECT COUNT(DISTINCT relation.sourceCard.id)
            FROM CardRelation relation
            WHERE relation.experiment.user.id = :userId
              AND relation.relationType = :relationType
              AND relation.createdAt >= :startAt
              AND relation.createdAt < :endAt
            """)
    long countDistinctSourceCardsByUserIdAndCreatedAtBetween(
            @Param("userId") Long userId,
            @Param("relationType") CardRelationType relationType,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt);

    @EntityGraph(attributePaths = {
            "experiment", "sourceCard", "derivedCard", "derivedCard.tags"
    })
    @Query("""
            SELECT relation
            FROM CardRelation relation
            WHERE relation.experiment.user.id = :userId
              AND relation.relationType = :relationType
              AND relation.createdAt >= :startAt
              AND relation.createdAt < :endAt
            ORDER BY relation.createdAt DESC, relation.id DESC
            """)
    List<CardRelation> findRecentDerivedRelationsByUserId(
            @Param("userId") Long userId,
            @Param("relationType") CardRelationType relationType,
            @Param("startAt") ZonedDateTime startAt,
            @Param("endAt") ZonedDateTime endAt);

    @Modifying
    @Query("""
            DELETE FROM CardRelation relation
            WHERE relation.experiment.id = :experimentId
              AND (relation.sourceCard.id = :cardId OR relation.derivedCard.id = :cardId)
            """)
    void deleteByExperimentIdAndCardId(@Param("experimentId") Long experimentId, @Param("cardId") Long cardId);

    void deleteByExperimentId(Long experimentId);
}
