package com.echogallery.experiment;
import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExperimentExplorationRecordRepository extends JpaRepository<ExperimentExplorationRecord, Long> {
    Optional<ExperimentExplorationRecord> findByIdAndExperimentId(Long id, Long experimentId);
    Optional<ExperimentExplorationRecord> findByIdAndThreadId(Long id, Long threadId);
    @EntityGraph(attributePaths = {"method", "thread"})
    List<ExperimentExplorationRecord> findByExperimentIdAndIdIn(Long experimentId, Collection<Long> ids);
    @EntityGraph(attributePaths = {"method", "thread"})
    List<ExperimentExplorationRecord> findByMethodIdOrderByOccurredAtAscIdAsc(Long methodId);
    List<ExperimentExplorationRecord> findByThreadIdOrderByOccurredAtAscIdAsc(Long threadId);
    long countByExperimentId(Long id);
    long countByThreadId(Long id);
    long countByMethodId(Long id);
    long countByThreadIdAndMethodIsNull(Long id);
    interface RecordRow {
        Long getId(); Long getThreadId(); String getThreadName(); Long getMethodId(); String getTryText(); String getDiscovery();
        long getVersion(); ZonedDateTime getCreatedAt(); ZonedDateTime getUpdatedAt(); ZonedDateTime getOccurredAt();
    }
    @Query("select r.id as id, r.thread.id as threadId, r.thread.name as threadName, m.id as methodId, m.content as tryText, r.discovery as discovery, r.version as version, r.createdAt as createdAt, r.updatedAt as updatedAt, r.occurredAt as occurredAt from ExperimentExplorationRecord r left join r.method m where r.experiment.id = :id and (:threadId is null or r.thread.id = :threadId) and (:methodId is null or m.id = :methodId) and (:unassigned = false or m.id is null) order by r.occurredAt desc, r.id desc")
    Page<RecordRow> page(@Param("id") Long id, @Param("threadId") Long threadId, @Param("methodId") Long methodId, @Param("unassigned") boolean unassigned, Pageable pageable);
    @Query("select r.experiment.id as experimentId, count(r) as recordCount from ExperimentExplorationRecord r where r.experiment.id in :ids group by r.experiment.id")
    List<ExperimentExplorationRecordCount> countByExperimentIds(@Param("ids") Collection<Long> ids);
    @Query("select r.thread.id, count(r) from ExperimentExplorationRecord r where r.thread.id in :ids group by r.thread.id")
    List<Object[]> countByThreadIds(@Param("ids") List<Long> ids);
    @Query(value = """
        select thread_id, id, content, discovery, occurred_at, created_at from (
          select r.thread_id, r.id, m.content, r.discovery, r.occurred_at, r.created_at,
            row_number() over(partition by r.thread_id order by r.occurred_at desc, r.id desc) n
          from experiment_exploration_records r left join exploration_thread_methods m on m.id = r.method_id where r.thread_id in (:ids)
        ) p where n <= 3 order by thread_id, n
        """, nativeQuery = true)
    List<Object[]> findRecentPreviewsByThreadIds(@Param("ids") List<Long> ids);
    long countByExperimentUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long owner, ZonedDateTime start, ZonedDateTime end);
    void deleteByExperimentId(Long experimentId);
}
