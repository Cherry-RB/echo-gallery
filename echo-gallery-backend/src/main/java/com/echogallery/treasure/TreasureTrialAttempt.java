package com.echogallery.treasure;

import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treasure_trial_attempts", indexes = @Index(name = "idx_trial_attempt_history", columnList = "trial_id,attempted_at"))
@Getter @Setter @NoArgsConstructor
public class TreasureTrialAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trial_id", nullable = false)
    private TreasureTrial trial;
    @Column(name = "title_snapshot", nullable = false, length = 255)
    private String titleSnapshot;
    @Column(name = "criteria_snapshot", nullable = false, columnDefinition = "TEXT")
    private String criteriaSnapshot;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TrialStatus status;
    @Column(name = "result_note", nullable = false, columnDefinition = "TEXT")
    private String resultNote;
    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;
    @Column(name = "attempted_at", nullable = false)
    private ZonedDateTime attemptedAt;
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
