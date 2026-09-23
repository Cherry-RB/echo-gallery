package com.echogallery.experiment;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardRelationResponse(
        Long id,
        Long experimentId,
        String experimentTitle,
        String experimentHypothesis,
        CardRelationType relationType,
        CardLineageCardResponse sourceCard,
        CardLineageCardResponse derivedCard,
        ZonedDateTime createdAt) {

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
}
