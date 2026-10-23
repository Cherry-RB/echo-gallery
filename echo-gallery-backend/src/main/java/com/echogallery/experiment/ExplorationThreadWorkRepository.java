package com.echogallery.experiment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ExplorationThreadWorkRepository extends JpaRepository<ExplorationThreadWork, Long> {
    List<ExplorationThreadWork> findByThreadIdOrderByCreatedAtAscIdAsc(Long threadId);
    Optional<ExplorationThreadWork> findByIdAndThreadId(Long id, Long threadId);
    boolean existsByThreadIdAndWorkId(Long threadId, Long workId);
    List<ExplorationThreadWork> findByWorkIdAndThreadExperimentUserIdOrderByCreatedAtDesc(Long workId, Long userId);
    @Query("select link.thread.id, count(link) from ExplorationThreadWork link where link.thread.id in :threadIds group by link.thread.id")
    List<Object[]> countByThreadIds(@Param("threadIds") List<Long> threadIds);
}
