package com.echogallery.experiment;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardExperimentContextResponse(
        List<CardExperimentResponse> experiments,
        List<CardRelationResponse> relations) {

    @JsonProperty("gardens")
    public List<CardExperimentResponse> legacyGardens() {
        return experiments;
    }
}
