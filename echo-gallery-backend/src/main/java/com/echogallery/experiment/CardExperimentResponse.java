package com.echogallery.experiment;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardExperimentResponse(
        Long experimentId,
        String experimentTitle,
        String experimentHypothesis,
        boolean experimentArchived,
        ExperimentStage stage,
        String note,
        ZonedDateTime addedAt) {

    @JsonProperty("gardenId")
    public Long legacyGardenId() {
        return experimentId;
    }

    @JsonProperty("gardenTitle")
    public String legacyGardenTitle() {
        return experimentTitle;
    }

    @JsonProperty("gardenHypothesis")
    public String legacyGardenHypothesis() {
        return experimentHypothesis;
    }

    @JsonProperty("gardenArchived")
    public boolean legacyGardenArchived() {
        return experimentArchived;
    }
}
