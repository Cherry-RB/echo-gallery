package com.echogallery.experiment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 從收藏卡片建立探索紀錄時，確保紀錄與卡片參考在同一交易中完成。 */
public record ExperimentRecordFromCardRequest(
        @NotNull @Valid ExperimentRecordRequest record,
        @NotNull @Positive Long cardId) {
}
