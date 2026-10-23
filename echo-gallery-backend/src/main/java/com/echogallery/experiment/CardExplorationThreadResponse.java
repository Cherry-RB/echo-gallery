package com.echogallery.experiment;

public record CardExplorationThreadResponse(
        Long threadId,
        String threadName,
        Long experimentId,
        String experimentTitle,
        boolean experimentArchived,
        ExplorationThreadStatus status,
        ExplorationThreadCardRole role,
        String note) {
}
