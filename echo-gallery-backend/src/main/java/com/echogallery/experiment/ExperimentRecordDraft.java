package com.echogallery.experiment;

import java.time.LocalDate;
import java.util.Objects;

/** 新探索紀錄的核心內容，日期與用途標記各自獨立。 */
public record ExperimentRecordDraft(
        ExperimentRecordText text,
        LocalDate recordedOn,
        boolean understandingNode,
        boolean methodCandidate,
        Highlight highlight) {

    public enum Highlight {
        GENERAL,
        GAIN,
        CHALLENGE_OVERCOME,
        PEAK_EXPERIENCE
    }

    public ExperimentRecordDraft {
        Objects.requireNonNull(text, "紀錄文字不可為空");
        Objects.requireNonNull(recordedOn, "紀錄日期不可為空");
        highlight = highlight == null ? Highlight.GENERAL : highlight;
    }
}
