package com.echogallery.issue;

import java.time.ZonedDateTime;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class IssueSummaryResponse {
    private Long id;
    private String title;
    private String objective;
    private String description;
    private String currentAssessment;
    private String outcomeCriteria;
    private String externalUrl;
    private IssueStatus status;
    private ZonedDateTime completedAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime latestProgressAt;
    private String latestProgressChangeSummary;
    private String latestProgressAssessment;
    private String latestProgressNextStep;
    private Long candidateCount;
    private Long usedCount;

    public IssueSummaryResponse(
            Long id,
            String title,
            String objective,
            String description,
            String currentAssessment,
            String outcomeCriteria,
            String externalUrl,
            IssueStatus status,
            ZonedDateTime completedAt,
            ZonedDateTime updatedAt,
            Long candidateCount,
            Long usedCount) {
        this.id = id;
        this.title = title;
        this.objective = objective;
        this.description = description;
        this.currentAssessment = currentAssessment;
        this.outcomeCriteria = outcomeCriteria;
        this.externalUrl = externalUrl;
        this.status = status;
        this.completedAt = completedAt;
        this.updatedAt = updatedAt;
        this.candidateCount = candidateCount;
        this.usedCount = usedCount;
    }
}
