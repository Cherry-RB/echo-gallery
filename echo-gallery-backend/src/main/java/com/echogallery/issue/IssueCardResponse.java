package com.echogallery.issue;

import java.time.ZonedDateTime;
import java.util.List;

import lombok.Data;

@Data
public class IssueCardResponse {
    private Long id;
    private Long issueId;
    private Long cardId;
    private String cardTitle;
    private String cardType;
    private List<String> tags;
    private IssueCardStatus status;
    private String note;
    private ZonedDateTime linkedAt;
    private ZonedDateTime usedAt;
}
