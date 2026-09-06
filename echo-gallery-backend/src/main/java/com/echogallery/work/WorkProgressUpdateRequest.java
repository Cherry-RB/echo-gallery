package com.echogallery.work;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkProgressUpdateRequest {

    @Size(max = 50000, message = "最近改變不可超過 50000 個字")
    private String changeSummary;

    @Size(max = 50000, message = "目前看法不可超過 50000 個字")
    private String assessment;

    @Size(max = 50000, message = "下一步不可超過 50000 個字")
    private String nextStep;
}
