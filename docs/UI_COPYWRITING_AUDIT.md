# UI 文案重構覆蓋清單

## 檢查基準

以 `src/router/index.ts`、`src/main.ts`、當前 Vue 元件及共用 TypeScript 顯示文字為準，沿相對匯入追查可達元件，並檢查 template、動態字串、label、placeholder、Tooltip、title、aria-label、驗證、Toast、確認與空狀態。

範圍：18 個實際頁面路由，對應 17 個頁面實作（`CardDetail.vue` 同時承接新建與詳情）；連同 Layout、App 與子元件共 69 個可達 Vue 檔案。下列「已檢查」皆指原始碼檢查，**不代表瀏覽器視覺驗收**。

## 路由（18/18 已檢查）

| 功能區 | 路由 | 實作 | 處理 |
| --- | --- | --- | --- |
| 登入、展示 | `/login` | `views/Login.vue` | 展示入口、通知、中英用語；保留驗證及展示內容庫選項 |
| 註冊 | `/register` | `views/Register.vue` | 成功訊息引導登入；保留欄位及驗證 |
| 登出 | `/logout` | `components/Logout.vue` | 簡化載入提示 |
| 卡片新建 | `/card/new` | `views/CardDetail.vue` | 新建用語、表單與回流設定 |
| 卡片詳情 | `/card/:id` | 同上 | 筆記空狀態、編輯、還原、回流與錯誤狀態 |
| 今日回流 | `/board/today` | `views/boards/TodayBoard.vue` | 副標、批次處理空狀態、無更多卡片；保留略過與重新排程後果 |
| 全部卡片 | `/board/all` | `views/boards/AllCardsBoard.vue` | 已封存名稱；保留更新、熱門、隨機起點、延後次數定義 |
| 搜尋 | `/search` | `views/SearchView.vue` | 卡片 ID 顯示一致；篩選、排序、頁碼、查詢失敗保留 |
| 標籤 | `/center/tag` | `views/center/TagCenter.vue` | 跨裝置選取說明、引導、刪除後果 |
| 奇珍列表 | `/treasures` | `views/treasure/TreasureList.vue` | 指定副標、館藏之中、空狀態、記收益、目標試煉等級 |
| 奇珍詳情 | `/treasures/:id` | `views/treasure/TreasureDetail.vue` | 奇珍來歷、編輯、試煉、收益、來源與刪除範圍 |
| 實驗列表 | `/experiments` | `views/experiment/ExperimentList.vue` | 探索副標、空狀態、通用範例、探索紀錄刪除後果 |
| 實驗詳情 | `/experiments/:id` | `views/experiment/ExperimentDetail.vue` | 試法、材料、整理、比較、種入、長出、編輯與清除／刪除 |
| 議題列表 | `/issues` | `views/issue/IssueList.vue` | 副標；保留議題、系統問題與現實訊號概念 |
| 議題詳情 | `/issues/:id` | `views/issue/IssueDetail.vue` | 移除版本實作說明；保留研判、模型、決策與刪除後果 |
| 現在行動 | `/overview` | `views/overview/OverviewDashboard.vue` | 指定副標與區塊、實際目標等級、收益與空狀態 |
| 近期回顧 | `/overview/recent` | `views/overview/OverviewRecent.vue` | 指定副標、統計名稱與原定義、衍生與材料、活動展開 |
| 回流狀態 | `/overview/cards` | `views/overview/CardReturnOverview.vue` | 指定副標與標題；保留狀態、數字、計算 Tooltip |

重新導向也已檢查：`/` → `/overview`；`/board/hot`、`/board/random`、`/board/archived`、`/board/snoozed` → `/board/all?view=…`；`/gardens` → `/experiments`、`/gardens/:id` → 實驗詳情；未知路徑 → `/`。沒有修改路徑或守衛。

目前沒有獨立帳號資料、系統設定、權限不足或 404 頁。權限／連線錯誤透過共用請求攔截器與頁面錯誤狀態處理；未新增不存在的畫面。

## 共用與互動元件（51/51 已檢查）

以下路徑均相對於 `echo-gallery-frontend/src/components/`；同列各檔皆已檢查。

| 功能區 | 元件 | 處理 |
| --- | --- | --- |
| 導航、系統（3） | `LeftSidebar.vue`、`ThemeSwitcher.vue`、`Logout.vue` | 展示重設不可復原性、登出；導航與主題用語保留 |
| 卡片顯示（3） | `Board-Flex.vue`、`CardItem.vue`、`CardTextMode.vue` | 載入、空列表、列表尾端、暫停回流、還原、刪除；卡片內容原樣呈現 |
| 卡片建立、選取與排程（3） | `QuickCreateCardDialog.vue`、`CardPickerDialog.vue`、`RecurrenceIntervalPicker.vue` | 引導與動作一致、卡片 ID；週期與暫停語意保留 |
| 共用 Dialog、閱讀（2） | `AppDialog.vue`、`ExpandableText.vue` | 未儲存後果；展開操作保留 |
| 實驗互動（5） | `experiment/AddCardToExperimentDialog.vue`、`ExperimentCardItem.vue`、`ExperimentExplorationEditor.vue`、`ExperimentExplorationHistoryDialog.vue`、`ExperimentQuickRecordAction.vue` | 種入引導、試法與發現標題、通用 placeholder、紀錄空狀態；土壤角色與狀態保留 |
| 議題互動（6） | `issue/AddCardToIssueDialog.vue`、`CardIssueManager.vue`、`CurrentAssessmentGuide.vue`、`IssueMaterialManager.vue`、`IssueUpdateDialog.vue`、`IssueUpdates.vue` | 訊號回顧引導、必填錯誤；素材關聯、空白框架、研判用語保留 |
| 總覽導航（1） | `overview/OverviewNavigation.vue` | 三頁名稱保留 |
| 奇珍建立與來源（3） | `treasure/TreasureFormDialog.vue`、`TreasureSourcePicker.vue`、`TreasureSourceSummary.vue` | 編輯奇珍、成功通知、卡片 ID；來源選取規則保留 |
| 奇珍等級與試煉（5） | `treasure/TreasureRankBadge.vue`、`TreasureRankSelect.vue`、`TreasureChallengeRoute.vue`、`TreasureChallengeExamples.vue`、`TreasureTrialDialog.vue` | 石銅銀金保留、試煉用語、狀態互斥提示與成功通知；通過條件不改 |
| 奇珍歷程與收益（3） | `treasure/TreasureAttemptHistory.vue`、`TreasureYieldDialog.vue`、`TreasureYieldHistory.vue` | 當時試煉、收益定義、記收益、空狀態、刪除與成功訊息 |
| 頁面結構（4） | `ui/PageHeader.vue`、`SectionHeader.vue`、`DetailShell.vue`、`DetailHeading.vue` | 檢查 props 與 slot 文字來源；無固定業務文案需改 |
| 選取結構（4） | `ui/PickerWorkspace.vue`、`PickerOption.vue`、`SelectionSummary.vue`、`CardPickerSelectionSummary.vue` | 取消「左側」位置依賴；已選／未選等精確標籤保留 |
| 歷程結構（5） | `ui/HistoryDialog.vue`、`HistoryTimeline.vue`、`HistoryEvent.vue`、`HistoryFields.vue`、`RecordHistoryTrigger.vue` | 共用關閉、紀錄操作、動態計數與欄位名稱檢查；由父元件傳入文字 |
| 空狀態、表單、封面（4） | `ui/AppEmptyState.vue`、`FieldCounter.vue`、`CoverImage.vue`、`CoverImageField.vue` | 字數、網址錯誤、圖片載入提示保留；空狀態由呼叫端提供 |

另檢查 `App.vue`（連線錯誤）與 `layout/MainLayout.vue`（桌面／手機導航與快速建立），兩者合計 2 檔；加上前述 51 個元件與 16 個 View，共 69 檔。

## 共用文字來源與保留範圍

- `main.ts`：Element Plus 內建繁體中文語系；沒有新增依賴。
- `index.html`：頁面語言標記改為 `zh-Hant`，與介面語言及輔助閱讀一致。
- `utils/treasure.ts`：等級、狀態、事件顯示名稱與收益定義；錯誤仍精確，範例不冒充使用者成果。
- `utils/useCardStatus.ts`：卡片成功、封存、還原訊息；排程回傳數值及邏輯保留。
- `utils/cardTextFieldCopy.ts`、`cardForm.ts`、`imageUrl.ts`、`composables/useTags.ts`：既有欄位、限制與驗證適合，保留。
- `utils/issueStatus.ts`、`issueUpdate.ts`、`experimentTheme.ts`：保留顯示 mapping、資料原文與識別色，不改狀態值。
- `utils/api/request.ts`、`stores/authStore.ts`：保留權限、登入、伺服器與網路錯誤流程；後端回傳訊息仍原樣呈現。後端訊息維持精確，未為敘事化而修改 Java。
- 所有 API JSON key、型別、Enum value、Query Key、路徑、Test ID、演算法、使用者資料與 Demo seed 不改。試煉／收益的介面名稱更動不涉及 contract。

## 未接入的檔案（7）

`views/Home.vue`、`views/CardCreate.vue`、`components/HelloWorld.vue`、`CardTextModeFooter.vue`、`RightSidebar.vue`、`SearchBar.vue`、`ui/FormSection.vue` 不在目前入口／路由的相對匯入依賴中。保留其內容，不計入可使用 UI 覆蓋率。實際 `/card/new` 使用 `CardDetail.vue`。

## 驗證與人工驗收

原始碼覆核涵蓋插值、條件分支、操作結果及路由綁定；不以 Build 成功取代視覺驗收。

| 驗證 | 結果 |
| --- | --- |
| `npm run build`（內含 `vue-tsc -b` 與 `vite build`） | 通過；仍有超過 500 kB 的 bundle 大小警告，未為文案任務改動分包設定 |
| `npm run test:run` | 35 份測試、139 個案例全部通過；8 份既有測試同步更新，新增 4 個案例 |
| 新增／擴充的重點斷言 | 收益請求等待與失敗不宣稱成功；入藏、收益與試煉通過通知；試煉狀態提示互斥；金級提示 |
| `git diff --check` | 通過 |
| 修改檔案編碼與換行 | 無替代字元、CR 字元或缺少檔尾換行 |
| 後端測試 | 未執行；未修改後端程式或 API contract |
| 瀏覽器導航、Dialog 與 RWD | 未驗證，依下列清單人工驗收 |

目前沒有可用的瀏覽器控制工具，也沒有既有 Playwright／Cypress 設定，因此以下尚待人工驗證：

1. 在 1440px、768px、390px、320px 走訪上述路由與總覽切換；確認手機抽屜、主題切換及新建入口可用。
2. 檢查長副標、總覽標題、目標等級加自訂長試煉名稱的換行、截斷與 Tooltip；確認卡片按鈕不擠壓內容。
3. 開啟快速新建、卡片編輯、種入／長出、探索紀錄、常用試法、整理、比較、議題訊號與奇珍來源 Dialog；確認內容可捲動且頁尾操作可見。
4. 檢查奇珍無館藏／篩選無結果、收益零筆、金級、未設定試煉、待確認、未通過、未來規劃、封存狀態，各自顯示相符文字。
5. 在隔離測試環境驗證入藏、通過、記收益的成功／失敗提示，刪除與離開編輯確認；不得為驗收污染既有 Demo session 或正式資料。
6. 檢查繁體中文的分頁、日期面板與預設關閉文字，確認長字串與數字仍可閱讀。

本次未建立帳號、Demo session 或測試資料，未啟動後端或操作資料庫；單元測試使用記憶體中的 API 替身。

## 實際修改檔案

### 產品程式（43 檔）

- `echo-gallery-frontend/index.html`
- `echo-gallery-frontend/src/App.vue`
- `echo-gallery-frontend/src/components/AppDialog.vue`
- `echo-gallery-frontend/src/components/Board-Flex.vue`
- `echo-gallery-frontend/src/components/CardPickerDialog.vue`
- `echo-gallery-frontend/src/components/CardTextMode.vue`
- `echo-gallery-frontend/src/components/LeftSidebar.vue`
- `echo-gallery-frontend/src/components/Logout.vue`
- `echo-gallery-frontend/src/components/QuickCreateCardDialog.vue`
- `echo-gallery-frontend/src/components/experiment/AddCardToExperimentDialog.vue`
- `echo-gallery-frontend/src/components/experiment/ExperimentExplorationEditor.vue`
- `echo-gallery-frontend/src/components/experiment/ExperimentExplorationHistoryDialog.vue`
- `echo-gallery-frontend/src/components/experiment/ExperimentQuickRecordAction.vue`
- `echo-gallery-frontend/src/components/issue/IssueUpdateDialog.vue`
- `echo-gallery-frontend/src/components/issue/IssueUpdates.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureAttemptHistory.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureChallengeRoute.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureFormDialog.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureSourcePicker.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureTrialDialog.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureYieldDialog.vue`
- `echo-gallery-frontend/src/components/treasure/TreasureYieldHistory.vue`
- `echo-gallery-frontend/src/components/ui/CardPickerSelectionSummary.vue`
- `echo-gallery-frontend/src/layout/MainLayout.vue`
- `echo-gallery-frontend/src/main.ts`
- `echo-gallery-frontend/src/utils/treasure.ts`
- `echo-gallery-frontend/src/utils/useCardStatus.ts`
- `echo-gallery-frontend/src/views/CardDetail.vue`
- `echo-gallery-frontend/src/views/Login.vue`
- `echo-gallery-frontend/src/views/Register.vue`
- `echo-gallery-frontend/src/views/SearchView.vue`
- `echo-gallery-frontend/src/views/boards/AllCardsBoard.vue`
- `echo-gallery-frontend/src/views/boards/TodayBoard.vue`
- `echo-gallery-frontend/src/views/center/TagCenter.vue`
- `echo-gallery-frontend/src/views/experiment/ExperimentDetail.vue`
- `echo-gallery-frontend/src/views/experiment/ExperimentList.vue`
- `echo-gallery-frontend/src/views/issue/IssueDetail.vue`
- `echo-gallery-frontend/src/views/issue/IssueList.vue`
- `echo-gallery-frontend/src/views/overview/CardReturnOverview.vue`
- `echo-gallery-frontend/src/views/overview/OverviewDashboard.vue`
- `echo-gallery-frontend/src/views/overview/OverviewRecent.vue`
- `echo-gallery-frontend/src/views/treasure/TreasureDetail.vue`
- `echo-gallery-frontend/src/views/treasure/TreasureList.vue`

### 同步與擴充測試（8 檔）

- `echo-gallery-frontend/src/components/issue/IssueUpdates.test.ts`
- `echo-gallery-frontend/src/components/treasure/TreasureChallenges.test.ts`
- `echo-gallery-frontend/src/components/treasure/TreasureDialogs.test.ts`
- `echo-gallery-frontend/src/components/treasure/TreasureYieldHistory.test.ts`
- `echo-gallery-frontend/src/views/boards/TodayBoard.test.ts`
- `echo-gallery-frontend/src/views/overview/OverviewDashboard.test.ts`
- `echo-gallery-frontend/src/views/overview/OverviewRecent.test.ts`
- `echo-gallery-frontend/src/views/treasure/TreasureList.test.ts`

### 新增文件（2 檔，尚未暫存）

- `docs/UI_COPYWRITING_GUIDE.md`
- `docs/UI_COPYWRITING_AUDIT.md`
