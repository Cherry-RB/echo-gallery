package com.echogallery.tag;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {

    // 關鍵：只找該使用者底下的特定名稱標籤
    Optional<Tag> findByUserIdAndName(Long userId, String name);
    
}
