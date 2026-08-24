export type CardType = "note" | "link";

export type CardGrowthStatus = "UNMARKED" | "SEED" | "GROWING" | "MATURE";

export type CardSearchTagMode = "AND" | "OR";
export type CardSearchArchiveStatus = "ACTIVE" | "ARCHIVED" | "ALL";
export type CardSearchSortBy = "UPDATED_AT" | "CREATED_AT" | "NEXT_SHOW_AT" | "ID";
export type CardSearchDirection = "ASC" | "DESC";

export interface CardSearchParams {
    id?: number;
    title?: string;
    tagIds?: number[];
    tagMode?: CardSearchTagMode;
    growthStatuses?: CardGrowthStatus[];
    archiveStatus?: CardSearchArchiveStatus;
    sortBy?: CardSearchSortBy;
    direction?: CardSearchDirection;
    page?: number;
    size?: number;
}

export interface PageResponse<T> {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
}

export interface TodayBatchResponse {
    cards: CardDto[];
    batchOfferedAt: string | null;
}

export type ViewMode = "text" | "gallery";

export type SourceType =
  | "bilibili"
  | "youtube"
  | "x"
  | "facebook"
  | "article"
  | "other";

export interface CardContentRequest {
    type: CardType;
    title: string;
    url?: string;
    summary?: string;
    content?: string;
    reason?: string;
    coverImageUrl?: string;
    tags: string[];
    intervalDays: number | null;
}

export type CreateCardRequest = CardContentRequest;

export interface UpdateCardRequest extends CardContentRequest {
    isArchived: boolean;
    growthStatus?: CardGrowthStatus;
}

export interface CardDto {
    id: string;

    // 基本資訊
    type: CardType;
    title: string;

    // 連結型卡片使用
    url?: string;
    sourceType?: SourceType;

    // 卡片摘要，用於瀑布流
    summary?: string;
    // 詳細內容，用於詳情頁
    content?: string;
    // 使用者為什麼收藏這張卡
    reason?: string;
    
    // 瀑布流顯示開關
    showContentPreview: boolean;

    // 視覺輔助
    coverImageUrl?: string;

    // 分類
    tags: string[];

    // 回流設定
    intervalDays: number | null; // 卡片出現頻率(天)
    nextShowAt: string | null; // 下一次出現的日期
    
    // 使用者互動 (詳情/link)
    lastOpenAt? : string | null; // 上一次被使用者點開詳情 / link 的日期
    lastOfferedAt?: string | null;
    openCount: number;
    snoozeCount?: number;

    // 使用者互動 (星標/熱度)
    lastLikedAt?: string; // 上一次按愛心的日期
    likeCount: number; // 累積被按愛心數量
    likeAvailableAt?: string; // 下一次可以再按愛心的日期

    // 最後一次任何互動
    lastInteractionAt?: string; // lastOpenAt/lastLikedAt

    // 狀態 (是否封存)
    isArchived: boolean;

    growthStatus: CardGrowthStatus;

    isShowContentPreview?: boolean| null;

    // 時間
    createdAt: string;
    updatedAt: string;
}
