package com.echogallery.goal;

import java.time.*;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class GoalData {
    private GoalData() {}
    public enum Status { PLANNED, ACTIVE, PAUSED, COMPLETED, ENDED }
    public enum Kind { TAG, EXPERIMENT, THREAD, WORK, TREASURE, CARD }
    public record Source(@NotNull Kind kind, @NotNull @Positive Long id, boolean excluded) {}
    public record Definition(@NotBlank @Size(max=255) String name, @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn, @Size(max=5000) String expectation, @Size(max=5000) String motivation,
            @Size(max=2048) String imageUrl, @NotNull Status status, boolean archived, boolean pinned,
            @Min(0) int pinOrder, @Min(1) @Max(365) int reviewDays, boolean remindersEnabled,
            boolean allTags, @NotNull @Size(max=200) List<@NotNull @Valid Source> sources,
            @NotNull @Size(max=30) List<@NotBlank String> metrics) {}
    public record Save(@Valid @NotNull Definition definition, @NotNull Long version,
            @Size(max=2000) String reason, @NotNull UUID requestId) {}
    public record View(Long id, Definition definition, Status effectiveStatus, long version,
            long cardCount, ZonedDateTime trackingStartedAt, ZonedDateTime closedAt) {}
    public enum ArchiveScope { UNARCHIVED, ARCHIVED, ALL }
    public enum Direction { UP, DOWN }
    public record PinOrder(@NotNull Direction direction, @NotNull Long version,
            @NotNull @Positive Long adjacentId, @NotNull Long adjacentVersion) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record TrackingPreview(boolean allTags,
            @NotNull @Size(max=200) List<@NotNull @Valid Source> sources) {}
    public record Option(Kind kind, Long id, String title, Long parentId, String parentTitle) {}
    public record Preference(boolean allActive, @NotNull @Size(max=200) List<@NotNull @Positive Long> goalIds,
            boolean includePaused, boolean includeArchived, @NotNull Long version) {}
    public record Board(@Valid @NotNull Preference selection, @NotNull UUID seed,
            @Size(max=20000) String cursor, @Size(max=5) List<@NotNull @Positive Long> pageIds) {}
    public record Associations(@NotNull @Size(max=100) List<@NotNull @Valid Source> objects) {}
    public record Association(Long goalId, String name, Status status, List<String> reasons) {}
}
