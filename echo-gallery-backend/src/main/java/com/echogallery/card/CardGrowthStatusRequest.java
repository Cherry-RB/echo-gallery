package com.echogallery.card;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CardGrowthStatusRequest {

    @NotNull(message = "成長狀態不可為空")
    private CardGrowthStatus growthStatus;
}
