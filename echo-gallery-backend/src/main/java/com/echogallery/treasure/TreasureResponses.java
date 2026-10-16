package com.echogallery.treasure;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public final class TreasureResponses {
    private TreasureResponses() {}

    public record Summary(Long id, String title, String description, String coverImageUrl, TreasureRank rank,
            LocalDate acquiredOn, boolean isArchived, ZonedDateTime createdAt, ZonedDateTime updatedAt,
            Trial activeTrial, long yieldCount) {}
    public record Reference(Long id, String title) {}
    public record Detail(Long id, String title, String description, String artifactUrl, String coverImageUrl,
            TreasureRank rank, TreasureRank nextRank, LocalDate acquiredOn, boolean isArchived,
            Reference sourceCard, Reference sourceExperiment, List<Reference> sourceTreasures,
            Trial activeTrial, long trialCount, long yieldCount, ZonedDateTime createdAt, ZonedDateTime updatedAt) {}
    public record Trial(Long id, TreasureRank targetRank, String title, String criteria, TrialStatus status,
            String resultNote, String evidenceUrl, ZonedDateTime attemptedAt, ZonedDateTime passedAt,
            ZonedDateTime createdAt, ZonedDateTime updatedAt) {}
    public record Yield(Long id, String title, String description, LocalDate occurredOn, Reference relatedCard,
            ZonedDateTime createdAt, ZonedDateTime updatedAt) {}
    public record Attempt(Long id, String titleSnapshot, String criteriaSnapshot, TrialStatus status,
            String resultNote, String evidenceUrl, ZonedDateTime attemptedAt, ZonedDateTime createdAt) {}
    public record Stats(long total, long stone, long bronze, long silver, long gold) {}
    public record Highlight(Long treasureId, String title, TreasureRank rank, String highlightKind,
            String highlightTitle, ZonedDateTime eventAt, Long trialId) {}
    public record Growth(long collectedCount, long promotedTreasureCount, long yieldTreasureCount, long yieldRecordCount) {}
    public record Event(Long treasureId, String treasureTitle, String kind, String eventTitle,
            ZonedDateTime eventAt, TreasureRank rank) {}
}
