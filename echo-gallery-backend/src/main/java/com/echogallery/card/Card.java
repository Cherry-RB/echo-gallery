package com.echogallery.card;

import java.util.HashSet;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.Set;

import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.echogallery.tag.Tag;
import com.echogallery.user.User;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 多對一：多張卡片屬於同一個使用者
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 10)
    private String type; // "note" 或 "link"

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;

    @Column(name = "url", length = 2048)
    private String url;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "summary", length = 600)
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "interval_days")
    private Integer intervalDays;

    @Column(name = "next_show_at")
    private ZonedDateTime nextShowAt;
    
    @Column(name = "like_available_at")
    private ZonedDateTime likeAvailableAt;

    @Column(name = "last_liked_at")
    private ZonedDateTime lastLikedAt;
    
    @Builder.Default
    @Column(name = "like_count")
    private int likeCount = 0;
    
    @Column(name = "is_archived", nullable = false)
    private boolean isArchived = false;

    @Column(name = "last_open_at")
    private ZonedDateTime lastOpenAt;
    
    @Builder.Default
    @Column(name = "open_count")
    private int openCount = 0;

    @Column(name = "last_interaction_at")
    private ZonedDateTime lastInteractionAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private ZonedDateTime updatedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "card_tags",
        joinColumns = @JoinColumn(name="card_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @BatchSize(size = 20) // JPA 會自動幫你把 N+1 查詢優化成 (1 + 1) 查詢
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    public void updateTags(Set<Tag> newTags) {
        if (newTags == null) {
            this.tags.clear();
            return;
        }

        // 1. 精準刪除：移除「原本有，但新清單中沒有」的標籤
        this.tags.removeIf(existingTag -> !newTags.contains(existingTag));

        // 2. 精準新增：只加入「新清單有，但原本沒有」的標籤
        for (Tag nextTag : newTags) {
            if (!this.tags.contains(nextTag)) {
                this.tags.add(nextTag);
            }
        }
    }
}