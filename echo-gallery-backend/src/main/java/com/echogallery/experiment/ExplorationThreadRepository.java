package com.echogallery.experiment;
import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExplorationThreadRepository extends JpaRepository<ExplorationThread, Long> {
    @EntityGraph(attributePaths = "experiment")
    List<ExplorationThread> findByExperimentIdOrderBySortOrderAscIdAsc(Long experimentId);
    Optional<ExplorationThread> findByIdAndExperimentId(Long id, Long experimentId);
    Optional<ExplorationThread> findByExperimentIdAndSystemKey(Long experimentId, String systemKey);
    long countByExperimentId(Long experimentId);
    boolean existsByIdAndExperimentId(Long id, Long experimentId);
    @Query("select coalesce(max(t.sortOrder), -1) + 1 from ExplorationThread t where t.experiment.id = :id")
    int nextPosition(@Param("id") Long id);
    @Query("select t.id from ExplorationThread t where t.experiment.id = :id order by t.sortOrder, t.id")
    List<Long> orderedIds(@Param("id") Long id);
    @Modifying(flushAutomatically = true)
    @Query(value = "update exploration_threads t set sort_order = p.ordinality - 1 from unnest(cast(string_to_array(:ids, ',') as bigint[])) with ordinality p(id, ordinality) where t.id = p.id and t.experiment_id = :experimentId", nativeQuery = true)
    int saveOrder(@Param("experimentId") Long experimentId, @Param("ids") String ids);
    interface ListRow {
        Long getId(); Long getExperimentId(); String getName(); ExplorationThreadStatus getStatus(); String getConclusion();
        int getSortOrder(); ZonedDateTime getLastActivityAt(); String getSystemKey(); long getVersion(); long getUnassignedVersion(); boolean getHasUnassignedHistory();
    }
    @Query("select t.id as id, t.experiment.id as experimentId, t.name as name, t.status as status, t.conclusion as conclusion, t.sortOrder as sortOrder, t.lastActivityAt as lastActivityAt, t.systemKey as systemKey, t.version as version, t.unassignedVersion as unassignedVersion, case when t.unassignedHistoryText <> '' then true else false end as hasUnassignedHistory from ExplorationThread t where t.experiment.id = :id order by t.sortOrder, t.id")
    Page<ListRow> listRows(@Param("id") Long id, Pageable pageable);
    @Query("select t.id as id, t.experiment.id as experimentId, t.name as name, t.status as status, t.conclusion as conclusion, t.sortOrder as sortOrder, t.lastActivityAt as lastActivityAt, t.systemKey as systemKey, t.version as version, t.unassignedVersion as unassignedVersion, case when t.unassignedHistoryText <> '' then true else false end as hasUnassignedHistory from ExplorationThread t where t.experiment.id = :experimentId and t.id = :threadId")
    Optional<ListRow> detailRow(@Param("experimentId") Long experimentId, @Param("threadId") Long threadId);
    interface ThreadOption { Long getId(); String getName(); String getSystemKey(); boolean getAvailable(); }
    @Query("select t.id as id, t.name as name, t.systemKey as systemKey, case when :cardId is null or not exists (select l.id from ExplorationThreadCard l where l.thread.id = t.id and l.card.id = :cardId and l.role = com.echogallery.experiment.ExplorationThreadCardRole.MATERIAL) then true else false end as available from ExplorationThread t where t.experiment.id = :experimentId and (:keyword = '' or lower(t.name) like :keyword escape '!') order by t.sortOrder, t.id")
    Page<ThreadOption> options(@Param("experimentId") Long experimentId, @Param("keyword") String keyword, @Param("cardId") Long cardId, Pageable pageable);
}
