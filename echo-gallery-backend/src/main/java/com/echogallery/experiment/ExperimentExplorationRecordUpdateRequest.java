package com.echogallery.experiment;

import jakarta.validation.constraints.Size;

public record ExperimentExplorationRecordUpdateRequest(
        @Size(max = 500, message = "試法不可超過 500 字") String tryText,
        @Size(max = 1000, message = "發現不可超過 1000 字") String discovery) {
}
