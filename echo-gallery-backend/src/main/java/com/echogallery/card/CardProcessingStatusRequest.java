package com.echogallery.card;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CardProcessingStatusRequest {

    @NotNull(message = "請指定是否待整理")
    private Boolean needsProcessing;
}
