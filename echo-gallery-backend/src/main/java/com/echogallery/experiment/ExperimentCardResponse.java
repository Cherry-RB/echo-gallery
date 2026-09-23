package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

public record ExperimentCardResponse(
        Long cardId,
        String cardType,
        String cardTitle,
        String cardReason,
        String cardSummary,
        List<String> cardTags,
        boolean cardArchived,
        Integer intervalDays,
        ZonedDateTime nextShowAt,
        boolean needsProcessing,
        ExperimentStage stage,
        String note,
        ZonedDateTime addedAt) {
}
