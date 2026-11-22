package com.echogallery.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ExperimentRecordDraftTests {

    @Test
    void bothPurposesMayBeSelectedWithoutPracticeText() {
        var draft = new ExperimentRecordDraft(
                new ExperimentRecordText(null, "讀到值得嘗試的點子", null),
                LocalDate.of(2026, 1, 2), true, true, null);

        assertTrue(draft.understandingNode());
        assertTrue(draft.methodCandidate());
        assertEquals(ExperimentRecordDraft.Highlight.GENERAL, draft.highlight());
    }

    @Test
    void titleOnlyCanBeAnUnderstandingNodeAndHaveAHighlight() {
        var draft = new ExperimentRecordDraft(
                new ExperimentRecordText("一個疑問", null, null), LocalDate.of(2026, 1, 2),
                true, false, ExperimentRecordDraft.Highlight.PEAK_EXPERIENCE);

        assertEquals(ExperimentRecordDraft.Highlight.PEAK_EXPERIENCE, draft.highlight());
    }

    @Test
    void requiresDateAndText() {
        var text = new ExperimentRecordText("標題", null, null);
        assertThrows(NullPointerException.class,
                () -> new ExperimentRecordDraft(text, null, false, false, null));
        assertThrows(NullPointerException.class,
                () -> new ExperimentRecordDraft(null, LocalDate.of(2026, 1, 2), false, false, null));
    }
}
