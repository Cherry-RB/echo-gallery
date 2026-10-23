package com.echogallery.experiment;

import java.time.ZonedDateTime;
import org.hibernate.annotations.*;
import com.echogallery.card.Card;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exploration_thread_cards", uniqueConstraints = @UniqueConstraint(name = "uk_thread_cards_role", columnNames = { "thread_id", "card_id", "role" }), indexes = @Index(name = "idx_thread_cards_card", columnList = "card_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationThreadCard {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThread thread;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Card card;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ExplorationThreadCardRole role;
    @Column(columnDefinition = "TEXT") private String note;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
}
