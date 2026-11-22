package com.echogallery.experiment;

import java.time.ZonedDateTime;

import com.echogallery.card.Card;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 探索紀錄附帶的收藏卡片或外部連結。 */
@Entity
@Table(name = "experiment_record_references")
@Getter
@Setter
@NoArgsConstructor
public class ExperimentRecordReference {
    public enum Kind { CARD, URL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExperimentRecord record;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Card card;

    @Column(length = 2048)
    private String url;

    @Column(length = 255)
    private String label;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
