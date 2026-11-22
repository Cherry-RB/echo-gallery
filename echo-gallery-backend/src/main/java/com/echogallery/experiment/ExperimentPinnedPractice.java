package com.echogallery.experiment;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "experiment_pinned_practices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_pinned_practice_slot", columnNames = { "experiment_id", "slot" }),
        @UniqueConstraint(name = "uk_pinned_practice_content", columnNames = { "experiment_id", "content_key" }) })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentPinnedPractice {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "content_key", nullable = false, length = 64) private String contentKey;
    @Column(nullable = false) private int slot;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private java.time.ZonedDateTime createdAt;
    public ExperimentPinnedPractice(Experiment experiment, String content, String contentKey, int slot) { this.experiment = experiment; this.content = content; this.contentKey = contentKey; this.slot = slot; }
    public void setSlot(int slot) { this.slot = slot; }
}
