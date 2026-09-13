package com.echogallery.user;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_users_demo_expiry", columnList = "is_demo_session, demo_expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    @Column(name = "show_content_preview")
    private Boolean showContentPreview;

    @Builder.Default
    @Column(name = "is_demo_session", nullable = false)
    private boolean demoSession = false;

    @Column(name = "demo_library", length = 40)
    private String demoLibrary;

    @Column(name = "demo_expires_at")
    private ZonedDateTime demoExpiresAt;
}
