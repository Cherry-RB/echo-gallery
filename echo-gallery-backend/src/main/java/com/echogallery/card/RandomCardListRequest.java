package com.echogallery.card;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record RandomCardListRequest(
        @Min(value = 1, message = "每頁至少顯示 1 張卡片")
        @Max(value = 100, message = "每頁最多顯示 100 張卡片")
        Integer pageSize,
        @Min(value = 1, message = "隨機起點格式錯誤") Long startId,
        @Min(value = 1, message = "游標格式錯誤") Long cursorId) {
}
