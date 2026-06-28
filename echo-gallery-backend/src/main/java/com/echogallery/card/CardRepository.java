package com.echogallery.card;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CardRepository extends JpaRepository<Card, Long> {

    // 嚴格隔離：只查詢屬於特定使用者的未歸檔卡片
    List<Card> findByUserIdAndIsArchivedFalse(Long userId);

    // 對應先前建立的記憶回流複合索引 (idx_cards_user_review_flow)
    // @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.nextShowAt <= :now AND c.isArchived = false")
    // List<Card> findActiveReviewCards(@Param("userId") Long userId, @Param("now") ZonedDateTime now);
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.nextShowAt <= :now AND c.isArchived = false")
    Page<Card> findActiveReviewCards(@Param("userId") Long userId, @Param("now") ZonedDateTime now, Pageable pageable);

    // 透過 Spring Data JPA 命名規範，直接建立限定用戶且支援分頁的查詢
    Page<Card> findByUserId(Long userId, Pageable pageable);

    // 統計資訊 1: 今日回流卡片數
    long countByUserIdAndIsArchivedFalseAndNextShowAtLessThanEqual(Long userId, ZonedDateTime now);

    // 統計資訊 2: 未封存總卡片數
    long countByUserIdAndIsArchivedFalse(Long userId);

    // 統計資訊 3: 稍後再看次數累積超過 10 次
    long countByUserIdAndIsArchivedFalseAndSnoozeCountGreaterThan(Long userId, int snoozeThreshold);
}
