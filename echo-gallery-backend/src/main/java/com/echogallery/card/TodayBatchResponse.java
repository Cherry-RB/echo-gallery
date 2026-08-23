package com.echogallery.card;

import java.time.ZonedDateTime;
import java.util.List;

public record TodayBatchResponse(
        List<CardSummaryResponse> cards,
        ZonedDateTime batchOfferedAt) {
}
