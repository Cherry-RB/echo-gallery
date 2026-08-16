package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class WorkSummaryResponse {
    private Long id;
    private String title;
    private WorkStatus status;
    private ZonedDateTime completedAt;
    private ZonedDateTime updatedAt;
}
