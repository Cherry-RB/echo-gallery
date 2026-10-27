package com.echogallery.experiment;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ExperimentMethodPreferenceRepository extends JpaRepository<ExperimentMethodPreference, Long> {
    List<ExperimentMethodPreference> findTop3ByExperimentIdAndLastUsedAtIsNotNullOrderByLastUsedAtDescIdDesc(Long experimentId);
    List<ExperimentMethodPreference> findByExperimentIdAndFavoriteSlotIsNotNullOrderByFavoriteSlotAsc(Long experimentId);
    Optional<ExperimentMethodPreference> findByExperimentIdAndMethodId(Long experimentId, Long methodId);
    @Modifying @Query("update ExperimentMethodPreference p set p.favoriteSlot = null where p.experiment.id = :id")
    void clearFavorites(@Param("id") Long id);
    @Modifying void deleteByMethodId(Long methodId);
}
