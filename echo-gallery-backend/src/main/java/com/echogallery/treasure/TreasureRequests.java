package com.echogallery.treasure;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public final class TreasureRequests {
    private TreasureRequests() {}

    public record Content(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 50000) String description,
            @Size(max = 2048) String artifactUrl,
            @Size(max = 2048) String coverImageUrl,
            @NotNull LocalDate acquiredOn,
            @Positive Long sourceCardId,
            @Positive Long sourceExperimentId,
            @Size(max = 20) List<@NotNull @Positive Long> sourceTreasureIds) {}

    public record Trial(
            TreasureRank targetRank,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 50000) String criteria) {}

    public record Result(
            @NotBlank @Size(max = 50000) String resultNote,
            @Size(max = 2048) String evidenceUrl,
            @NotNull ZonedDateTime attemptedAt) {}

    public record Attempt(
            @NotNull TrialStatus status,
            @NotBlank @Size(max = 50000) String resultNote,
            @Size(max = 2048) String evidenceUrl,
            @NotNull ZonedDateTime attemptedAt) {}

    public record Yield(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 50000) String description,
            @NotNull LocalDate occurredOn,
            @Positive Long relatedCardId) {}
}
