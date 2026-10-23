package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ExplorationThreadRepository extends JpaRepository<ExplorationThread, Long> {
    @EntityGraph(attributePaths = "experiment")
    List<ExplorationThread> findByExperimentIdOrderBySortOrderAscIdAsc(Long experimentId);
    @EntityGraph(attributePaths = "experiment")
    Page<ExplorationThread> findByExperimentIdOrderBySortOrderAscIdAsc(Long experimentId, Pageable pageable);
    Optional<ExplorationThread> findByIdAndExperimentId(Long id, Long experimentId);
    long countByExperimentId(Long experimentId);
}
