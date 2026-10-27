package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class ExplorationThreadRequests {
    private ExplorationThreadRequests() {}

    public record ThreadRequest(
            @NotBlank @Size(max = 255) String name,
            ExplorationThreadStatus status,
            @Size(max = 10000) String conclusion, Long expectedVersion) {
        public ThreadRequest(String name, ExplorationThreadStatus status, String conclusion) { this(name, status, conclusion, null); }
    }

    public record OrderRequest(@NotEmpty List<@NotNull Long> threadIds, @NotNull Long expectedVersion) {}
    public record PositionRequest(@NotNull Long targetThreadId, boolean after, @NotNull Long expectedVersion) {}
    public record MethodRequest(@NotBlank @Size(max = 2000) String content, Long expectedVersion) {}
    public record RecordRequest(Long methodId, @NotBlank @Size(max = 10000) String discovery,
            Long expectedVersion, ZonedDateTime occurredAt, Long expectedMethodVersion) {}
    public record ImportItem(
            @NotNull Long recordId,
            boolean includeMethod,
            boolean includeDiscovery,
            @Size(max = 2000) String methodContent,
            @Size(max = 10000) String discovery,
            Long targetMethodId) {}
    public record ImportRequest(@NotEmpty @Size(max = 100) List<@NotNull @Valid ImportItem> items) {}
    public record NewImportRequest(@NotBlank @Size(max = 255) String name,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ImportItem> items) {}
    public record DiscoveryRequest(Long methodId, @Size(max = 500) String tryText,
            @NotBlank @Size(max = 1000) String discovery, Long expectedMethodVersion) {}
    public record NoteRequest(@Size(max = 1000) String note) {}
    public record CardsRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> cardIds,
            @NotNull ExplorationThreadCardRole role) {}
    public record NewMaterialRequest(@NotBlank @Size(max = 255) String name, @NotNull Long cardId) {}
    public record CardRequest(@NotNull Long cardId, @NotNull ExplorationThreadCardRole role,
            @Size(max = 1000) String note) {}
    public record WorkRequest(@NotNull Long workId) {}
}
