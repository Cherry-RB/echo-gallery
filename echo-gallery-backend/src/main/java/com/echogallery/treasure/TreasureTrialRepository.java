package com.echogallery.treasure;

import java.time.ZonedDateTime;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TreasureTrialRepository extends JpaRepository<TreasureTrial, Long> {
    Page<TreasureTrial> findByTreasureIdOrderByCreatedAtDescIdDesc(Long id, Pageable pageable);
    Optional<TreasureTrial> findByIdAndTreasureId(Long id, Long treasureId);
    List<TreasureTrial> findByTreasureIdOrderByTargetRank(Long id);
    boolean existsByTreasureIdAndTargetRank(Long id, TreasureRank rank);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureTrial> findByTreasureIdInOrderByTargetRank(Collection<Long> ids);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureTrial> findByTreasureIdInAndStatusInOrderByUpdatedAtDescIdDesc(Collection<Long> ids, Collection<TrialStatus> statuses);
    List<TreasureTrial> findByTreasureIdAndStatusInOrderByCreatedAtDescIdDesc(Long id, Collection<TrialStatus> statuses);
    long countByTreasureId(Long id);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureTrial> findByTreasureUserIdAndTreasureArchivedFalseAndStatusInOrderByCreatedAtDescIdDesc(
            Long userId, Collection<TrialStatus> statuses, Pageable pageable);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureTrial> findByTreasureUserIdAndPassedAtGreaterThanEqualAndPassedAtLessThanOrderByPassedAtDescIdDesc(
            Long userId, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
    @EntityGraph(attributePaths = "treasure")
    List<TreasureTrial> findByTreasureUserIdAndAttemptedAtGreaterThanEqualAndAttemptedAtLessThanOrderByAttemptedAtDescIdDesc(
            Long userId, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
    @Query("select count(distinct t.treasure.id) from TreasureTrial t where t.treasure.user.id = :userId and t.targetRank is not null and t.passedAt >= :start and t.passedAt < :end")
    long countPromoted(@Param("userId") Long userId, @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end);
    @EntityGraph(attributePaths = "treasure")
    @Query("""
            select t from TreasureTrial t where t.treasure.user.id = :userId and t.treasure.archived = false
            and t.targetRank is not null and t.passedAt >= :start and t.passedAt < :end
            and not exists (select other.id from TreasureTrial other where other.treasure = t.treasure
                and other.targetRank is not null
                and (other.passedAt > t.passedAt or (other.passedAt = t.passedAt and other.id > t.id)))
            order by t.passedAt desc, t.id desc
            """)
    List<TreasureTrial> findPromotionHighlights(@Param("userId") Long userId,
            @Param("start") ZonedDateTime start, @Param("end") ZonedDateTime end, Pageable pageable);
    @Modifying @Query("delete from TreasureTrial t where t.treasure.id = :id")
    void deleteTreasure(@Param("id") Long id);
    @Modifying @Query("delete from TreasureTrial t where t.treasure.user.id = :userId")
    void deleteOwner(@Param("userId") Long userId);
}
