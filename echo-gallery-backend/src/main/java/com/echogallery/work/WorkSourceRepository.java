package com.echogallery.work;

import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface WorkSourceRepository extends JpaRepository<WorkSource, Long> {
    @EntityGraph(attributePaths = {"card"})
    List<WorkSource> findByWorkIdOrderByIdDesc(Long workId);
    boolean existsByWorkIdAndCardId(Long workId, Long cardId);
    @EntityGraph(attributePaths = {"work"})
    @Query("select s from WorkSource s where s.work.user.id = :owner and s.card.id in :cards")
    List<WorkSource> usages(@Param("owner") Long owner, @Param("cards") List<Long> cards);
    @Modifying @Query("delete from WorkSource s where s.work.user.id = :owner")
    void deleteOwner(@Param("owner") Long owner);
}
