package com.echogallery.experiment;

import java.time.ZonedDateTime;
import java.util.List;

/** 實驗詳情列表使用的輕量探索線資料；完整內容只在開啟詳情時讀取。 */
public record ExplorationThreadSummaryResponse(
        Long id, Long experimentId, String name, ExplorationThreadStatus status, String conclusion,
        int sortOrder, ZonedDateTime lastActivityAt,
        long materialCount, long methodCount, long recordCount, long resultCardCount, long workCount,
        String firstMethodContent,
        List<MaterialPreview> recentMaterials, List<RecordPreview> recentRecords, long version, String systemKey, long orderVersion) {
    public record MaterialPreview(Long cardId, String title, String note) {}
    public record RecordPreview(Long id, String methodContent, String discovery,
            ZonedDateTime occurredAt, ZonedDateTime createdAt) {}
}
