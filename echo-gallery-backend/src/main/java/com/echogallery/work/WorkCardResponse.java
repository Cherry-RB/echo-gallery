package com.echogallery.work;

import java.time.ZonedDateTime;
import java.util.List;

import com.echogallery.card.CardGrowthStatus;

import lombok.Data;

@Data
public class WorkCardResponse {
    private Long id;
    private Long workId;
    private Long cardId;
    private String cardTitle;
    private String cardType;
    private CardGrowthStatus cardGrowthStatus;
    private List<String> tags;
    private WorkCardStatus status;
    private String note;
    private ZonedDateTime linkedAt;
    private ZonedDateTime usedAt;
}
