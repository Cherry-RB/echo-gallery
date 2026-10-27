package com.echogallery.experiment;
import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
@Entity
@Table(name = "experiment_exploration_records", indexes = {
    @Index(name = "idx_discoveries_experiment_time", columnList = "experiment_id, occurred_at, id"),
    @Index(name = "idx_discoveries_thread_time", columnList = "thread_id, occurred_at, id"),
    @Index(name = "idx_discoveries_method_time", columnList = "method_id, occurred_at, id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExperimentExplorationRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThread thread;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "method_id") private ExplorationThreadMethod method;
    @Column(nullable = false, columnDefinition = "TEXT") private String discovery;
    @Column(name = "occurred_at", nullable = false) private ZonedDateTime occurredAt;
    @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private ZonedDateTime updatedAt;
    public String getTryText() { return method == null ? null : method.getContent(); }
}
