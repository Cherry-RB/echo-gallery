package com.echogallery.experiment;

import java.time.ZonedDateTime;

public final class ExperimentOrganizationResponses {
    private ExperimentOrganizationResponses() {}
    public record Topic(Long id, String name, Integer pinnedSlot, ZonedDateTime createdAt) {
        static Topic from(ExperimentTopic topic) { return new Topic(topic.getId(), topic.getName(), topic.getPinnedSlot(), topic.getCreatedAt()); }
    }
    public record Practice(Long id, String content, int slot, ZonedDateTime createdAt) {
        static Practice from(ExperimentPinnedPractice practice) { return new Practice(practice.getId(), practice.getContent(), practice.getSlot(), practice.getCreatedAt()); }
    }
}
