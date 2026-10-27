package com.echogallery.experiment;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @RequiredArgsConstructor
public class ExplorationReceiptCleanup {
    private final ExplorationOperationReceiptRepository receipts;
    private final Clock clock;
    @Scheduled(fixedDelayString = "${app.exploration.receipt-cleanup-ms:3600000}")
    @Transactional
    public void cleanup() { receipts.deleteByExpiresAtBefore(ZonedDateTime.now(clock)); }
}
