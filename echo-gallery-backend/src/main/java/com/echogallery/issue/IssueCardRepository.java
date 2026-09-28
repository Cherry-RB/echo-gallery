package com.echogallery.issue;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueCardRepository extends JpaRepository<IssueCard, Long> {
    boolean existsByIssueIdAndCardId(Long issueId, Long cardId);

    Optional<IssueCard> findByIssueIdAndCardId(Long issueId, Long cardId);

    void deleteByIssueId(Long issueId);

    List<IssueCard> findAllByIssueId(Long issueId);

    @EntityGraph(attributePaths = { "card", "card.tags" })
    Page<IssueCard> findByIssueIdAndStatusOrderByLinkedAtDescIdDesc(
            Long issueId,
            IssueCardStatus status,
            Pageable pageable);

    @EntityGraph(attributePaths = "issue")
    List<IssueCard> findByCardIdAndIssueUserIdOrderByLinkedAtDesc(Long cardId, Long userId);

    long countByIssueUserIdAndLinkedAtGreaterThanEqualAndLinkedAtLessThan(
            Long userId,
            ZonedDateTime startAt,
            ZonedDateTime endAt);
}
