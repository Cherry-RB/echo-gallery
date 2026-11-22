package com.echogallery.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ExperimentRecordTests {
    @Test
    void editingOneRecordDoesNotChangeAnotherRecordsPracticeOrRequestIdentity() {
        var experiment = Experiment.builder().title("畫畫").build();
        var date = LocalDate.of(2026, 1, 2);
        var firstId = UUID.randomUUID();
        var original = new ExperimentRecordDraft(new ExperimentRecordText(null, "第一次", "先畫五分鐘"),
                date, false, true, null);
        var first = new ExperimentRecord(experiment, firstId, "a".repeat(64), original);
        var second = new ExperimentRecord(experiment, UUID.randomUUID(), "b".repeat(64), original);

        first.apply(new ExperimentRecordDraft(new ExperimentRecordText(null, "有新的理解", "先畫十分鐘"),
                date, true, true, ExperimentRecordDraft.Highlight.GAIN));

        assertEquals("先畫十分鐘", first.getPracticeText());
        assertEquals("先畫五分鐘", second.getPracticeText());
        assertEquals(firstId, first.getCreateRequestId());
        assertEquals("a".repeat(64), first.getCreatePayloadHash());
        assertTrue(first.isUnderstandingNode());
        assertTrue(first.isMethodCandidate());
        assertFalse(second.isUnderstandingNode());
        assertEquals(date, first.getRecordedOn());
    }
}
