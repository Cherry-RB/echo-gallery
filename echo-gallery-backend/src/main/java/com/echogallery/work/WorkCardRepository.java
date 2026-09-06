package com.echogallery.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkCardRepository extends JpaRepository<WorkCard, Long> {
    boolean existsByWorkIdAndCardId(Long workId, Long cardId);

    Optional<WorkCard> findByWorkIdAndCardId(Long workId, Long cardId);

    void deleteByWorkId(Long workId);

    @EntityGraph(attributePaths = { "card", "card.tags" })
    Page<WorkCard> findByWorkIdAndStatusOrderByLinkedAtDescIdDesc(
            Long workId,
            WorkCardStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = "work")
    List<WorkCard> findByCardIdAndWorkUserIdOrderByLinkedAtDesc(Long cardId, Long userId);
}
