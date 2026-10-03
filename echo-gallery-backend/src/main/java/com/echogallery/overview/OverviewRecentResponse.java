package com.echogallery.overview;

import java.time.ZonedDateTime;

public record OverviewRecentResponse(
        int periodDays,
        ZonedDateTime periodStartAt,
        ZonedDateTime periodEndAt,
        OverviewResponse.OverviewPeriodResponse period) {
}
