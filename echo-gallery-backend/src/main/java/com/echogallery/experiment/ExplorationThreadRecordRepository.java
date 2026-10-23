package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExplorationThreadRecordRepository extends JpaRepository<ExplorationThreadRecord, Long> {
    List<ExplorationThreadRecord> findByThreadIdOrderBySortOrderAscIdAsc(Long threadId);
    Optional<ExplorationThreadRecord> findByIdAndThreadId(Long id, Long threadId);
    boolean existsByThreadIdAndSourceRecordId(Long threadId, Long sourceRecordId);
    boolean existsByThreadIdAndSourceRecordIdAndIdNot(Long threadId, Long sourceRecordId, Long id);
    long countByThreadId(Long threadId);
    long countByMethodId(Long methodId);
    @Query("select record.thread.id, count(record) from ExplorationThreadRecord record where record.thread.id in :threadIds group by record.thread.id")
    List<Object[]> countByThreadIds(@Param("threadIds") List<Long> threadIds);
    @Query(value = """
            select ranked.thread_id, ranked.record_id, ranked.method_content, ranked.discovery,
                   ranked.occurred_at, ranked.created_at
            from (
                select thread_record.thread_id, thread_record.id as record_id, thread_method.content as method_content,
                       thread_record.discovery, thread_record.occurred_at, thread_record.created_at,
                       row_number() over (partition by thread_record.thread_id
                           order by coalesce(thread_record.occurred_at, thread_record.created_at) desc, thread_record.id desc) as row_number
                from exploration_thread_records thread_record
                left join exploration_thread_methods thread_method on thread_method.id = thread_record.method_id
                where thread_record.thread_id in (:threadIds)
            ) ranked
            where ranked.row_number <= 3
            order by ranked.thread_id, ranked.row_number
            """, nativeQuery = true)
    List<Object[]> findRecentPreviewsByThreadIds(@Param("threadIds") List<Long> threadIds);
    @Modifying void deleteByMethodId(Long methodId);
    @Modifying @Query("update ExplorationThreadRecord r set r.method = null where r.method.id = :methodId")
    void detachMethod(Long methodId);
}
