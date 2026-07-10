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

    // 看板分頁 - ALL 未封存卡片
    Page<Card> findByUserIdAndIsArchivedFalse(Long userId, Pageable pageable);

    // 看板分頁 - TODAY 今日看板
    // 對應先前建立的記憶回流複合索引 (idx_cards_user_review_flow)
    // @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.nextShowAt <= :now AND c.isArchived = false")
    // List<Card> findActiveReviewCards(@Param("userId") Long userId, @Param("now") ZonedDateTime now);
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.nextShowAt < :startOfTomorrow AND c.isArchived = false")
    Page<Card> findTodayCards(@Param("userId") Long userId, @Param("startOfTomorrow") ZonedDateTime startOfTomorrow, Pageable pageable);

    // 看板分頁 - 熱度排行
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.isArchived = false ORDER BY c.likeCount DESC")
    Page<Card> findHotCards(@Param("userId") Long userId, Pageable pageable);

    // 看板分頁 - 隨機看板
    @Query(value = "SELECT * FROM cards c WHERE c.user_id = :userId AND c.is_archived = false ORDER BY RANDOM()",
       countQuery = "SELECT count(*) FROM cards c WHERE c.user_id = :userId AND c.is_archived = false",
       nativeQuery = true)
    Page<Card> findRandomCards(@Param("userId") Long userId, Pageable pageable);

    // 看板分頁 - 已封存看板
    Page<Card> findByUserIdAndIsArchivedTrue(Long userId, Pageable pageable);

    // 看板分頁 - 稍後再看看版
    @Query("SELECT c FROM Card c WHERE c.user.id = :userId AND c.isArchived = false AND c.snoozeCount > :threshold")
    Page<Card> findSnoozedCards(@Param("userId") Long userId, @Param("threshold") int threshold, Pageable pageable);

    // 透過 Spring Data JPA 命名規範，直接建立限定用戶且支援分頁的查詢
    Page<Card> findByUserId(Long userId, Pageable pageable);

    // 統計資訊 1: 今日回流卡片數
    long countByUserIdAndIsArchivedFalseAndNextShowAtLessThan(Long userId, ZonedDateTime startOfTomorrow);

    // 統計資訊 2: 未封存總卡片數
    long countByUserIdAndIsArchivedFalse(Long userId);

    // 統計資訊 3: 稍後再看次數累積超過 10 次
    long countByUserIdAndIsArchivedFalseAndSnoozeCountGreaterThan(Long userId, int snoozeThreshold);
}
