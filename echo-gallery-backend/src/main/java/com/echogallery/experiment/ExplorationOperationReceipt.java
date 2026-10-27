package com.echogallery.experiment;
import java.time.ZonedDateTime;
import com.echogallery.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
@Entity
@Table(name = "exploration_operation_receipts", uniqueConstraints = @UniqueConstraint(name = "uk_exploration_receipt", columnNames = {"user_id", "request_id"}),
    indexes = @Index(name = "idx_exploration_receipt_expiry", columnList = "expires_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExplorationOperationReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE) private User user;
    @Column(name = "request_id", nullable = false, length = 36) private String requestId;
    @Column(name = "payload_hash", nullable = false, length = 64) private String payloadHash;
    @Column(name = "result_json", nullable = false, columnDefinition = "TEXT") private String resultJson;
    @Column(name = "expires_at", nullable = false) private ZonedDateTime expiresAt;
}
