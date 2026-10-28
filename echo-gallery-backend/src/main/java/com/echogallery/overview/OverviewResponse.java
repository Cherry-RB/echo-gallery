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
            long workCount,
            List<ExperimentTryResponse> experimentTries,
            List<com.echogallery.treasure.TreasureResponses.Summary> treasureActions,
            List<AttentionSignalResponse> attentionSignals) {
    }

    public record OverviewPeriodResponse(
            FlowMetricResponse flow,
            com.echogallery.treasure.TreasureResponses.Growth treasureGrowth,
            List<com.echogallery.treasure.TreasureResponses.Event> recentTreasureYields,
            List<com.echogallery.treasure.TreasureResponses.Event> recentTreasureProgress,
            List<ActivityResponse> activities,
            List<ExperimentMaterialResponse> recentExperimentMaterials) {
    }

    public record FlowMetricResponse(int reengagedCardCount, int reviewedCardCount) {
    }

    public record ActivityResponse(String key, int value) {
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

    public record ExperimentTryResponse(Long experimentId, String experimentTitle, String currentTry, long explorationRecordCount) {
    }

    public record AttentionSignalResponse(String key, int cardCount) {
    }
}
