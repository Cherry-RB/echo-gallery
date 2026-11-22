package com.echogallery.experiment;

import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ExperimentRecordRepository extends JpaRepository<ExperimentRecord, Long> {
    Optional<ExperimentRecord> findByExperimentIdAndCreateRequestId(Long experimentId, UUID requestId);

    Optional<ExperimentRecord> findByIdAndExperimentIdAndExperimentUserId(Long id, Long experimentId, Long userId);

    Page<ExperimentRecord> findByExperimentIdAndExperimentUserId(Long experimentId, Long userId, Pageable pageable);

    Page<ExperimentRecord> findByExperimentIdAndExperimentUserIdAndPracticeTextIsNotNullOrderByCreatedAtDescIdDesc(
            Long experimentId, Long userId, Pageable pageable);

    java.util.List<ExperimentRecord> findByExperimentIdInOrderByRecordedOnDescIdDesc(Collection<Long> experimentIds);

    @Query("select r from ExperimentRecord r where r.experiment.id = :experimentId and r.experiment.user.id = :userId " +
            "and (:keyword = '' or lower(coalesce(r.title, '')) like lower(concat('%', :keyword, '%')) or lower(coalesce(r.content, '')) like lower(concat('%', :keyword, '%')) or lower(coalesce(r.practiceText, '')) like lower(concat('%', :keyword, '%'))) " +
            "and (:understandingNode is null or r.understandingNode = :understandingNode) " +
            "and (:methodCandidate is null or r.methodCandidate = :methodCandidate) " +
            "and (:highlight is null or r.highlight = :highlight) " +
            "and (:dateFrom is null or r.recordedOn >= :dateFrom) " +
            "and (:dateTo is null or r.recordedOn <= :dateTo) " +
            "and (:topicFilterEmpty = true or " +
            "(:topicMatch = 'ANY' and exists (select 1 from ExperimentRecordTopic rt where rt.record = r and rt.topic.id in :topicIds)) or " +
            "(:topicMatch = 'ALL' and (select count(distinct rt.topic.id) from ExperimentRecordTopic rt where rt.record = r and rt.topic.id in :topicIds) = :topicCount)) " +
            "and (:unclassified is null or :unclassified = false or not exists (select 1 from ExperimentRecordTopic rt where rt.record = r))")
    Page<ExperimentRecord> search(@Param("experimentId") Long experimentId, @Param("userId") Long userId,
            @Param("keyword") String keyword, @Param("understandingNode") Boolean understandingNode,
            @Param("methodCandidate") Boolean methodCandidate, @Param("highlight") ExperimentRecordDraft.Highlight highlight,
            @Param("dateFrom") java.time.LocalDate dateFrom, @Param("dateTo") java.time.LocalDate dateTo,
            @Param("topicIds") Collection<Long> topicIds, @Param("topicFilterEmpty") boolean topicFilterEmpty,
            @Param("topicMatch") String topicMatch, @Param("topicCount") long topicCount,
            @Param("unclassified") Boolean unclassified, Pageable pageable);

    interface RecordCount { Long getExperimentId(); Long getRecordCount(); }

    @Query("select r.experiment.id as experimentId, count(r) as recordCount from ExperimentRecord r where r.experiment.id in :ids group by r.experiment.id")
    java.util.List<RecordCount> countByExperimentIds(@Param("ids") Collection<Long> ids);

    long countByExperimentId(Long experimentId);

    long countByExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId,
            java.time.ZonedDateTime start, java.time.ZonedDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ExperimentRecord r where r.id = :id and r.experiment.id = :experimentId and r.experiment.user.id = :userId")
    Optional<ExperimentRecord> lockOwned(@Param("id") Long id, @Param("experimentId") Long experimentId,
            @Param("userId") Long userId);
}
