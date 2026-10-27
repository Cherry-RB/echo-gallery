package com.echogallery.experiment;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExplorationOperationReceiptRepository extends JpaRepository<ExplorationOperationReceipt, Long> {
    Optional<ExplorationOperationReceipt> findByUserIdAndRequestId(Long userId, String requestId);
    void deleteByExpiresAtBefore(ZonedDateTime time);
    void deleteByUserId(Long userId);
}
