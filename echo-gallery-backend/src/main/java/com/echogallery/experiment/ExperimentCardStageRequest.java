package com.echogallery.experiment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExperimentCardStageRequest {

    @NotNull(message = "請選擇土壤")
    private ExperimentStage stage;
}
