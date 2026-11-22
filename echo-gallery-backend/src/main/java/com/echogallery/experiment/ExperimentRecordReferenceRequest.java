package com.echogallery.experiment;

import jakarta.validation.constraints.Size;

public record ExperimentRecordReferenceRequest(
        ExperimentRecordReference.Kind kind,
        Long cardId,
        @Size(max = 2048) String url,
        @Size(max = 255) String label) {}
