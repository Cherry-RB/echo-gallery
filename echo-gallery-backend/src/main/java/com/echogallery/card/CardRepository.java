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

    @Query("""
    SELECT c
    FROM Card c
    WHERE c.user.id = :userId
    AND c.isArchived = false
    AND (
        (:cardId IS NOT NULL AND c.id = :cardId)
        OR LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
    )
    """)
    Page<Card> searchActiveCards(
            @Param("userId") Long userId,
            @Param("cardId") Long cardId,
            @Param("keyword") String keyword,
            Pageable pageable);

    @Query("""
            SELECT
                COUNT(c) AS totalCards,
                COALESCE(SUM(CASE WHEN c.nextShowAt < :startOfTomorrow THEN 1 ELSE 0 END), 0) AS todayEchoCards,
                COALESCE(SUM(CASE WHEN c.snoozeCount > :snoozeThreshold THEN 1 ELSE 0 END), 0) AS highSnoozeCards,
                COALESCE(SUM(CASE WHEN c.growthStatus = :seedStatus THEN 1 ELSE 0 END), 0) AS seedCards,
                COALESCE(SUM(CASE WHEN c.growthStatus = :growingStatus THEN 1 ELSE 0 END), 0) AS growingCards,
                COALESCE(SUM(CASE WHEN c.growthStatus = :matureStatus THEN 1 ELSE 0 END), 0) AS matureCards
            FROM Card c
            WHERE c.user.id = :userId
            AND c.isArchived = false
            """)
    CardStatsProjection findActiveStats(
            @Param("userId") Long userId,
            @Param("startOfTomorrow") ZonedDateTime startOfTomorrow,
            @Param("snoozeThreshold") int snoozeThreshold,
            @Param("seedStatus") CardGrowthStatus seedStatus,
            @Param("growingStatus") CardGrowthStatus growingStatus,
            @Param("matureStatus") CardGrowthStatus matureStatus);

    // 以標籤作為篩選條件 查詢卡片
        // 只有當命中的標籤數量恰好等於傳入的標籤總數，才保留該張卡片。這確保了卡片同時擁有了所有要求的標籤（AND 邏輯）
    @Query("""
    SELECT c
    FROM Card c
    JOIN c.tags t
    WHERE c.user.id = :userId
    AND c.isArchived = false
    AND t.id IN :tagIds
    GROUP BY c
    HAVING COUNT(DISTINCT t.id) = :tagCount
    ORDER BY c.updatedAt DESC
    """)
    List<Card> findCardsByAllTags(
            @Param("userId") Long userId,
            @Param("tagIds") List<Long> tagIds,
            @Param("tagCount") Long tagCount);

    @Query("""
    SELECT DISTINCT c
    FROM Card c
    JOIN c.tags t
    WHERE c.user.id = :userId
    AND c.isArchived = false
    AND t.id IN :tagIds
    ORDER BY c.updatedAt DESC
    """)
    List<Card> findCardsByAnyTags(
        @Param("userId") Long userId,
        @Param("tagIds") List<Long> tagIds);
}
