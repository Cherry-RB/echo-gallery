package com.echogallery.experiment;

import java.time.ZonedDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exploration_threads", indexes = {
        @Index(name = "idx_threads_experiment_sort", columnList = "experiment_id, sort_order"),
        @Index(name = "idx_threads_experiment_activity", columnList = "experiment_id, last_activity_at")
}, uniqueConstraints = @UniqueConstraint(name = "uk_threads_experiment_migration", columnNames = { "experiment_id", "migration_key" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationThread {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @Column(nullable = false, length = 255) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    @Builder.Default private ExplorationThreadStatus status = ExplorationThreadStatus.NOT_STARTED;
    @Column(columnDefinition = "TEXT") private String conclusion;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "last_activity_at", nullable = false) private ZonedDateTime lastActivityAt;
    @Column(name = "migration_key", length = 50) private String migrationKey;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private ZonedDateTime updatedAt;
}
