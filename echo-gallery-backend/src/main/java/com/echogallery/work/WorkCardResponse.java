package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class WorkCardResponse {
    private Long id;
    private Long workId;
    private Long cardId;
    private WorkCardStatus status;
    private String note;
    private ZonedDateTime linkedAt;
    private ZonedDateTime usedAt;
}
