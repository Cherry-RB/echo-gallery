package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkSummaryResponse {
    private Long id;
    private String title;
    private WorkStatus status;
    private ZonedDateTime completedAt;
    private ZonedDateTime updatedAt;
    private Long candidateCount;
    private Long usedCount;
}
