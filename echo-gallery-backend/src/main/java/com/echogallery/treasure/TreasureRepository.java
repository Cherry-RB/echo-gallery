package com.echogallery.treasure;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TreasureRepository extends JpaRepository<Treasure, Long>, JpaSpecificationExecutor<Treasure> {
    @EntityGraph(attributePaths = {"sourceCard", "sourceExperiment"})
    Optional<Treasure> findByIdAndUserId(Long id, Long userId);
    long countByUserIdAndArchivedFalse(Long userId);
    long countByUserIdAndArchivedFalseAndRank(Long userId, TreasureRank rank);
    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, ZonedDateTime start, ZonedDateTime end);
    List<Treasure> findByUserIdAndArchivedFalseOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);
    List<Treasure> findByUserIdAndArchivedFalseOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);
    List<Treasure> findByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
            Long userId, ZonedDateTime start, ZonedDateTime end, Pageable pageable);

    @Modifying @Query("update Treasure t set t.sourceCard = null where t.sourceCard.id = :id")
    void detachCard(@Param("id") Long id);
    @Modifying @Query("update Treasure t set t.sourceExperiment = null where t.sourceExperiment.id = :id")
    void detachExperiment(@Param("id") Long id);
    @Modifying @Query("delete from Treasure t where t.user.id = :userId")
    void deleteOwner(@Param("userId") Long userId);
}
