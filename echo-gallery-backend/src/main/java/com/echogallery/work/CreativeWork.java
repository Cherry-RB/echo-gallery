package com.echogallery.work;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import com.echogallery.user.User;
import com.echogallery.experiment.Experiment;
import com.echogallery.treasure.Treasure;

@Entity @Table(name = "creative_works", indexes = @Index(name = "idx_work_owner_edited", columnList = "user_id,edited_at"))
@Getter @Setter
public class CreativeWork {
    public enum Mode { INTERNAL, EXTERNAL }
    public enum Stage { WORKING, FIRST_DRAFT, REVISING, REVISED, SHAREABLE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private User user;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "experiment_id")
    @OnDelete(action = OnDeleteAction.SET_NULL) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "treasure_id", unique = true)
    @OnDelete(action = OnDeleteAction.SET_NULL) private Treasure treasure;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Mode editorMode = Mode.INTERNAL;
    @Column(nullable = false, columnDefinition = "TEXT") private String body = "";
    @Column(nullable = false) private long bodyVersion;
    @Column(length = 2048) private String externalUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Stage stage = Stage.WORKING;
    @Column(columnDefinition = "TEXT") private String nextStep;
    private LocalDate firstCompletedOn;
    private ZonedDateTime stageChangedAt;
    private boolean published;
    @Column(length = 2048) private String shareUrl;
    private LocalDate sharedOn;
    @Column(columnDefinition = "TEXT") private String shareNote;
    private boolean archived;
    @Column(nullable = false) private ZonedDateTime createdAt;
    @Column(nullable = false) private ZonedDateTime editedAt;
}
