package com.echogallery.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ResumeCardRequest {

    @NotNull(message = "回流間隔不可為空")
    @Min(value = 1, message = "回流間隔至少為 1 天")
    @Max(value = 365, message = "回流間隔最多為 365 天")
    private Integer intervalDays;
}
