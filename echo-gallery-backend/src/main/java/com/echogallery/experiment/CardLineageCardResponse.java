package com.echogallery.experiment;

public record CardLineageCardResponse(
        Long cardId,
        String cardTitle,
        String cardType,
        boolean cardArchived) {
}
