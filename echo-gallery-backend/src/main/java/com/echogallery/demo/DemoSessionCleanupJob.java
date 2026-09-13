package com.echogallery.demo;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DemoSessionCleanupJob {
    private final DemoSessionService demoSessionService;

    @Scheduled(fixedDelayString = "${app.demo.cleanup.fixed-delay-ms:900000}")
    public void clearExpiredSessions() {
        demoSessionService.clearExpiredSessions();
    }
}
