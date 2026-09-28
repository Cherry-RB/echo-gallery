package com.echogallery.issue;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateIssueRequest extends IssueContentRequest {

    @NotNull(message = "議題狀態不可為空")
    private IssueStatus status;
}
