package com.echogallery.overview;

import java.time.ZonedDateTime;
import java.util.List;

public record OverviewResponse(
        int periodDays,
        ZonedDateTime periodStartAt,
        ZonedDateTime periodEndAt,
        OverviewCurrentResponse current,
        OverviewPeriodResponse period) {

    public record OverviewCurrentResponse(
            int todayReturnPoolCount,
            int recurringCardCount,
            int pausedCardCount,
            int needsProcessingCardCount,
            int activeExperimentCount,
            long treasureCount,
            List<ExperimentTryResponse> experimentTries,
            List<com.echogallery.treasure.TreasureResponses.Highlight> treasureHighlights,
            List<AttentionSignalResponse> attentionSignals) {
    }

    public record OverviewPeriodResponse(
            FlowMetricResponse flow,
            GenerativityMetricResponse generativity,
            com.echogallery.treasure.TreasureResponses.Growth treasureGrowth,
            List<com.echogallery.treasure.TreasureResponses.Event> recentTreasureEvents,
            List<ActivityResponse> activities,
            List<DerivedCardResponse> derivedCards,
            List<ExperimentMaterialResponse> recentExperimentMaterials) {
    }

    public record FlowMetricResponse(int reengagedCardCount, int reviewedCardCount) {
    }

    public record GenerativityMetricResponse(int derivedCardCount, int sourceCardCount) {
    }

    public record ActivityResponse(String key, int value) {
    }

    public record DerivedCardResponse(
            Long cardId,
            String title,
            ZonedDateTime createdAt,
            Long experimentId,
            String experimentTitle,
            List<LineageCardResponse> sourceCards,
            List<String> tags) {
    }

    public record ExperimentMaterialResponse(
            Long cardId,
            String title,
            ZonedDateTime addedAt,
            Long experimentId,
            String experimentTitle,
            String sourceKind,
            List<String> tags) {
    }

    public record LineageCardResponse(Long cardId, String title) {
    }

    public record ExperimentTryResponse(Long experimentId, String experimentTitle, String currentTry, long explorationRecordCount) {
    }

    public record AttentionSignalResponse(String key, int cardCount) {
    }
}
