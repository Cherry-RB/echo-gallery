# 卡片成長、查詢與限量回流任務追蹤 Memo

> 建立日期：2026.08.23
>
> 文件性質：本次改動的暫時任務追蹤文件，不加入長期文件索引。
>
> 完成處理：功能上線並驗證穩定後，刪除本文件；長期有效的產品決策以 `docs/ECHO_GALLERY_MVP.md` 為準。

## 一、目標

本次改動要解決兩種實際使用壓力：

1. 每張 Card 預設為 `SEED`，容易讓成長狀態變成必須逐張整理的待辦。
2. Today 顯示全部到期 Card，存量增加後容易形成無法清空的債務感。

同時補上卡片綜合查詢，讓使用者可以主動找回想整理的內容，不必完全依賴回流機制。

## 二、已確認原則

- 新建 Card 預設為 `UNMARKED`；`SEED` 改為使用者主動標記的「待補充」狀態。
- 既有 `SEED` 一次轉為 `UNMARKED`；既有 `GROWING / MATURE` 保持不變。
- Card 被選入 Today 批次只是被提供，不算完成回顧。
- 選入批次只更新 `lastOfferedAt`，不更新 `nextShowAt`。
- 只有從 Today 進入 Card Detail，才推進 `nextShowAt` 並更新回顧紀錄。
- 每批最多 5 張；重新整理維持目前批次，不自動補滿。
- 使用者可主動選擇「今天想多看一批」；同一 Card 同一天最多被提供一次。
- 下一批沒有候選時不建立空批次、不清空目前畫面；保留目前 Card 並提示「今天沒有更多新卡片了」。
- 不顯示全部到期數量或今日剩餘數量。
- 查詢與其他看板進入詳情不算 Today 回顧。
- 稍後再看必須同時延後 `nextShowAt` 並累加 `snoozeCount`。
- Card 回流與日界線計算使用注入的 `Clock`，時區以 Asia/Taipei 為準。

## 三、資料與 API 變更總覽

### 資料模型

- `CardGrowthStatus` 新增 `UNMARKED`。
- `Card.growthStatus` Java 預設值改為 `UNMARKED`。
- `Card` 新增 nullable `lastOfferedAt`。
- PostgreSQL `growth_status` 允許值 / constraint、預設值、既有資料與候選查詢索引同步調整。
- migration SQL 必須可重複安全執行，並先在本地資料庫驗證，再套用正式環境。

### API contract

```text
PUT  /api/cards/{id}/growth-status       新增：只更新成長狀態
GET  /api/cards/search                   調整：複合篩選及分頁回應
POST /api/cards/today/prepare            新增：取得或建立今日目前批次
POST /api/cards/today/next               新增：主動取得下一批
PUT  /api/cards/{id}/read                調整：同一次提供只完成一次回顧
PUT  /api/cards/{id}/snooze              修正：累加 snoozeCount
GET  /api/sidebar/stats                  調整：移除 todayEchoCards
```

現有 Today 前端改用專用批次 API。通用 `/api/cards/list` 暫時保留給其他看板，避免本次擴大重構。

## 四、任務進度

- [ ] 任務 1：成長狀態語意與資料遷移
- [ ] 任務 2：卡片綜合查詢垂直切片
- [ ] 任務 3：Today 限量回流後端與可測試時間
- [ ] 任務 4：Today 前端與右側欄契約調整
- [ ] 任務 5：稍後再看修正、整合驗證與上線準備

建議依序完成。任務 1 與任務 2 可先獨立使用；任務 3 完成 API 後再進行任務 4；任務 5 作為整體收尾。

---

## 任務 1：成長狀態語意與資料遷移

### 目標

讓未標記成為正常狀態，並讓 `SEED` 只代表使用者主動留下的待補充提示。

### 實作項目

#### 後端

- [ ] `CardGrowthStatus` 新增 `UNMARKED`。
- [ ] `Card.growthStatus` 預設值由 `SEED` 改為 `UNMARKED`。
- [ ] 檢查 Create / Update request、detail / summary response 與 mapper，確保 `UNMARKED` 可完整往返。
- [ ] 新增只更新成長狀態的 request DTO 與 service 方法。
- [ ] 新增 `PUT /api/cards/{id}/growth-status`，包含登入使用者 ownership 檢查。
- [ ] 不讓 growth status mutation 更新 `nextShowAt` 或回顧欄位。

#### 資料庫

- [ ] 編寫可重複安全執行的 PostgreSQL migration SQL。
- [ ] 先允許 `UNMARKED`，再將既有 `SEED` 全部轉為 `UNMARKED`。
- [ ] 保留 `GROWING / MATURE`。
- [ ] 確認資料庫預設值與 Java 預設值一致。
- [ ] migration 完成後檢查各狀態筆數。

#### 前端

- [ ] `CardGrowthStatus` 型別加入 `UNMARKED`。
- [ ] 新建 Card 預設為 `UNMARKED`。
- [ ] Card Detail 可顯示與切換四種狀態。
- [ ] 看板維持條件顯示：`UNMARKED` 不產生標記；其餘狀態依既有設計顯示。
- [ ] Today Card 提供「標記種子」快捷操作，成功後同步詳情、列表與相關統計快取。
- [ ] 檢查 Work 素材選擇器及所有 growth status map，不得以 `SEED` 作為缺值 fallback。

### 驗收條件

- [ ] 新建 Card 的狀態為 `UNMARKED`。
- [ ] 舊資料不再因原預設值全部顯示成種子。
- [ ] 四種狀態可由 API 與畫面正確往返。
- [ ] 快速標記 `SEED` 不改變 `nextShowAt`、`openCount` 或 `lastOpenAt`。
- [ ] 非擁有者不能修改 Card growth status。

### 建議驗證

- [ ] `CardGrowthStatusPersistenceTests` 補上四種 enum 與預設狀態。
- [ ] controller / integration test 覆蓋 ownership、非法狀態及只更新目標欄位。
- [ ] 前端 build 與 Card Create、Card Detail、Today、Work 素材畫面手動驗證。

---

## 任務 2：卡片綜合查詢垂直切片

### 目標

提供足夠的主動找回能力，尤其可用 `SEED`、Card ID、標題或標籤找出想繼續整理的內容。

### 實作項目

#### 後端

- [ ] 定義 search request 參數：`id`、`title`、`tagIds`、`tagMode`、`growthStatuses`、`sortBy`、`direction`、`page`、`size`。
- [ ] ID 使用精確比對；標題使用不分大小寫模糊查詢。
- [ ] 標籤支援 AND / OR；AND 查詢不可因 join 重複造成錯誤筆數。
- [ ] 成長狀態支援多選。
- [ ] 排序白名單限定為 `UPDATED_AT / CREATED_AT / NEXT_SHOW_AT / ID`，並支援 ASC / DESC。
- [ ] 所有條件都必須套用登入使用者 ownership；預設排除封存 Card。
- [ ] response 改為包含 `content`、分頁資訊與 `totalElements` 的頁面物件。
- [ ] 確認查詢沒有 N+1；summary DTO 只取得列表所需欄位與標籤。

#### 前端

- [ ] 更新 `cardApi.searchCards` request / response 型別。
- [ ] 重整 `SearchView`：ID、標題、標籤、成長狀態、排序與方向。
- [ ] 篩選條件改變時回到第一頁。
- [ ] 使用 TanStack Query 將查詢條件納入 query key，避免不同條件共用錯誤快取。
- [ ] 支援載入、空結果、失敗、分頁與總筆數顯示。
- [ ] 查詢結果前往 Card Detail 時，不帶 Today review 來源。
- [ ] 同步調整 `WorkMaterialManager` 對既有 search API 的使用；可保留輕量 ID / 標題介面。

### 驗收條件

- [ ] 各條件可獨立與組合使用。
- [ ] 多標籤 AND / OR 結果、總筆數及分頁皆正確。
- [ ] 相同排序值時有穩定次排序，不因翻頁造成重複或漏項。
- [ ] 查詢他人 Card ID 不會洩漏存在性或內容。
- [ ] 從查詢結果進入詳情不更新回流欄位。

### 建議驗證

- [ ] 擴充 `CardSearchIntegrationTests`：ownership、模糊標題、多標籤 AND / OR、狀態多選、排序、分頁與空值。
- [ ] 檢查實際 SQL 或 Hibernate statistics，確認列表沒有逐卡查標籤的 N+1。
- [ ] 前端 build，並手動測試桌面與窄螢幕篩選區。

---

## 任務 3：Today 限量回流後端與可測試時間

### 目標

建立每批最多 5 張、同日不重複、曝光不算回顧的後端模型，並讓跨日行為可穩定測試。

### 實作項目

#### 時間與資料模型

- [ ] Card 新增 nullable `lastOfferedAt`，加入 Card Detail response 供詳情頁唯讀顯示，不加入一般 Card summary。
- [ ] 編寫可重複安全執行的 PostgreSQL migration SQL及候選查詢索引。
- [ ] 提供 Asia/Taipei 的 `Clock` bean，CardService 不再直接呼叫系統現在時間。
- [ ] 統一由 Clock 計算今日零時、明日零時與目前時間。

#### 批次 API

- [ ] 定義 Today batch response，包含 `cards` 與目前批次識別 `batchOfferedAt`；不加入 `hasMore` 或剩餘筆數。
- [ ] 實作 `POST /api/cards/today/prepare`：有目前批次就原樣回傳，沒有才建立第一批。
- [ ] 實作 `POST /api/cards/today/next`：驗證前端帶入的目前批次識別後，建立下一批。
- [ ] 目前批次使用當日最大的 `lastOfferedAt` 辨識；先確認批次存在，再回傳其中仍到期且未封存的 Card。已回顧或封存的 Card 不顯示，也不觸發自動補卡。
- [ ] 即使目前批次的 Card 已全部回顧或封存，prepare 仍回傳該批次識別及空的 `cards`，不得自動建立下一批。
- [ ] prepare / next 均限制目前登入使用者，單批最多 5 張。
- [ ] 同一批 Card 寫入同一個 `lastOfferedAt`。
- [ ] 新批次時間正規化至 PostgreSQL 可保存的精度，且必須晚於目前批次時間，避免固定 Clock 或極短間隔請求合併成同一批。
- [ ] 候選限定未封存、已到期且今天尚未提供過。
- [ ] 候選優先使用 `lastOfferedAt` 為 null / 最舊者，再以穩定欄位排序。
- [ ] 使用 transaction 並鎖定目前 User 資料列，再檢查與建立批次，避免同一使用者的重複請求產生重疊批次。
- [ ] next 的批次識別不符時回傳明確衝突，不默默再跳一批。
- [ ] next 沒有候選時回傳原本的 `batchOfferedAt` 與空的 `cards`，不更新任何 Card，也不建立 User 今日完成／已耗盡狀態。
- [ ] 批次中的 Card 被封存時可從畫面隱藏，但不因此自動補入新 Card。

#### 回顧行為

- [ ] 保留只有 Today 來源才呼叫 read API 的前端契約。
- [ ] read 依注入 Clock 推進 `nextShowAt`，更新 `lastOpenAt / openCount / lastInteractionAt` 並清除 `snoozeCount`。
- [ ] read 鎖定 Card，並以 `lastOpenAt >= lastOfferedAt` 判斷同一次提供已完成回顧；重複請求直接回傳目前狀態，不再次推進或計數。
- [ ] prepare / next 不得改變上述回顧欄位。

### 驗收條件

- [ ] 同一天重複 prepare 取得相同批次。
- [ ] next 取得新的最多 5 張，且不包含今天已提供過的 Card。
- [ ] 連點 next 不會跳過兩批；過期 request 不會覆蓋目前批次。
- [ ] Card 只出現在批次時，`nextShowAt` 與回顧紀錄不變。
- [ ] 同批只開 2 張時，只有該 2 張推進 `nextShowAt`；其餘 Card 隔日仍可參與輪替。
- [ ] 重新整理目前批次時，已回顧或封存的 Card 消失，其餘 Card 保留，且不自動補滿。
- [ ] 同一批 Card 重複開啟或同時送出 read，只完成一次回顧。
- [ ] 使用者多日未登入不會預先建立或累積批次。
- [ ] next 沒有候選時穩定回傳空的 `cards`；前端保留目前批次，重新整理後仍恢復該批次中未回顧的 Card。

### 建議驗證

- [ ] 固定 Clock 測試 Asia/Taipei 23:59、00:00、同日與跨日。
- [ ] integration test 覆蓋 prepare 冪等、next、同日去重、跨日再資格、ownership 與空結果。
- [ ] concurrency test 覆蓋 prepare / next 的重複請求。
- [ ] 測試 offer 與 read 對各 Card 欄位的差異。

---

## 任務 4：Today 前端與右側欄契約調整

### 目標

把 Today 從無限到期瀑布流改為明確的有限批次，並移除會重新造成待辦壓力的全部到期數量。

### 實作項目

#### Today 前端

- [ ] `cardApi` 新增 prepare / next API 與型別。
- [ ] Today 使用獨立 TanStack Query key，不再沿用通用 Card list 的 infinite query。
- [ ] 首次進入與重新整理呼叫 prepare，保留後端目前批次。
- [ ] 顯示每批最多 5 張 Card，維持既有 CardTextMode 互動與 RWD。
- [ ] 加入「今天想多看一批」；next 取得新的 `cards` 時取代目前批次，回傳空 `cards` 時則保留目前批次。
- [ ] mutation 期間停用按鈕，避免連點；衝突時重新取得後端目前批次。
- [ ] next 回傳空的 `cards` 時保留目前批次，顯示「今天沒有更多新卡片了」，不顯示欠卡數量。
- [ ] 點進詳情時保留 Today 來源，只有此路徑觸發 read。
- [ ] 快速標記 `SEED` 後更新目前批次卡片及相關 query cache。
- [ ] 所有看板的 CardTextMode 統一隱藏 `nextShowAt`；Card Detail 保留 `nextShowAt`，並新增唯讀顯示 `lastOfferedAt`。

#### 右側欄

- [ ] 後端 `SidebarStatsResponse` 移除 `todayEchoCards`。
- [ ] `CardStatsProjection` 與統計 SQL 移除到期總數運算及不再使用的參數。
- [ ] `SidebarService` 同步調整 mapping。
- [ ] 前端 sidebar 型別與 `RightSidebar` 移除今日到期數量。
- [ ] 保留卡片總數、`SEED / GROWING / MATURE`、作品總數、未完成作品、高 snooze 卡片與標籤排行。
- [ ] 不新增 `UNMARKED` 統計。

### 驗收條件

- [ ] Today 不再無限載入全部到期 Card。
- [ ] 重新整理不會自動換批，主動按鈕才會換批。
- [ ] 批次少於 5 張或為空時，版面與操作正常；下一批為空時不清除目前 Card。
- [ ] Today 進入詳情會完成回顧，其他來源不會。
- [ ] 右側欄不再顯示或請求全部到期數量。
- [ ] 桌面、平板與手機尺寸下，批次按鈕及空狀態可正常使用。

### 建議驗證

- [ ] 前端 component / composable test 覆蓋 prepare、next、loading、衝突與空結果。
- [ ] sidebar integration test 驗證 response contract 已移除欄位。
- [ ] 前端 build 與瀏覽器手動流程驗證。

---

## 任務 5：稍後再看修正、整合驗證與上線準備

### 目標

補齊既有計數缺口，確認整條成長、查詢與 Today 流程可上線，並安全套用資料庫變更。

### 實作項目

#### 稍後再看

- [ ] `snoozeCard()` 每次成功操作執行 `snoozeCount += 1`。
- [ ] snooze 繼續依既有 request 更新 `nextShowAt`，但不算完成回顧。
- [ ] read 成功後將 `snoozeCount` 歸零。
- [ ] 確認「稍後再看次數 > 10」看板與右側欄統計可由正常操作累積。
- [ ] 本次不新增 `lastSnoozedAt`。

#### 整合與回歸

- [ ] 執行全部後端測試。
- [ ] 執行前端測試與 production build。
- [ ] 驗證 Create / Detail / Today / Search / Tag / Work material / sidebar 沒有 contract regression。
- [ ] 驗證各 mutation 後 TanStack Query cache 同步，不需整頁重新整理。
- [ ] 檢查候選查詢與綜合查詢的 SQL、索引使用、重複列及 N+1。
- [ ] 依手動測試清單驗證桌面與行動版。

#### 資料庫與上線

- [ ] 在本地現有資料副本重複執行 migration，確認第二次安全無副作用。
- [ ] 檢查 migration 前後 Card 總筆數及各 growth status 筆數。
- [ ] 先部署相容舊資料的後端 / migration 順序，避免 enum 或 NOT NULL 導致啟動失敗。
- [ ] 正式環境執行 migration 前備份資料庫。
- [ ] 正式環境執行後抽查 `growth_status`、`last_offered_at` 與索引。
- [ ] 部署前後端並完成 smoke test。
- [ ] 功能穩定後刪除本暫時 memo；若仍有未完成項目，移入正式 issue 或長期技術債文件。

### 完成定義

- [ ] 新 Card 不再預設為需要培育的 `SEED`。
- [ ] 使用者可透過查詢主動找回 Card。
- [ ] Today 每批最多 5 張，可主動換批且同日不重複。
- [ ] 提供、回顧與稍後再看的欄位更新責任互不混淆。
- [ ] 右側欄不再呈現全部到期債務。
- [ ] 本地及正式 migration 都有驗證紀錄。
- [ ] 前後端自動驗證通過，主要使用流程已完成人工 smoke test。

## 五、本次不處理

- 回流歷史、流入流出量與卡片健康度儀表板。
- 今日剩餘或全部到期數量。
- 自動補批、固定冷卻未開啟 Card、動態批次與推薦演算法。
- `lastOfferDay`、batch token 資料欄位、User 批次狀態或事件歷史表。
- User 今日完成／已耗盡日期欄位；沒有下一批時保留目前批次，不持久化空畫面。
- 自動調整 growth status，或以 growth status 影響回流。
- `openCount / lastOpenAt` 資料庫欄位改名。
- `lastSnoozedAt`。
- 暫停回流功能。
- BoardFlex 全面共用重構。
- Work / WorkCard 功能變更。

## 六、建議 Commit 顆粒度

完成各任務且驗證後再依實際 staged 內容決定，預期可拆為：

```text
feat(card): 調整卡片成長狀態語意
feat(card): 新增卡片綜合查詢
feat(card): 新增今日限量回流批次
feat(card): 調整今日看板與側邊欄統計
fix(card): 正確累加稍後再看次數
```

Migration 是否與對應後端功能放在同一 commit，依實際 SQL 存放位置決定；不可提交只依賴尚未納入版本控制的 schema 變更而沒有清楚部署說明的程式碼。
