package com.echogallery.treasure;

import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "treasure_relations",
    uniqueConstraints = @UniqueConstraint(name = "uq_treasure_relation", columnNames = {"source_treasure_id", "target_treasure_id"}),
    indexes = @Index(name = "idx_treasure_relation_target", columnList = "target_treasure_id"))
@Getter @Setter @NoArgsConstructor
public class TreasureRelation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_treasure_id", nullable = false)
    private Treasure source;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_treasure_id", nullable = false)
    private Treasure target;
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
