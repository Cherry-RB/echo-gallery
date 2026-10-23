package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

public record ExperimentExplorationResponse(
        String currentTry,
        Long currentTrySourceMethodId,
        List<String> favoriteTries,
        List<RecordResponse> records) {

    public record RecordResponse(
            Long id,
            String tryText,
            Long sourceThreadMethodId,
            String discovery,
            ZonedDateTime createdAt,
            List<CardExportResponse> exports) {
    }

    public record CardExportResponse(
            Long cardId,
            String cardTitle,
            ZonedDateTime exportedAt) {
    }
}
