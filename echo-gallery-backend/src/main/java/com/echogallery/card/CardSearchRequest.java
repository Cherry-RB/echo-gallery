package com.echogallery.card;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CardSearchRequest {

    @Positive(message = "Card ID 必須大於 0")
    private Long id;

    @Size(max = 255, message = "卡片標題不可超過 255 個字元")
    private String title;

    @Valid
    @Size(max = 50, message = "一次最多查詢 50 個標籤")
    private List<@Positive(message = "標籤 ID 必須大於 0") Long> tagIds = new ArrayList<>();

    private CardSearchTagMode tagMode = CardSearchTagMode.OR;

    @Size(max = 4, message = "成長狀態不可超過 4 種")
    private List<CardGrowthStatus> growthStatuses = new ArrayList<>();

    private CardSearchArchiveStatus archiveStatus = CardSearchArchiveStatus.ACTIVE;

    private CardSearchSortBy sortBy = CardSearchSortBy.UPDATED_AT;

    private CardSearchDirection direction = CardSearchDirection.DESC;

    @Min(value = 0, message = "頁碼不可小於 0")
    private int page = 0;

    @Min(value = 1, message = "每頁筆數至少為 1")
    @Max(value = 100, message = "每頁筆數不可超過 100")
    private int size = 20;
}
