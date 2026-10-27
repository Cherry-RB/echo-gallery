package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

public record ExperimentExplorationResponse(
    String currentTry, Long currentTrySourceMethodId, List<String> favoriteTries, List<RecordResponse> records,
    Long currentThreadId, MethodOption currentMethod, List<MethodOption> recentMethods, List<MethodOption> favoriteMethods,
    long version, long recordCount) {
    public record MethodOption(Long id, Long threadId, String threadName, String content, long version,
        long discoveryCount, boolean hasHistory, ZonedDateTime lastOrganizedAt) {}
    public record RecordResponse(Long id, String tryText, Long sourceThreadMethodId, String discovery, ZonedDateTime createdAt,
        List<CardExportResponse> exports, Long threadId, String threadName, Long methodId, long version,
        ZonedDateTime occurredAt, ZonedDateTime updatedAt) {}
    public record CardExportResponse(Long cardId, String cardTitle, ZonedDateTime exportedAt) {}
    public record History(String text, long version, ZonedDateTime lastOrganizedAt) {}
}
