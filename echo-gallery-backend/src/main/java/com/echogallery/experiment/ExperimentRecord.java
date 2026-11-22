package com.echogallery.experiment;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 探索紀錄獨立保存文字，避免修改方法設定時改寫歷史內容。 */
@Entity
@Table(name = "experiment_records", indexes = {
        @Index(name = "idx_exp_records_recorded", columnList = "experiment_id, recorded_on, id"),
        @Index(name = "idx_exp_records_created", columnList = "experiment_id, created_at, id"),
        @Index(name = "idx_exp_records_updated", columnList = "experiment_id, updated_at, id")
}, uniqueConstraints = @UniqueConstraint(name = "uk_exp_records_request", columnNames = {"experiment_id", "create_request_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Experiment experiment;

    @Column(length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "practice_text", columnDefinition = "TEXT")
    private String practiceText;

    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    @Column(name = "is_understanding_node", nullable = false)
    private boolean understandingNode;

    @Column(name = "is_method_candidate", nullable = false)
    private boolean methodCandidate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExperimentRecordDraft.Highlight highlight;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "create_request_id", nullable = false, updatable = false)
    private UUID createRequestId;

    @Column(name = "create_payload_hash", nullable = false, updatable = false, length = 64)
    private String createPayloadHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public ExperimentRecord(Experiment experiment, UUID requestId, String payloadHash, ExperimentRecordDraft draft) {
        this.experiment = java.util.Objects.requireNonNull(experiment);
        this.createRequestId = java.util.Objects.requireNonNull(requestId);
        this.createPayloadHash = java.util.Objects.requireNonNull(payloadHash);
        apply(draft);
    }

    public void apply(ExperimentRecordDraft draft) {
        var value = java.util.Objects.requireNonNull(draft);
        title = value.text().title();
        content = value.text().content();
        practiceText = value.text().practiceText();
        recordedOn = value.recordedOn();
        understandingNode = value.understandingNode();
        methodCandidate = value.methodCandidate();
        highlight = value.highlight();
    }
}
