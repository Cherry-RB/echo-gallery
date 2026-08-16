package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.Data;

@Data
public class CardWorkResponse {
    private Long workId;
    private String workTitle;
    private WorkStatus workStatus;
    private WorkCardStatus status;
    private String note;
    private ZonedDateTime linkedAt;
    private ZonedDateTime usedAt;
}
