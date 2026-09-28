package com.echogallery.issue;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IssueUpdateRepository extends JpaRepository<IssueUpdate, Long> {

    Slice<IssueUpdate> findByIssueIdOrderByCreatedAtDescIdDesc(Long issueId, Pageable pageable);

    Optional<IssueUpdate> findByIdAndIssueId(Long id, Long issueId);

    void deleteByIssueId(Long issueId);

    @Query("""
            SELECT progressUpdate
            FROM IssueUpdate progressUpdate
            JOIN FETCH progressUpdate.issue issue
            WHERE issue.user.id = :userId
            ORDER BY progressUpdate.createdAt DESC, progressUpdate.id DESC
            """)
    List<IssueUpdate> findRecentByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT progressUpdate
            FROM IssueUpdate progressUpdate
            JOIN FETCH progressUpdate.issue issue
            WHERE issue.user.id = :userId
              AND NOT EXISTS (
                  SELECT newerUpdate.id
                  FROM IssueUpdate newerUpdate
                  WHERE newerUpdate.issue.id = issue.id
                    AND (
                        newerUpdate.createdAt > progressUpdate.createdAt
                        OR (
                            newerUpdate.createdAt = progressUpdate.createdAt
                            AND newerUpdate.id > progressUpdate.id
                        )
                    )
              )
            """)
    List<IssueUpdate> findLatestByUserId(@Param("userId") Long userId);

    long countByIssueUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            ZonedDateTime startAt,
            ZonedDateTime endAt);

    @Query("""
            SELECT COUNT(DISTINCT laterUpdate.issue.id)
            FROM IssueUpdate laterUpdate
            WHERE laterUpdate.issue.user.id = :userId
              AND laterUpdate.createdAt >= :periodStartAt
              AND laterUpdate.createdAt < :periodEndAt
              AND EXISTS (
                  SELECT earlierUpdate.id
                  FROM IssueUpdate earlierUpdate
                  WHERE earlierUpdate.issue.id = laterUpdate.issue.id
                    AND earlierUpdate.nextStep IS NOT NULL
                    AND TRIM(earlierUpdate.nextStep) <> ''
                    AND (
                        earlierUpdate.createdAt < laterUpdate.createdAt
                        OR (earlierUpdate.createdAt = laterUpdate.createdAt AND earlierUpdate.id < laterUpdate.id)
                    )
              )
            """)
    long countIssuesWithFollowUpAfterNextStep(
            @Param("userId") Long userId,
            @Param("periodStartAt") ZonedDateTime periodStartAt,
            @Param("periodEndAt") ZonedDateTime periodEndAt);

}
