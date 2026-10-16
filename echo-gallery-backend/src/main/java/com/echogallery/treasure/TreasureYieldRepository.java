package com.echogallery.treasure;

import java.time.LocalDate;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TreasureYieldRepository extends JpaRepository<TreasureYield, Long> {
    @EntityGraph(attributePaths = "relatedCard")
    Page<TreasureYield> findByTreasureIdOrderByOccurredOnDescIdDesc(Long id, Pageable pageable);
    interface TreasureCount { Long getTreasureId(); long getTotal(); }
    @Query("select y.treasure.id as treasureId, count(y) as total from TreasureYield y where y.treasure.id in :ids group by y.treasure.id")
    List<TreasureCount> countForTreasures(@Param("ids") Collection<Long> ids);
    Optional<TreasureYield> findByIdAndTreasureId(Long id, Long treasureId);
    long countByTreasureId(Long id);
    long countByTreasureUserIdAndOccurredOnGreaterThanEqualAndOccurredOnLessThan(Long userId, LocalDate start, LocalDate end);
    @Query("select count(distinct y.treasure.id) from TreasureYield y where y.treasure.user.id = :userId and y.occurredOn >= :start and y.occurredOn < :end")
    long countYieldTreasures(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureYield> findByTreasureUserIdAndTreasureArchivedFalseOrderByOccurredOnDescIdDesc(Long userId, Pageable pageable);
    @EntityGraph(attributePaths = "treasure")
    @Query("""
            select y from TreasureYield y where y.treasure.user.id = :userId and y.treasure.archived = false
            and not exists (select other.id from TreasureYield other where other.treasure = y.treasure
                and (other.occurredOn > y.occurredOn or (other.occurredOn = y.occurredOn and other.id > y.id)))
            order by y.occurredOn desc, y.id desc
            """)
    List<TreasureYield> findHighlights(@Param("userId") Long userId, Pageable pageable);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureYield> findByTreasureUserIdAndOccurredOnGreaterThanEqualAndOccurredOnLessThanOrderByOccurredOnDescIdDesc(
            Long userId, LocalDate start, LocalDate end, Pageable pageable);
    @Modifying @Query("update TreasureYield y set y.relatedCard = null where y.relatedCard.id = :id")
    void detachCard(@Param("id") Long id);
    @Modifying @Query("delete from TreasureYield y where y.treasure.id = :id")
    void deleteTreasure(@Param("id") Long id);
    @Modifying @Query("delete from TreasureYield y where y.treasure.user.id = :userId")
    void deleteOwner(@Param("userId") Long userId);
}
