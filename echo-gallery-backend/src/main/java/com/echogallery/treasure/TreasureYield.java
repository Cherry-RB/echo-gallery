package com.echogallery.treasure;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import com.echogallery.card.Card;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treasure_yields", indexes = @Index(name = "idx_treasure_yields_date", columnList = "treasure_id,occurred_on"))
@Getter @Setter @NoArgsConstructor
public class TreasureYield {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treasure_id", nullable = false)
    private Treasure treasure;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_card_id")
    private Card relatedCard;
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
