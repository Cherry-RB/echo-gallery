package com.echogallery.work;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkCardRepository extends JpaRepository<WorkCard, Long> {
    boolean existsByWorkIdAndCardId(Long workId, Long cardId);

    Optional<WorkCard> findByWorkIdAndCardId(Long workId, Long cardId);
}
