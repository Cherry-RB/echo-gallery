package com.echogallery.experiment;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface ExperimentRepository extends JpaRepository<Experiment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Experiment e where e.id = :id and e.user.id = :owner")
    Optional<Experiment> lockOwned(@Param("id") Long id, @Param("owner") Long owner);
    Page<Experiment> findByUserIdAndIsArchivedOrderByUpdatedAtDescIdDesc(Long userId, boolean isArchived, Pageable pageable);

    Page<Experiment> findByUserIdOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);

    List<Experiment> findByUserIdAndIsArchivedFalseOrderByUpdatedAtDescIdDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsArchivedFalse(Long userId);

    List<Experiment> findByIdIn(List<Long> experimentIds);
    List<Experiment> findAllByUserId(Long userId);
}
