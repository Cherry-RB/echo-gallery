package com.echogallery.work;

import java.time.ZonedDateTime;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkProgressUpdateResponse {

    private Long id;
    private Long workId;
    private String workTitle;
    private String changeSummary;
    private String assessment;
    private String nextStep;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
