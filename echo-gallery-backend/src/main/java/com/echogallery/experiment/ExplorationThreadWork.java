package com.echogallery.experiment;

import java.time.ZonedDateTime;
import org.hibernate.annotations.*;
import com.echogallery.work.CreativeWork;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "exploration_thread_works", uniqueConstraints = @UniqueConstraint(name = "uk_thread_works", columnNames = { "thread_id", "work_id" }), indexes = @Index(name = "idx_thread_works_work", columnList = "work_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationThreadWork {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThread thread;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "work_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private CreativeWork work;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
}
