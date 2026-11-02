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
    public record AssociationReason(Kind kind, Long sourceId, String label, boolean direct) {}
    public record Association(Long goalId, String name, Status status, List<AssociationReason> reasons) {}
    public record Metric(String metricKey, Long value, String unit, String mode, LocalDate startsOn,
            LocalDate endsOn, ZonedDateTime coverageStart, String coverageStatus, long definitionVersion) {}
    public record Evidence(String id, String type, ZonedDateTime occurredAt, ZonedDateTime recordedAt,
            String sourceKind, Long sourceId, boolean excluded, long version, String reason) {}
    public record EvidencePage(List<Evidence> content, long totalElements, int page, int size, int totalPages) {}
    public record EvidenceChange(boolean excluded, @NotBlank @Size(max=2000) String reason, @NotNull @Min(0) Long version) {}
    public enum LogKind { NOTE, MEASUREMENT }
    public record ProgressLog(Long id, LogKind kind, LocalDate occurredOn, String content, java.math.BigDecimal amount,
            String unit, long version, ZonedDateTime createdAt, ZonedDateTime updatedAt) {}
    public record ProgressLogSave(@NotNull LogKind kind, @NotNull LocalDate occurredOn, @Size(max=5000) String content,
            @DecimalMin(value="0.01") java.math.BigDecimal amount, @Size(max=80) String unit, @NotNull @Min(0) Long version) {}
    public enum ReviewStatus { DRAFT, SUBMITTED }
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record ReviewContent(@Size(max=5000) String observation, @Size(max=5000) String decision,
            @Pattern(regexp="ALIGNED|ADJUST|UNCERTAIN|PAUSE|COMPLETE|STOP|", message="方向選項不正確") String direction,
            @Min(1) @Max(5) Integer informationHelpfulness, @Min(1) @Max(5) Integer actionEffectiveness) {}
    @JsonIgnoreProperties(ignoreUnknown=true)
    public record ReviewSnapshot(Integer formatVersion, Definition definition, List<Metric> metrics,
            List<Source> sources, List<ProgressLog> logs, Long cardCount,
            ZonedDateTime coverageAt, Status resultingStatus) {}
    public record Review(Long id, LocalDate startsOn, LocalDate endsOn, ReviewStatus status,
            ReviewContent content, long version, ZonedDateTime updatedAt, ZonedDateTime submittedAt,
            ReviewSnapshot snapshot, long goalVersion) {}
    public record ReviewSave(@Valid @NotNull ReviewContent content, @NotNull @Min(0) Long version) {}
    public record ReviewSubmit(@NotNull @Min(0) Long version, @NotNull UUID requestId,
            @NotNull @Min(1) Long goalVersion, Status targetStatus) {}
    public record Reminder(Long goalId, String goalName, LocalDate dueOn, ZonedDateTime snoozedUntil, long version) {}
    public record ReminderSnooze(@NotNull @Positive Long goalId, @NotNull ZonedDateTime until, @NotNull @Min(0) Long version) {}
}
