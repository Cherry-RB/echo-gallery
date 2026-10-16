package com.echogallery.treasure;

import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treasure_trials", uniqueConstraints = @UniqueConstraint(name = "uq_treasure_trial_rank", columnNames = {"treasure_id", "target_rank"}), indexes = @Index(name = "idx_treasure_trials_time", columnList = "treasure_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class TreasureTrial {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "treasure_id", nullable = false)
    private Treasure treasure;
    @Enumerated(EnumType.STRING) @Column(name = "target_rank", nullable = false, length = 10)
    private TreasureRank targetRank;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String criteria;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private TrialStatus status = TrialStatus.PLANNED;
    @Column(name = "result_note", columnDefinition = "TEXT")
    private String resultNote;
    @Column(name = "evidence_url", length = 2048)
    private String evidenceUrl;
    @Column(name = "attempted_at")
    private ZonedDateTime attemptedAt;
    @Column(name = "passed_at")
    private ZonedDateTime passedAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
