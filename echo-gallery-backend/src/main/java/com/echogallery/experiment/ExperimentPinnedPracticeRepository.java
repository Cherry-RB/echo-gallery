package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentPinnedPracticeRepository extends JpaRepository<ExperimentPinnedPractice, Long> {
    List<ExperimentPinnedPractice> findByExperimentIdOrderBySlotAsc(Long experimentId);
    Optional<ExperimentPinnedPractice> findByIdAndExperimentId(Long id, Long experimentId);
    long countByExperimentId(Long experimentId);
}
