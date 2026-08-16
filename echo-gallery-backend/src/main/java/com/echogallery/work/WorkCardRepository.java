package com.echogallery.work;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkCardRepository extends JpaRepository<WorkCard, Long> {
}
