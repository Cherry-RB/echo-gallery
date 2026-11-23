package com.echogallery.experiment;

import java.time.LocalDate;
import java.time.ZonedDateTime;

/** API 回傳的完整探索紀錄，不以共享方法覆寫歷史文字。 */
public record ExperimentRecordResponse(
        Long id, String title, String content, String practiceText, LocalDate recordedOn,
        boolean understandingNode, boolean methodCandidate, ExperimentRecordDraft.Highlight highlight,
        long version, ZonedDateTime createdAt, ZonedDateTime updatedAt,
        java.util.List<ExperimentOrganizationResponses.Topic> topics) {

    static ExperimentRecordResponse from(ExperimentRecord record) {
        return from(record, java.util.List.of());
    }

    static ExperimentRecordResponse from(ExperimentRecord record, java.util.List<ExperimentOrganizationResponses.Topic> topics) {
        return new ExperimentRecordResponse(record.getId(), record.getTitle(), record.getContent(),
                record.getPracticeText(), record.getRecordedOn(), record.isUnderstandingNode(),
                record.isMethodCandidate(), record.getHighlight(), record.getVersion(), record.getCreatedAt(),
                record.getUpdatedAt(), topics);
    }
}
