package com.echogallery.experiment;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ExperimentOrganizationRequests {
    private ExperimentOrganizationRequests() {}
    public record Topic(@NotBlank @Size(max = 100) String name) {}
    public record Pin(@NotNull Integer slot) {}
    public record TopicOrder(@NotNull @Size(max = 50) List<Long> topicIds) {}
    public record RecordTopics(@NotNull @Size(max = 50) List<Long> topicIds) {}
    public record Practice(@NotBlank @Size(max = 10000) String content, Integer slot) {}
}
