package com.echogallery.card;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

public record TodayNextRequest(
        @NotNull ZonedDateTime currentBatchOfferedAt) {
}
