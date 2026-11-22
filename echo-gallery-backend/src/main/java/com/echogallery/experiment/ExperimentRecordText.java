package com.echogallery.experiment;

/** 探索紀錄文字的共用驗證規則；不變更使用者輸入的內容。 */
public record ExperimentRecordText(String title, String content, String practiceText) {

    public static final int TITLE_LIMIT = 255;
    public static final int CONTENT_LIMIT = 500_000;
    public static final int PRACTICE_LIMIT = 2_000;

    public ExperimentRecordText {
        if (isBlank(title) && isBlank(content) && isBlank(practiceText)) {
            throw new IllegalArgumentException("標題、內容或做法至少填寫一項");
        }
        checkLength(title, TITLE_LIMIT, "標題");
        checkLength(content, CONTENT_LIMIT, "內容");
        checkLength(practiceText, PRACTICE_LIMIT, "做法");
    }

    private static boolean isBlank(String text) {
        return text == null || text.codePoints().allMatch(
                codePoint -> Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint));
    }

    private static void checkLength(String text, int limit, String field) {
        if (text != null && text.codePointCount(0, text.length()) > limit) {
            throw new IllegalArgumentException(field + "超過長度限制");
        }
    }
}
