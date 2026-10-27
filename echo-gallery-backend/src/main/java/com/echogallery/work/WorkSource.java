package com.echogallery.work;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.echogallery.card.Card;

@Entity @Table(name = "creative_work_sources", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"work_id", "card_id"})})
@Getter @Setter
public class WorkSource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "work_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private CreativeWork work;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "card_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Card card;
}
