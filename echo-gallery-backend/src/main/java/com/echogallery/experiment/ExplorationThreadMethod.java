package com.echogallery.experiment;

import java.time.ZonedDateTime;
import org.hibernate.annotations.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exploration_thread_methods",
        indexes = {
                @Index(name = "idx_thread_methods_thread_sort", columnList = "thread_id, sort_order"),
                @Index(name = "idx_thread_methods_source_record", columnList = "source_record_id")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_thread_methods_source", columnNames = { "thread_id", "source_record_id" }))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationThreadMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThread thread;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "source_record_id")
    @OnDelete(action = OnDeleteAction.SET_NULL) private ExperimentExplorationRecord sourceRecord;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private ZonedDateTime updatedAt;
}
