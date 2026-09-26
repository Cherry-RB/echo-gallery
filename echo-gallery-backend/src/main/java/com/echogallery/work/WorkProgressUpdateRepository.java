package com.echogallery.work;

import java.util.List;
import java.util.Optional;
import java.time.ZonedDateTime;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkProgressUpdateRepository extends JpaRepository<WorkProgressUpdate, Long> {

    Slice<WorkProgressUpdate> findByWorkIdOrderByCreatedAtDescIdDesc(Long workId, Pageable pageable);

    Optional<WorkProgressUpdate> findByIdAndWorkId(Long id, Long workId);

    void deleteByWorkId(Long workId);

    @Query("""
            SELECT progressUpdate
            FROM WorkProgressUpdate progressUpdate
            JOIN FETCH progressUpdate.work work
            WHERE work.user.id = :userId
            ORDER BY progressUpdate.createdAt DESC, progressUpdate.id DESC
            """)
    List<WorkProgressUpdate> findRecentByUserId(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT progressUpdate
            FROM WorkProgressUpdate progressUpdate
            JOIN FETCH progressUpdate.work work
            WHERE work.user.id = :userId
              AND NOT EXISTS (
                  SELECT newerUpdate.id
                  FROM WorkProgressUpdate newerUpdate
                  WHERE newerUpdate.work.id = work.id
                    AND (
                        newerUpdate.createdAt > progressUpdate.createdAt
                        OR (
                            newerUpdate.createdAt = progressUpdate.createdAt
                            AND newerUpdate.id > progressUpdate.id
                        )
                    )
              )
            """)
    List<WorkProgressUpdate> findLatestByUserId(@Param("userId") Long userId);

    long countByWorkUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long userId,
            ZonedDateTime startAt,
            ZonedDateTime endAt);

    @Query("""
            SELECT COUNT(DISTINCT laterUpdate.work.id)
            FROM WorkProgressUpdate laterUpdate
            WHERE laterUpdate.work.user.id = :userId
              AND laterUpdate.createdAt >= :periodStartAt
              AND laterUpdate.createdAt < :periodEndAt
              AND EXISTS (
                  SELECT earlierUpdate.id
                  FROM WorkProgressUpdate earlierUpdate
                  WHERE earlierUpdate.work.id = laterUpdate.work.id
                    AND earlierUpdate.nextStep IS NOT NULL
                    AND TRIM(earlierUpdate.nextStep) <> ''
                    AND (
                        earlierUpdate.createdAt < laterUpdate.createdAt
                        OR (earlierUpdate.createdAt = laterUpdate.createdAt AND earlierUpdate.id < laterUpdate.id)
                    )
              )
            """)
    long countWorksWithFollowUpAfterNextStep(
            @Param("userId") Long userId,
            @Param("periodStartAt") ZonedDateTime periodStartAt,
            @Param("periodEndAt") ZonedDateTime periodEndAt);

}
