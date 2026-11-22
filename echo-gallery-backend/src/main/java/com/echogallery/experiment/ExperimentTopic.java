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
@Table(name = "experiment_topics", uniqueConstraints = @UniqueConstraint(name = "uk_experiment_topic_name", columnNames = { "experiment_id", "name" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @Column(nullable = false, length = 100) private String name;
    @Column(name = "pinned_slot") private Integer pinnedSlot;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private java.time.ZonedDateTime createdAt;

    public ExperimentTopic(Experiment experiment, String name) { this.experiment = experiment; this.name = name; }
    public void setPinnedSlot(Integer pinnedSlot) { this.pinnedSlot = pinnedSlot; }
}
