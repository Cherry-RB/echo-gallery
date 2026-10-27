package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

public record ExplorationThreadResponse(
        Long id, Long experimentId, String name, ExplorationThreadStatus status, String conclusion,
        int sortOrder, ZonedDateTime lastActivityAt, ZonedDateTime createdAt, ZonedDateTime updatedAt,
        List<MethodResponse> methods, List<RecordResponse> records,
        List<CardResponse> cards, List<WorkResponse> works, long version, long unassignedVersion, String systemKey,
        long methodCount, long recordCount, boolean hasUnassignedHistory, long unassignedRecordCount) {
    public record MethodResponse(Long id, String content, int sortOrder, Long sourceRecordId,
            ZonedDateTime createdAt, ZonedDateTime updatedAt, long version, long discoveryCount, boolean hasHistory, ZonedDateTime lastOrganizedAt) {}
    public record RecordResponse(Long id, Long methodId, String discovery, int sortOrder,
            Long sourceRecordId, ZonedDateTime occurredAt, ZonedDateTime createdAt, ZonedDateTime updatedAt, long version, Long threadId, String tryText) {}
    public record CardResponse(Long id, Long cardId, String cardTitle, String cardNote, String cardType, ExplorationThreadCardRole role,
            String note, int sortOrder, boolean cardArchived, ZonedDateTime createdAt) {}
    public record WorkResponse(Long id, Long workId, String workTitle, CreativeWorkStage stage,
            boolean workArchived, ZonedDateTime createdAt) {}
    public enum CreativeWorkStage { WORKING, FIRST_DRAFT, REVISING, REVISED, SHAREABLE }
}
