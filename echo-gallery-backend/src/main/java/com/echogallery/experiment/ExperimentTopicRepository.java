package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentTopicRepository extends JpaRepository<ExperimentTopic, Long> {
    List<ExperimentTopic> findByExperimentIdOrderByPinnedSlotAscIdAsc(Long experimentId);
    Optional<ExperimentTopic> findByIdAndExperimentId(Long id, Long experimentId);
}
