package com.echogallery.work;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkProgressUpdateRepository extends JpaRepository<WorkProgressUpdate, Long> {

    Slice<WorkProgressUpdate> findByWorkIdOrderByCreatedAtDescIdDesc(Long workId, Pageable pageable);

    Optional<WorkProgressUpdate> findByIdAndWorkId(Long id, Long workId);

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

}
