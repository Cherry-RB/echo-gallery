package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExperimentTopicRepository extends JpaRepository<ExperimentTopic, Long> {
    @Query("select topic from ExperimentTopic topic where topic.experiment.id = :experimentId order by topic.displayOrder asc, topic.id asc")
    List<ExperimentTopic> findByExperimentIdOrdered(@Param("experimentId") Long experimentId);
    @Query("select coalesce(max(topic.displayOrder), 0) from ExperimentTopic topic where topic.experiment.id = :experimentId")
    int maxDisplayOrderByExperimentId(@Param("experimentId") Long experimentId);
    Optional<ExperimentTopic> findByIdAndExperimentId(Long id, Long experimentId);
}
