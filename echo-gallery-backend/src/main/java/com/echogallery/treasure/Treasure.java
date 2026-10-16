package com.echogallery.treasure;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.echogallery.card.Card;
import com.echogallery.experiment.Experiment;
import com.echogallery.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treasures", indexes = {
    @Index(name = "idx_treasures_owner_archive_time", columnList = "user_id,is_archived,updated_at"),
    @Index(name = "idx_treasures_owner_rank", columnList = "user_id,rank")
})
@Getter
@Setter
@NoArgsConstructor
public class Treasure {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column(nullable = false, length = 255)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(name = "artifact_url", length = 2048)
    private String artifactUrl;
    @Column(name = "cover_image_url", length = 2048)
    private String coverImageUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private TreasureRank rank = TreasureRank.STONE;
    @Column(name = "acquired_on", nullable = false)
    private LocalDate acquiredOn;
    @Column(name = "is_archived", nullable = false)
    private boolean archived;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_card_id")
    private Card sourceCard;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_experiment_id")
    private Experiment sourceExperiment;
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
