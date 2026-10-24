package com.echogallery.experiment;

import java.time.ZonedDateTime;

public record ExperimentResponse(
        Long id,
        String title,
        String description,
        String hypothesis,
        String conclusion,
        String currentTry,
        long explorationRecordCount,
        ExperimentThemeColor themeColor,
        boolean isArchived,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt) {
}
