package com.echogallery.experiment;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "experiment_record_topics", uniqueConstraints = @UniqueConstraint(name = "uk_record_topic", columnNames = { "record_id", "topic_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExperimentRecordTopic {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "record_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExperimentRecord record;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "topic_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExperimentTopic topic;
    public ExperimentRecordTopic(Experiment experiment, ExperimentRecord record, ExperimentTopic topic) { this.experiment = experiment; this.record = record; this.topic = topic; }
}
