package com.echogallery.issue;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IssueUpdateRequest {

    @Size(max = 50000, message = "新訊號不可超過 50000 個字")
    private String changeSummary;

    @Size(max = 50000, message = "模型更新不可超過 50000 個字")
    private String assessment;

    @Size(max = 50000, message = "介入或等待不可超過 50000 個字")
    private String nextStep;
}
