package com.echogallery.issue;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class CardIssueResponse {
    private Long issueId;
    private String issueTitle;
    private IssueStatus issueStatus;
    private IssueCardStatus status;
    private String note;
    private ZonedDateTime linkedAt;
    private ZonedDateTime usedAt;
}
