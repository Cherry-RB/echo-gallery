package com.echogallery.card;

import lombok.Data;

@Data
public class CardRequest {
    private Long id;
    private String type;         // "note" 或 "link"
    private String title;
    private String coverImageUrl;
    private String url;
    // private String sourceType;   // "bilibili", "youtube" 等
    private String summary;
    private String content;
    private String reason;
    private String[] tags;
    private Integer intervalDays;
    private Boolean isArchived;
}
