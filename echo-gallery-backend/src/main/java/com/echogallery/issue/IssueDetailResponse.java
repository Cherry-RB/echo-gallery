package com.echogallery.issue;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class IssueDetailResponse {
    private Long id;
    private String title;
    private String objective;
    private String description;
    private String currentAssessment;
    private String outcomeCriteria;
    private IssueStatus status;
    private String externalUrl;
    private ZonedDateTime completedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
