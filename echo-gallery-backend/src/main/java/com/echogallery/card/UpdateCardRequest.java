package com.echogallery.card;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCardRequest extends CardContentRequest {
    @NotNull(message = "請重新載入卡片後再編輯")
    private Long expectedContentVersion;

    @NotNull(message = "封存狀態不可為空")
    private Boolean isArchived;

}
