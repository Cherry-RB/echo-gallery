package com.echogallery.card;

public enum BoardType {
    TODAY,
    ALL,
    HOT,
    RANDOM,
    ARCHIVED,
    SNOOZED;

    public static BoardType from(String value) {
        if (value == null || value.isBlank()) {
            return TODAY;
        }

        return switch (value.toLowerCase()) {
            case "today" -> TODAY;
            case "all" -> ALL;
            case "hot" -> HOT;
            case "random" -> RANDOM;
            case "archived" -> ARCHIVED;
            case "snoozed" -> SNOOZED;
            default -> throw new IllegalArgumentException("Unsupported boardType: " + value);
        };
    }
}
