package com.echogallery.overview;

import java.time.LocalDate;
import java.util.List;

public record CardReturnOverviewResponse(
        CardStateResponse state,
        List<CadenceBandResponse> cadenceBands,
        List<ForecastDayResponse> forecastDays,
        List<SnoozeBandResponse> snoozeBands) {

    public record CardStateResponse(
            int recurringCardCount,
            int pausedCardCount,
            int archivedCardCount,
            int needsProcessingCardCount,
            int neverReviewedCardCount,
            int scheduleIssueCardCount) {
    }

    public record CadenceBandResponse(String key, int cardCount) {
    }

    public record ForecastDayResponse(LocalDate date, int cardCount) {
    }

    public record SnoozeBandResponse(String key, int cardCount) {
    }
}
