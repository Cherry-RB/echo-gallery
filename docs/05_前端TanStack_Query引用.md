# TanStack Query 深度技術實戰與核心原理全紀錄

## 一、 技術選型決策：從路由跳轉痛點談起

### 1. 選型背景與痛點（Trigger）

在開發 EchoGallery 過程中，遇到了關鍵的狀態同步瓶頸：使用者在瀑布流瀏覽多頁卡片後進入詳情頁（`CardDetail.vue`），執行互動操作後返回列表，卻發現頁面狀態重置，導致滾動位置遺失，或列表顯示的卡片狀態（如 `isArchived`）與詳情頁執行過的操作不一致。

### 2. 為什麼 TanStack Query 是最佳解？

傳統方案如 Pinia 雖能保存全域狀態，但無法解決非同步請求的「快取有效性」與「競態條件」。

* **解決的問題**：將 API 請求視為伺服器狀態（Server State），透過 `queryKey` 實現自動化快取管理。
* **技術選型優勢**：
* **自動化生命週期**：無需手動維護 `isLoading`、`error` 等狀態。
* **快取共享**：`['cards']` 與 `['card', id]` 可作為單一資料源，詳情頁更新能主動觸發列表頁重渲染。
* **樂觀更新（Optimistic Update）**：提供 `onMutate` 鉤子，實現極致的 UI 回應速度。

---

## 二、 核心應用場景與配置對應

### 1. 瀑布流中的無限滾動（Board-Flex.vue）

* **應用場景**：處理分頁請求與無限捲動。
* **實戰邏輯**：使用 `useInfiniteQuery`。
* **關鍵配置**：
```typescript
queryKey: ['cards'],
queryFn: ({ pageParam }) => cardApi.getCards({ pageNumber: pageParam, pageSize: 15 }),
getNextPageParam: (lastPage, allPages) => { /* 依後端回傳判定有無下一頁 */ }

```


* **底層原理**：將 API 回傳的每一頁作為一個子陣列儲存在 `pages` 結構中，保持列表的增量渲染，避免全量重新查詢。

### 2. 詳情頁的精準同步（CardDetail.vue）

* **應用場景**：卡片內容展示與單一狀態更新。
* **實戰邏輯**：使用 `useQuery` 並定義專屬 `queryKey`。
* **優化軌跡**：從初期的自訂 `useAsync` 手動封裝，轉向 `useQuery(['card', id])`，實現與列表頁快取的無縫聯動。

### 3. 文字截斷與視覺防禦（CardTextMode.vue）

* **痛點**：內容快取更新後，長字元或不規則排版導致 UI 崩潰。
* **解決方案（核心 CSS）**：
```css
display: -webkit-box;
-webkit-line-clamp: 3;
-webkit-box-orient: vertical;
overflow: hidden;
overflow-wrap: break-word; /* 強制斷行，防止長英文單字撐破卡片 */
word-break: break-word;

```

---

## 三、 封裝 `useCardStatus.ts` 與進階快取管理

### 1. 樂觀更新的手術刀式實作

這是為了確保網路延遲不會影響互動體驗。`useCardStatus.ts` 封裝了四階段除錯邏輯：

* **`cancelQueries`**：阻斷舊的請求，確保快取一致性。
* **`prepareSnapshot`**：建立 `previousCards` 與 `previousCardDetail` 快照，作為發生錯誤時 Rollback 的資料源。
* **精準更新（`setQueryData`）**：
```typescript
// 遍歷所有頁面，尋找對應 ID 並替換
pages: old.pages.map(page => page.map(card => 
    String(card.id) === String(variables.id) ? updatedCard : card
))

```

* **`onError` 與 `onSettled**`：確保在請求錯誤後自動復原，請求結束後自動失效舊資料，驅動下次正確查詢。

### 2. 全局優化策略

* **策略**：管理所有 `['cards']` 與 `['card', id]` 開頭的資料。
* **技術執行**：透過 `queryClient.invalidateQueries({ queryKey: ['cards'] })` 實現全域性的強制失效，確保任何時候進入列表頁都是最新數據。
