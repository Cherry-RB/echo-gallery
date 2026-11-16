package com.echogallery.treasure;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreasureCardSourceRepository extends JpaRepository<TreasureCardSource, Long> {
    @EntityGraph(attributePaths = {"card"})
    List<TreasureCardSource> findByTreasureIdOrderById(Long treasureId);

    @Modifying
    @Query("delete from TreasureCardSource s where s.treasure.id = :treasureId")
    void deleteByTreasureId(@Param("treasureId") Long treasureId);

    @Modifying
    @Query("delete from TreasureCardSource s where s.treasure.user.id = :userId")
    void deleteOwner(@Param("userId") Long userId);
}
