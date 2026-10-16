package com.echogallery.treasure;

import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TreasureRelationRepository extends JpaRepository<TreasureRelation, Long> {
    @EntityGraph(attributePaths = "source")
    List<TreasureRelation> findByTargetIdOrderById(Long id);
    @EntityGraph(attributePaths = {"source", "target"})
    Page<TreasureRelation> findByTargetIdOrderByIdDesc(Long id, Pageable pageable);
    @EntityGraph(attributePaths = {"source", "target"})
    Page<TreasureRelation> findBySourceIdOrderByIdDesc(Long id, Pageable pageable);
    @Query(value = """
            with recursive descendants(id) as (
                select target_treasure_id from treasure_relations where source_treasure_id = :targetId
                union
                select r.target_treasure_id from treasure_relations r join descendants d on r.source_treasure_id = d.id
            ) select exists(select 1 from descendants where id = :sourceId)
            """, nativeQuery = true)
    boolean wouldCreateCycle(@Param("targetId") Long targetId, @Param("sourceId") Long sourceId);
    @Modifying @Query("delete from TreasureRelation r where r.target.id = :id")
    void deleteSources(@Param("id") Long id);
    @Modifying @Query("delete from TreasureRelation r where r.source.id = :id or r.target.id = :id")
    void deleteTreasure(@Param("id") Long id);
    @Modifying @Query("delete from TreasureRelation r where r.source.user.id = :userId or r.target.user.id = :userId")
    void deleteOwner(@Param("userId") Long userId);
}
