package com.echogallery.issue;

import java.time.ZonedDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueUpdateResponse {

    private Long id;
    private Long issueId;
    private String issueTitle;
    private String changeSummary;
    private String assessment;
    private String nextStep;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
