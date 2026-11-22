package com.echogallery.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ExperimentRecordTextTests {

    @Test
    void acceptsAnyOneTextFieldAndPreservesItsOriginalText() {
        assertEquals("  標題\n", new ExperimentRecordText("  標題\n", null, null).title());
        assertEquals("內容\n第二行", new ExperimentRecordText(null, "內容\n第二行", null).content());
        assertEquals("先畫五分鐘", new ExperimentRecordText(null, null, "先畫五分鐘").practiceText());
    }

    @Test
    void rejectsMissingOrUnicodeWhitespaceOnlyText() {
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRecordText(null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRecordText("\n\t", "\u3000", "\u00a0"));
    }

    @Test
    void countsUnicodeCodePointsWithoutSilentlyTruncating() {
        String title = "🎨".repeat(ExperimentRecordText.TITLE_LIMIT);
        assertEquals(title, new ExperimentRecordText(title, null, null).title());
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRecordText(title + "畫", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentRecordText(null, null, "畫".repeat(ExperimentRecordText.PRACTICE_LIMIT + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ExperimentRecordText(null, "畫".repeat(ExperimentRecordText.CONTENT_LIMIT + 1), null));
    }
}
