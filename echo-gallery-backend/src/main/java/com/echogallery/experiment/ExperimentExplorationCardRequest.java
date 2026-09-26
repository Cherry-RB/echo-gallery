package com.echogallery.experiment;

import java.util.List;

import com.echogallery.card.CardContentRequest;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 將探索紀錄整理為新卡；這類卡片屬於實驗場，但不建立衍生關聯。
 */
public class ExperimentExplorationCardRequest extends CardContentRequest {

    @NotEmpty(message = "至少選擇一筆探索紀錄")
    @Size(max = 100, message = "一次最多整理 100 筆探索紀錄")
    private List<@Positive Long> recordIds;

    public List<Long> getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(List<Long> recordIds) {
        this.recordIds = recordIds;
    }
}
