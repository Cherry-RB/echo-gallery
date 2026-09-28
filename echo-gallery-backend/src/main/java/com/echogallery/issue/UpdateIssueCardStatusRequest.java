package com.echogallery.issue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateIssueCardStatusRequest {

    @NotNull(message = "作品素材狀態不可為空")
    private IssueCardStatus status;
}
