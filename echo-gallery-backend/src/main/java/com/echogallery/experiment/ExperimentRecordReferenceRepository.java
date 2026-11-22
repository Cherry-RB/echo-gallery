package com.echogallery.experiment;

import java.util.List;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentRecordReferenceRepository extends JpaRepository<ExperimentRecordReference, Long> {
    List<ExperimentRecordReference> findByRecordIdOrderByIdAsc(Long recordId);
    Optional<ExperimentRecordReference> findByIdAndRecordId(Long id, Long recordId);
    boolean existsByCardId(Long cardId);
    @Query("select ref from ExperimentRecordReference ref join fetch ref.record record join fetch record.experiment experiment where ref.card.id = :cardId and experiment.user.id = :owner order by ref.id desc")
    List<ExperimentRecordReference> findCardUsages(@Param("cardId") Long cardId, @Param("owner") Long owner);
    @Query("select ref from ExperimentRecordReference ref join fetch ref.record record join fetch record.experiment experiment join fetch ref.card card " +
            "where ref.kind = com.echogallery.experiment.ExperimentRecordReference.Kind.CARD and experiment.user.id = :userId " +
            "and ref.createdAt >= :startAt and ref.createdAt < :endAt order by ref.createdAt desc, ref.id desc")
    List<ExperimentRecordReference> findRecentCardReferences(@Param("userId") Long userId,
            @Param("startAt") ZonedDateTime startAt, @Param("endAt") ZonedDateTime endAt);
}
