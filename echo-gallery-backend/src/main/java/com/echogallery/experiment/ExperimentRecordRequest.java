package com.echogallery.experiment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 探索紀錄的新增與更新輸入；三個文字欄位的至少一項規則由服務統一驗證。 */
public record ExperimentRecordRequest(
        @Size(max = ExperimentRecordText.TITLE_LIMIT) String title,
        @Size(max = ExperimentRecordText.CONTENT_LIMIT) String content,
        @Size(max = ExperimentRecordText.PRACTICE_LIMIT) String practiceText,
        @NotNull LocalDate recordedOn,
        boolean understandingNode,
        boolean methodCandidate,
        ExperimentRecordDraft.Highlight highlight,
        @Size(max = 50) List<Long> topicIds,
        UUID requestId,
        Long expectedVersion) {

    ExperimentRecordDraft draft() {
        return new ExperimentRecordDraft(new ExperimentRecordText(title, content, practiceText), recordedOn,
                understandingNode, methodCandidate, highlight);
    }
}
