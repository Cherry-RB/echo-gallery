package com.echogallery.experiment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRecordTopicRepository extends JpaRepository<ExperimentRecordTopic, Long> {
    List<ExperimentRecordTopic> findByRecordId(Long recordId);
    void deleteByRecordId(Long recordId);
    void deleteByTopicId(Long topicId);
}
