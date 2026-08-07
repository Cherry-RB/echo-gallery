package com.echogallery.tag;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    // 關鍵：只找該使用者底下的特定名稱標籤
    Optional<Tag> findByUserIdAndName(Long userId, String name);

    @Query("""
        SELECT new com.echogallery.tag.TagDto(t.id, t.name, COUNT(c.id))
        FROM Tag t
        JOIN t.cards c
        WHERE t.user.id = :userId AND c.isArchived = false
        GROUP BY t.id, t.name
        ORDER BY COUNT(c.id) DESC
    """)
    List<TagDto> findTopTagsWithCardCount(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT new com.echogallery.tag.TagDto(t.id, t.name, COUNT(c.id))
        FROM Tag t
        JOIN t.cards c
        WHERE t.user.id = :userId AND c.isArchived = false
        GROUP BY t.id, t.name
        ORDER BY COUNT(c.id) DESC
    """)
    List<TagDto> findTagsWithCardCount(@Param("userId") Long userId);
}
