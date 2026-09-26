package com.echogallery.experiment;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    Page<Experiment> findByUserIdAndIsArchivedOrderByUpdatedAtDescIdDesc(Long userId, boolean isArchived, Pageable pageable);

    long countByUserIdAndIsArchivedFalse(Long userId);

    List<Experiment> findByIdIn(List<Long> experimentIds);
}
