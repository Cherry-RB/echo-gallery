package com.echogallery.work;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {
    List<Work> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
