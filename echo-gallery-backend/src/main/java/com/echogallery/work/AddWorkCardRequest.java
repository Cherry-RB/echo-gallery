package com.echogallery.work;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddWorkCardRequest {

    @NotNull(message = "卡片 ID 不可為空")
    private Long cardId;

    private String note;
}
