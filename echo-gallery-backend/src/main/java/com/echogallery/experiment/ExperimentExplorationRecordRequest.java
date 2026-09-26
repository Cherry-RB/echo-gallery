package com.echogallery.experiment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExperimentExplorationRecordRequest(
        @NotBlank(message = "發現不可留白") @Size(max = 1000, message = "發現不可超過 1000 字") String discovery,
        boolean includeCurrentTry) {
}
