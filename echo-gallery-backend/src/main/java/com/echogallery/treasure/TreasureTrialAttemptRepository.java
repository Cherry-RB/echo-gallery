package com.echogallery.treasure;

import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TreasureTrialAttemptRepository extends JpaRepository<TreasureTrialAttempt, Long> {
    Page<TreasureTrialAttempt> findByTrialIdOrderByAttemptedAtDescIdDesc(Long id, Pageable pageable);
    @EntityGraph(attributePaths = {"trial", "trial.treasure"})
    List<TreasureTrialAttempt> findByTrialTreasureUserIdAndAttemptedAtGreaterThanEqualAndAttemptedAtLessThanOrderByAttemptedAtDescIdDesc(
            Long owner, ZonedDateTime start, ZonedDateTime end, Pageable pageable);
    @Modifying @Query("delete from TreasureTrialAttempt a where a.trial.treasure.id = :id")
    void deleteTreasure(@Param("id") Long id);
    @Modifying @Query("delete from TreasureTrialAttempt a where a.trial.treasure.user.id = :owner")
    void deleteOwner(@Param("owner") Long owner);
}
