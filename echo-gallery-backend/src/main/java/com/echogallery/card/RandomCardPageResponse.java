package com.echogallery.card;

import java.util.List;

public record RandomCardPageResponse(
        List<CardSummaryResponse> content,
        Long startId,
        Long cursorId,
        boolean hasMore) {
}
