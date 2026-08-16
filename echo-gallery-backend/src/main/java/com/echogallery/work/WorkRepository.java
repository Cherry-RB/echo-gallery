package com.echogallery.work;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkRepository extends JpaRepository<Work, Long> {
    @Query("""
            SELECT
                COUNT(w) AS totalWorks,
                COALESCE(SUM(CASE WHEN w.status IN :unfinishedStatuses THEN 1 ELSE 0 END), 0) AS unfinishedWorks
            FROM Work w
            WHERE w.user.id = :userId
            """)
    WorkStatsProjection findStats(
            @Param("userId") Long userId,
            @Param("unfinishedStatuses") List<WorkStatus> unfinishedStatuses);

    @Query("""
            SELECT new com.echogallery.work.WorkSummaryResponse(
                w.id,
                w.title,
                w.description,
                w.externalUrl,
                w.status,
                w.completedAt,
                w.updatedAt,
                SUM(CASE WHEN wc.status = :candidateStatus THEN 1 ELSE 0 END),
                SUM(CASE WHEN wc.status = :usedStatus THEN 1 ELSE 0 END)
            )
            FROM Work w
            LEFT JOIN WorkCard wc ON wc.work = w
            WHERE w.user.id = :userId
            GROUP BY w.id, w.title, w.description, w.externalUrl, w.status, w.completedAt, w.updatedAt
            ORDER BY w.updatedAt DESC
            """)
    List<WorkSummaryResponse> findSummariesByUserId(
            @Param("userId") Long userId,
            @Param("candidateStatus") WorkCardStatus candidateStatus,
            @Param("usedStatus") WorkCardStatus usedStatus);
}
