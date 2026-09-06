package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class WorkDetailResponse {
    private Long id;
    private String title;
    private String objective;
    private String description;
    private String currentAssessment;
    private String outcomeCriteria;
    private WorkStatus status;
    private String externalUrl;
    private ZonedDateTime completedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
