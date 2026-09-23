package com.echogallery.experiment;

import java.util.List;

import com.echogallery.card.CardContentRequest;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExperimentGrowRequest extends CardContentRequest {

    @NotEmpty(message = "至少選擇一張來源卡片")
    @Size(max = 20, message = "一次最多可從 20 張卡片長出新卡")
    private List<@Positive(message = "來源卡片 ID 必須大於 0") Long> sourceCardIds;

    @NotNull(message = "請選擇新卡要放入的土壤")
    private ExperimentStage stage;

    @Size(max = 1000, message = "種植備註不可超過 1000 字")
    private String note;
}
