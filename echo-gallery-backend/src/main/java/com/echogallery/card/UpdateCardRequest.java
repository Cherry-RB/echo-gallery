package com.echogallery.card;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCardRequest extends CardContentRequest {

    @NotNull(message = "封存狀態不可為空")
    private Boolean isArchived;
}
