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
            int issueWithNextStepCount,
            List<ExperimentTryResponse> experimentTries,
            List<NextStepResponse> nextSteps,
            List<AttentionSignalResponse> attentionSignals) {
    }

    public record OverviewPeriodResponse(
            FlowMetricResponse flow,
            GenerativityMetricResponse generativity,
            ClosureMetricResponse closure,
            List<ActivityResponse> activities,
            List<DerivedCardResponse> derivedCards,
            List<ExperimentMaterialResponse> recentExperimentMaterials) {
    }

    public record FlowMetricResponse(int reengagedCardCount, int reviewedCardCount) {
    }

    public record GenerativityMetricResponse(int derivedCardCount, int sourceCardCount) {
    }

    public record ClosureMetricResponse(int issueWithFollowUpCount) {
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

    public record NextStepResponse(Long issueId, String issueTitle, String nextStep, ZonedDateTime updatedAt, long progressUpdateCount) {
    }

    public record ExperimentTryResponse(Long experimentId, String experimentTitle, String currentTry, long explorationRecordCount) {
    }

    public record AttentionSignalResponse(String key, int cardCount) {
    }
}
