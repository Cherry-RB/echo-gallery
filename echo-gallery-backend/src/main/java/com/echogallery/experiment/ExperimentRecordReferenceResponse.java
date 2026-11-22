package com.echogallery.experiment;

import java.time.ZonedDateTime;

public record ExperimentRecordReferenceResponse(Long id, ExperimentRecordReference.Kind kind, Long cardId,
        String title, String url, String label, ZonedDateTime createdAt) {
    static ExperimentRecordReferenceResponse from(ExperimentRecordReference reference) {
        return new ExperimentRecordReferenceResponse(reference.getId(), reference.getKind(),
                reference.getCard() == null ? null : reference.getCard().getId(),
                reference.getCard() == null ? null : reference.getCard().getTitle(), reference.getUrl(),
                reference.getLabel(), reference.getCreatedAt());
    }
}
