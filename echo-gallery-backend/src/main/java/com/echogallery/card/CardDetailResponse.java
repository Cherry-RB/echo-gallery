package com.echogallery.card;

import lombok.Data;
import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class CardDetailResponse {
    private Long id;
    private String type;
    private String title;
    private List<String> tags;
    private String coverImageUrl;
    private String reason;
    private String summary;
    private String content;
    private String url;
    private Integer intervalDays;
    private ZonedDateTime nextShowAt;
    private Integer openCount;
    private ZonedDateTime likeAvailableAt;
    private ZonedDateTime lastLikedAt;
    private Integer likeCount;
    @JsonProperty("isArchived")
    private Boolean isArchived;
    private ZonedDateTime lastOpenAt;
    private ZonedDateTime lastInteractionAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private String sourceType;
}