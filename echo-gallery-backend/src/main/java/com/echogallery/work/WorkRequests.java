package com.echogallery.work;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class WorkRequests {
    private WorkRequests() {}
    public record Info(@NotBlank @Size(max = 255) String title,
            @Size(max = 50000) String description, Long experimentId,
            @NotNull CreativeWork.Mode editorMode, @Size(max = 2048) String externalUrl,
            @Size(max = 100) List<@NotNull @Valid Source> sources) {}
    public record Body(@NotNull @Size(max = 500000) String body, @NotNull @Min(0) Long expectedVersion) {}
    public record Stage(@NotNull CreativeWork.Stage stage, LocalDate firstCompletedOn) {}
    public record NextStep(@Size(max = 50000) String nextStep) {}
    public record Share(boolean published, @Size(max = 2048) String shareUrl,
            LocalDate sharedOn, @Size(max = 50000) String shareNote) {}
    public record Source(Long cardId, Long recordId) {}
    public record Sources(@NotNull @Size(min = 1, max = 100) List<@NotNull @Valid Source> sources) {}
    public record Link(@NotNull @Positive Long treasureId) {}
}
