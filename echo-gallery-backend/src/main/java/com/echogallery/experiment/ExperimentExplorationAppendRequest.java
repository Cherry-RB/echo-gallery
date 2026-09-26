package com.echogallery.experiment;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ExperimentExplorationAppendRequest(
        @NotEmpty(message = "至少選擇一筆探索紀錄")
        @Size(max = 100, message = "一次最多整理 100 筆探索紀錄")
        List<@Positive Long> recordIds,
        @NotBlank(message = "整理內容不可留白") String content) {
}
