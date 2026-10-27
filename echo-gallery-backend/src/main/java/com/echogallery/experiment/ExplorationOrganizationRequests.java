package com.echogallery.experiment;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.echogallery.card.CreateCardRequest;

public final class ExplorationOrganizationRequests {
    private ExplorationOrganizationRequests() {}
    public enum Mode { HISTORY, UPDATE_METHOD, CARD }
    public record Selection(@NotNull Long threadId, Long methodId, @Size(max = 100) List<@NotNull Long> recordIds,
        Boolean allRecords, Boolean includeMethod, Boolean includeHistory) {
        public Selection { allRecords = Boolean.TRUE.equals(allRecords); includeMethod = Boolean.TRUE.equals(includeMethod); includeHistory = Boolean.TRUE.equals(includeHistory); }
    }
    public record Preview(@NotNull Mode mode, @NotEmpty @Size(max = 100) List<@NotNull @Valid Selection> groups, Long targetCardId) {}
    public record Draft(@NotBlank String key, @NotBlank @Size(max = 500000) String text) {}
    public record Commit(@NotBlank @Size(max = 1000000) String token,
        @NotBlank @Pattern(regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId,
        @NotEmpty @Size(max = 100) List<@NotNull @Valid Draft> drafts, Boolean clearRecords,
        @Size(max = 2000) String newMethodContent, @Valid CreateCardRequest newCard,
        @Size(max = 500000) String cardContent, @Min(0) Long expectedFocusVersion,
        @Size(max = 3) List<@NotNull Long> focusFavoriteMethodIds) {
        public Commit { clearRecords = Boolean.TRUE.equals(clearRecords); }
        public Commit(String token, String requestId, List<Draft> drafts, Boolean clearRecords,
                String newMethodContent, CreateCardRequest newCard, String cardContent) {
            this(token, requestId, drafts, clearRecords, newMethodContent, newCard, cardContent, null, null);
        }
    }
}
