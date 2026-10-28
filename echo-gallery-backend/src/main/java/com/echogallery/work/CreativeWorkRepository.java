package com.echogallery.work;

import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CreativeWorkRepository extends JpaRepository<CreativeWork, Long>, JpaSpecificationExecutor<CreativeWork> {
    @Override @EntityGraph(attributePaths = {"experiment", "treasure"})
    org.springframework.data.domain.Page<CreativeWork> findAll(org.springframework.data.jpa.domain.Specification<CreativeWork> spec, org.springframework.data.domain.Pageable pageable);
    Optional<CreativeWork> findByIdAndUserId(Long id, Long userId);
    Optional<CreativeWork> findByTreasureIdAndUserId(Long treasureId, Long userId);
    boolean existsByTreasureId(Long treasureId);
    long countByUserId(Long userId);
    @Modifying @Query("delete from CreativeWork w where w.user.id = :owner")
    void deleteOwner(@Param("owner") Long owner);
}
