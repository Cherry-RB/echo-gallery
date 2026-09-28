package com.echogallery.issue;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddIssueCardRequest {

    @NotNull(message = "卡片 ID 不可為空")
    private Long cardId;

    @Size(max = 1000, message = "素材備註不可超過 1000 個字")
    private String note;
}
