package com.echogallery.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRecurrenceRequest {

    @NotNull(message = "回流天數不可為空")
    @Min(value = 1, message = "回流天數至少為 1 天")
    @Max(value = 365, message = "回流天數最多為 365 天")
    private Integer intervalDays;

    /** 今日看板直接調整週期時，同時將本次回流視為稍後再看。 */
    private boolean deferCurrentOccurrence;
}
