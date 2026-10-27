package com.echogallery.experiment;
import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
@Entity
@Table(name = "experiment_method_preferences", uniqueConstraints = {
    @UniqueConstraint(name = "uk_preference_method", columnNames = {"experiment_id", "method_id"}),
    @UniqueConstraint(name = "uk_preference_slot", columnNames = {"experiment_id", "favorite_slot"})
}, indexes = @Index(name = "idx_preference_recent", columnList = "experiment_id, last_used_at"))
@Check(constraints = "favorite_slot is null or favorite_slot between 1 and 3")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExperimentMethodPreference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "method_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThreadMethod method;
    @Column(name = "last_used_at") private ZonedDateTime lastUsedAt;
    @Column(name = "favorite_slot") private Integer favoriteSlot;
}
