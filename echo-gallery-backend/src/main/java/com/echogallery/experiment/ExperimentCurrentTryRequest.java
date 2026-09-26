package com.echogallery.experiment;

import jakarta.validation.constraints.Size;

public record ExperimentCurrentTryRequest(
        @Size(max = 500, message = "試法不可超過 500 字") String currentTry) {
}
