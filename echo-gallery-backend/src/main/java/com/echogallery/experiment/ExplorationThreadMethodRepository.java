package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExplorationThreadMethodRepository extends JpaRepository<ExplorationThreadMethod, Long> {
    List<ExplorationThreadMethod> findByThreadIdOrderBySortOrderAscIdAsc(Long threadId);
    Optional<ExplorationThreadMethod> findByIdAndThreadId(Long id, Long threadId);
    boolean existsByThreadIdAndSourceRecordId(Long threadId, Long sourceRecordId);
    boolean existsByThreadIdAndSourceRecordIdAndIdNot(Long threadId, Long sourceRecordId, Long id);
    long countByThreadId(Long threadId);
    @Query("select method.thread.id, count(method) from ExplorationThreadMethod method where method.thread.id in :threadIds group by method.thread.id")
    List<Object[]> countByThreadIds(@Param("threadIds") List<Long> threadIds);
    @Query(value = """
            select ranked.thread_id, ranked.content
            from (
                select thread_method.thread_id, thread_method.content,
                       row_number() over (partition by thread_method.thread_id order by thread_method.sort_order asc, thread_method.id asc) as row_number
                from exploration_thread_methods thread_method
                where thread_method.thread_id in (:threadIds)
            ) ranked
            where ranked.row_number = 1
            """, nativeQuery = true)
    List<Object[]> findFirstPreviewsByThreadIds(@Param("threadIds") List<Long> threadIds);
}
