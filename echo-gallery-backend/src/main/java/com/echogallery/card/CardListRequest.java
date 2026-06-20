package com.echogallery.card;

import lombok.Data;

@Data
public class CardListRequest {
    // private String type;         // "note" 或 "link"
    // private String title;
    // private String url;
    // private String sourceType;   // "bilibili", "youtube" 等
    // private String summary;
    // private String content;
    // private String reason;
    // private String[] tagNames;
    private Integer pageNumber;
    private Integer pageSize;
}