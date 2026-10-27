package com.echogallery.experiment;
import java.time.ZonedDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
@Entity
@Table(name = "exploration_thread_methods", indexes = @Index(name = "idx_thread_methods_thread_sort", columnList = "thread_id, sort_order, id"),
    uniqueConstraints = @UniqueConstraint(name = "uk_method_thread_content", columnNames = {"thread_id", "content_key"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationThreadMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "experiment_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private Experiment experiment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "thread_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private ExplorationThread thread;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "content_key", nullable = false, length = 64) private String contentKey;
    @Builder.Default @Column(name = "history_text", nullable = false, columnDefinition = "TEXT") private String historyText = "";
    @Column(name = "last_organized_at") private ZonedDateTime lastOrganizedAt;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private long version;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private ZonedDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private ZonedDateTime updatedAt;
    @PrePersist @PreUpdate
    void prepare() {
        content = content.strip();
        contentKey = key(content);
        if (experiment == null && thread != null) experiment = thread.getExperiment();
    }
    public static String key(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.strip().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
