package com.echogallery.experiment;
import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
@Entity
@Table(name = "experiment_explorations", uniqueConstraints = @UniqueConstraint(name = "uk_experiment_explorations_experiment", columnNames = "experiment_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExperimentExploration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_thread_id")
    @OnDelete(action = OnDeleteAction.SET_NULL) private ExplorationThread currentThread;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "current_method_id")
    @OnDelete(action = OnDeleteAction.SET_NULL) private ExplorationThreadMethod currentMethod;
    @Column(nullable = false) private long version;
    @Column(name = "order_version", nullable = false) private long orderVersion;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private ZonedDateTime updatedAt;
    public String getCurrentTry() { return currentMethod == null ? null : currentMethod.getContent(); }
}
