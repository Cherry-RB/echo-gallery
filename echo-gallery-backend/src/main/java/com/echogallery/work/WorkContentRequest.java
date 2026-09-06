package com.echogallery.work;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class WorkContentRequest {

    @NotBlank(message = "議題名稱不可為空")
    @Size(max = 255, message = "議題名稱不可超過 255 個字")
    private String title;

    @Size(max = 50000, message = "本輪焦點不可超過 50000 個字")
    private String objective;

    @Size(max = 50000, message = "背景與脈絡不可超過 50000 個字")
    private String description;

    @Size(max = 50000, message = "整體研判不可超過 50000 個字")
    private String currentAssessment;

    @Size(max = 50000, message = "結案或重議條件不可超過 50000 個字")
    private String outcomeCriteria;

    @Size(max = 2048, message = "外部連結不可超過 2048 個字")
    @ValidOptionalHttpUrl
    private String externalUrl;
}
