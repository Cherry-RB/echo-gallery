package com.echogallery.experiment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ExperimentCardRequest {

    @NotNull(message = "請選擇要種入的卡片")
    @Positive(message = "卡片 ID 必須大於 0")
    private Long cardId;

    @NotNull(message = "請選擇土壤")
    private ExperimentStage stage;

    @Size(max = 1000, message = "種植備註不可超過 1000 字")
    private String note;
}
