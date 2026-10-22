package com.echogallery.work;

import java.util.List;
import org.springframework.data.jpa.repository.*;

public interface WorkSourceRepository extends JpaRepository<WorkSource, Long> {
    @EntityGraph(attributePaths = {"card", "record", "record.experiment"})
    List<WorkSource> findByWorkIdOrderByIdDesc(Long workId);
    boolean existsByWorkIdAndCardId(Long workId, Long cardId);
    boolean existsByWorkIdAndRecordId(Long workId, Long recordId);
    @EntityGraph(attributePaths = {"work"})
    @Query("select s from WorkSource s where s.work.user.id = :owner and (s.card.id in :cards or s.record.id in :records)")
    List<WorkSource> usages(Long owner, List<Long> cards, List<Long> records);
    @Modifying @Query("delete from WorkSource s where s.work.user.id = :owner")
    void deleteOwner(Long owner);
}
