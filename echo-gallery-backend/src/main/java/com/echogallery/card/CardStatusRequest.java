package com.echogallery.card;

import lombok.Data;

@Data
public class CardStatusRequest {
    /** 星號狀態 */
    private Boolean starStatus;
    /** 封存狀態 */
    private Boolean archivedStatus;
    /** 更新下次回流日期，用於「稍後再看」功能 */
    private int nextIntervalDays;
}
