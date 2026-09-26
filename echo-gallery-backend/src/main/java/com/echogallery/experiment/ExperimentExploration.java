package com.echogallery.experiment;

import java.time.ZonedDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "experiment_explorations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_experiment_explorations_experiment",
                columnNames = "experiment_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExperimentExploration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Experiment experiment;

    @Column(name = "current_try", columnDefinition = "TEXT")
    private String currentTry;

    @Column(name = "favorite_try_1", columnDefinition = "TEXT")
    private String favoriteTry1;

    @Column(name = "favorite_try_2", columnDefinition = "TEXT")
    private String favoriteTry2;

    @Column(name = "favorite_try_3", columnDefinition = "TEXT")
    private String favoriteTry3;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
