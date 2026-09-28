package com.echogallery.issue;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.echogallery.card.Card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "issue_cards",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_cards_issue_card", columnNames = { "issue_id", "card_id" })
    },
    indexes = {
        @Index(name = "idx_issue_cards_issue_status", columnList = "issue_id, status"),
        @Index(name = "idx_issue_cards_card_id", columnList = "card_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Issue issue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Card card;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 20)
    private IssueCardStatus status = IssueCardStatus.CANDIDATE;

    @Column(columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "linked_at", nullable = false, updatable = false)
    private ZonedDateTime linkedAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;
}
