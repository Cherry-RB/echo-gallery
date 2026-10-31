package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public final class ExplorationActivityRequests {
    private ExplorationActivityRequests() {}
    public enum MethodAction { UPDATE, COPY }
    public record Focus(Long threadId, Long methodId, @Size(max = 2000) String newContent,
        @NotNull @Min(0) Long expectedVersion, MethodAction action, @Min(0) Long expectedMethodVersion,
        @Size(max = 3) List<@NotNull Long> favoriteMethodIds) {
        public Focus(Long threadId, Long methodId, String newContent, Long expectedVersion) {
            this(threadId, methodId, newContent, expectedVersion, null, null, null);
        }
    }
    public record Favorites(@NotNull @Size(max = 3) List<@NotNull Long> methodIds, @NotNull @Min(0) Long expectedVersion) {}
    public record Method(Long threadId, @NotBlank @Size(max = 2000) String content, Long sourceMethodId, @Min(0) Long expectedVersion) {}
    public record Discovery(Long threadId, Long methodId, @NotBlank @Size(max = 10000) String discovery, ZonedDateTime occurredAt, @Min(0) Long expectedVersion, @Min(0) Long expectedMethodVersion) {}
    public record Assignment(@NotNull Long threadId, Long methodId, @NotNull @Min(0) Long expectedVersion, @Min(0) Long expectedMethodVersion) {}
    public record HistoryUpdate(@NotNull Long threadId, Long methodId, @NotNull String text,
        @NotNull @Min(0) Long expectedVersion) {}
    public record MethodAssignment(@NotNull Long targetThreadId, @NotNull @Min(0) Long expectedVersion) {}
}
