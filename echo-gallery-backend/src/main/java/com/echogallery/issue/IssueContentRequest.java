package com.echogallery.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class IssueContentRequest {

    @NotBlank(message = "議題名稱不可為空")
    @Size(max = 255, message = "議題名稱不可超過 255 個字")
    private String title;

    @Size(max = 50000, message = "本輪系統問題不可超過 50000 個字")
    private String objective;

    @Size(max = 50000, message = "背景與脈絡不可超過 50000 個字")
    private String description;

    @Size(max = 50000, message = "補充研判不可超過 50000 個字")
    private String currentAssessment;

    @Size(max = 50000, message = "關鍵狀態不可超過 50000 個字")
    private String keyStates;

    @Size(max = 50000, message = "主導迴路不可超過 50000 個字")
    private String dominantLoops;

    @Size(max = 50000, message = "主要瓶頸不可超過 50000 個字")
    private String primaryConstraint;

    @Size(max = 50000, message = "當前槓桿點不可超過 50000 個字")
    private String leveragePoint;

    @Size(max = 50000, message = "待觀察訊號不可超過 50000 個字")
    private String watchSignals;

    @Size(max = 50000, message = "暫不介入或延遲提醒不可超過 50000 個字")
    private String nonInterventionNote;

    @Size(max = 50000, message = "收斂或重議條件不可超過 50000 個字")
    private String outcomeCriteria;

    @Size(max = 2048, message = "外部連結不可超過 2048 個字")
    @ValidOptionalHttpUrl
    private String externalUrl;
}
