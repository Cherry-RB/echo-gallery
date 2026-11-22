package com.echogallery.work;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkSourceRepository extends JpaRepository<WorkSource, Long> {
    @EntityGraph(attributePaths = {"card", "record", "record.experiment"})
    List<WorkSource> findByWorkIdOrderByIdDesc(Long workId);
    boolean existsByWorkIdAndCardId(Long workId, Long cardId);
    boolean existsByWorkIdAndRecordId(Long workId, Long recordId);
    boolean existsByRecordId(Long recordId);
    @EntityGraph(attributePaths = {"work"})
    @Query("select s from WorkSource s where s.work.user.id = :owner and s.card.id in :cards")
    List<WorkSource> usages(@Param("owner") Long owner, @Param("cards") List<Long> cards);
    @EntityGraph(attributePaths = {"work"})
    @Query("select s from WorkSource s where s.work.user.id = :owner and s.record.id in :records")
    List<WorkSource> recordUsages(@Param("owner") Long owner, @Param("records") List<Long> records);
    @Modifying @Query("delete from WorkSource s where s.work.user.id = :owner")
    void deleteOwner(@Param("owner") Long owner);
}
