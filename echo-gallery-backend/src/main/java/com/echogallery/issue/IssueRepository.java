package com.echogallery.issue;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {
    List<Issue> findAllByUserId(Long userId);
    @Query("""
            SELECT
                COUNT(w) AS totalIssues,
                COALESCE(SUM(CASE WHEN w.status IN :unfinishedStatuses THEN 1 ELSE 0 END), 0) AS unfinishedIssues
            FROM Issue w
            WHERE w.user.id = :userId
            """)
    IssueStatsProjection findStats(
            @Param("userId") Long userId,
            @Param("unfinishedStatuses") List<IssueStatus> unfinishedStatuses);

    @Query("""
            SELECT new com.echogallery.issue.IssueSummaryResponse(
                w.id,
                w.title,
                w.objective,
                w.description,
                w.currentAssessment,
                w.outcomeCriteria,
                w.externalUrl,
                w.status,
                w.completedAt,
                w.updatedAt,
                (SELECT COUNT(progressUpdate.id) FROM IssueUpdate progressUpdate WHERE progressUpdate.issue = w),
                SUM(CASE WHEN wc.status = :candidateStatus THEN 1 ELSE 0 END),
                SUM(CASE WHEN wc.status = :usedStatus THEN 1 ELSE 0 END)
            )
            FROM Issue w
            LEFT JOIN IssueCard wc ON wc.issue = w
            WHERE w.user.id = :userId
            GROUP BY w.id, w.title, w.objective, w.description, w.currentAssessment,
                w.outcomeCriteria, w.externalUrl, w.status, w.completedAt, w.updatedAt
            ORDER BY w.updatedAt DESC
            """)
    List<IssueSummaryResponse> findSummariesByUserId(
            @Param("userId") Long userId,
            @Param("candidateStatus") IssueCardStatus candidateStatus,
            @Param("usedStatus") IssueCardStatus usedStatus);
}
