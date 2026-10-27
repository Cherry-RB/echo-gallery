package com.echogallery.experiment;
import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExplorationThreadMethodRepository extends JpaRepository<ExplorationThreadMethod, Long> {
    List<ExplorationThreadMethod> findByThreadIdOrderBySortOrderAscIdAsc(Long threadId);
    Optional<ExplorationThreadMethod> findByIdAndThreadId(Long id, Long threadId);
    Optional<ExplorationThreadMethod> findByIdAndExperimentId(Long id, Long experimentId);
    boolean existsByIdAndExperimentId(Long id, Long experimentId);
    Optional<ExplorationThreadMethod> findByThreadIdAndContentKey(Long threadId, String key);
    long countByThreadId(Long threadId);
    @Query("select coalesce(max(m.sortOrder), -1) + 1 from ExplorationThreadMethod m where m.thread.id = :id")
    int nextPosition(@Param("id") Long id);
    @Query("select m.thread.id, count(m) from ExplorationThreadMethod m where m.thread.id in :ids group by m.thread.id")
    List<Object[]> countByThreadIds(@Param("ids") List<Long> ids);
    @Query(value = "select distinct on (thread_id) thread_id, content from exploration_thread_methods where thread_id in (:ids) order by thread_id, sort_order, id", nativeQuery = true)
    List<Object[]> findFirstPreviewsByThreadIds(@Param("ids") List<Long> ids);
    interface Option {
        Long getId(); Long getThreadId(); String getThreadName(); String getContent(); long getVersion(); long getDiscoveryCount();
        boolean getHasHistory(); ZonedDateTime getLastOrganizedAt();
    }
    @Query("""
        select m.id as id, m.thread.id as threadId, m.thread.name as threadName, m.content as content,
               m.version as version, (select count(r) from ExperimentExplorationRecord r where r.method.id = m.id) as discoveryCount,
               case when m.historyText <> '' then true else false end as hasHistory, m.lastOrganizedAt as lastOrganizedAt
        from ExplorationThreadMethod m where m.experiment.id = :experimentId
          and (:threadId is null or m.thread.id = :threadId)
          and (:keyword = '' or lower(m.content) like :keyword escape '!')
        order by m.thread.sortOrder, m.thread.id, m.sortOrder, m.id
        """)
    Page<Option> options(@Param("experimentId") Long experimentId, @Param("threadId") Long threadId, @Param("keyword") String keyword, Pageable pageable);
    @Query("select m.id as id, m.thread.id as threadId, m.thread.name as threadName, m.content as content, m.version as version, (select count(r) from ExperimentExplorationRecord r where r.method.id = m.id) as discoveryCount, case when m.historyText <> '' then true else false end as hasHistory, m.lastOrganizedAt as lastOrganizedAt from ExplorationThreadMethod m where m.id = :id")
    Option option(@Param("id") Long id);
}
