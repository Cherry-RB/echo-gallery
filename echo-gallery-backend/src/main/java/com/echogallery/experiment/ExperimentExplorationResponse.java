package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

public record ExperimentExplorationResponse(
        String currentTry,
        List<String> favoriteTries,
        List<RecordResponse> records) {

    public record RecordResponse(
            Long id,
            String tryText,
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
