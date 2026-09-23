package com.echogallery.experiment;

import java.time.ZonedDateTime;

public record ExperimentResponse(
        Long id,
        String title,
        String description,
        String hypothesis,
        ExperimentThemeColor themeColor,
        boolean isArchived,
        long seedCount,
        long growingCount,
        long matureCount,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt) {
}
