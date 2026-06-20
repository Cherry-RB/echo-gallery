package com.echogallery.card;

import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class CardSummaryResponse {
    private Long id;
    private String type;
    private String title;
    private String reason;
    private String summary;
    private List<String> tags;
    private ZonedDateTime likeAvailableAt;
    private Integer likeCount;
    private String url;
    private ZonedDateTime nextShowAt;
    private ZonedDateTime createdAt;
    @JsonProperty("isArchived")
    private Boolean isArchived;
    private Integer intervalDays;
}