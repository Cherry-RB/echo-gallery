package com.echogallery.work;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.echogallery.card.Card;
import com.echogallery.experiment.ExperimentExplorationRecord;

@Entity @Table(name = "creative_work_sources", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"work_id", "card_id"}),
        @UniqueConstraint(columnNames = {"work_id", "record_id"})})
@Check(constraints = "(card_id is null) <> (record_id is null)")
@Getter @Setter
public class WorkSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "work_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private CreativeWork work;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "card_id")
    @OnDelete(action = OnDeleteAction.CASCADE) private Card card;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "record_id")
    @OnDelete(action = OnDeleteAction.CASCADE) private ExperimentExplorationRecord record;
}
