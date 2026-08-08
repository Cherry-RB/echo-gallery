# Echo Gallery 全專案 Code Review 追蹤

## 文件目的

本文件記錄 2026-08-08 對 Echo Gallery 前後端所做的靜態 Code Review，作為後續分批修正、驗證與提交的追蹤依據。

這是一份持續維護的 Current 文件，不是一次性修正計畫。實作前仍須重新確認相關原始碼，因為檔案行號與現況可能在後續 commit 中改變。

## 審查範圍與限制

審查面向：

1. 安全性：SQL injection、JWT 驗證、敏感資訊暴露。
2. 例外處理完整性。
3. API 設計合理性。
4. 前後端型別一致性。
5. 測試覆蓋率缺口。
6. 程式碼可維護性。

本次僅進行靜態審查，未執行動態滲透測試、依賴漏洞掃描或正式環境設定檢查，也未讀取 `.env`、`credentials/` 或 `secrets/`。

正面結果：目前未發現把使用者輸入直接拼接至 SQL 的程式碼；Repository 查詢使用固定 JPQL／SQL 與參數綁定，也未發現明確可直接跨使用者讀寫 Card 或 Tag 的路徑。

## 狀態與更新規則

每個問題使用固定 CR 編號，狀態只使用以下值：

```text
待處理 → 處理中 → 待驗證 → 已完成
                    ↘ 接受風險
```

更新規則：

- 開始實作前，將狀態改為「處理中」，並記錄預計處理的 branch 或 commit。
- 程式碼完成但尚未完成全部驗證時，標記為「待驗證」。
- 通過完成條件所列驗證後，才能標記為「已完成」。
- 決定暫不處理時，標記為「接受風險」，並記錄原因與重新評估日期。
- 每個 commit 只處理一項 CR，或一組高度相關且能共同驗證的 CR。

## 追蹤總表

| 編號 | 嚴重度 | 面向 | 問題摘要 | 狀態 |
|---|---|---|---|---|
| CR-001 | 高 | 安全性 | 登入與註冊缺乏速率限制 | 待處理 |
| CR-002 | 中 | 安全性 | JWT 儲存與撤銷策略不足 | 待處理 |
| CR-003 | 中 | 安全性／維運 | 正式環境資料庫設定不安全 | 待處理 |
| CR-004 | 低 | 安全性 | 403／404 差異可透露資源存在性 | 待處理 |
| CR-005 | 中 | 例外處理 | API 錯誤回應格式不一致 | 待處理 |
| CR-006 | 中 | 例外處理 | Card request 驗證不足，可能造成 500 | 待處理 |
| CR-007 | 中 | 例外處理 | Card 狀態 request 缺省值會被當成操作 | 待處理 |
| CR-008 | 中 | 例外處理 | 安全上下文錯誤被靜默轉為 null | 待處理 |
| CR-009 | 低 | 例外處理 | Validation 只回傳第一個欄位錯誤 | 待處理 |
| CR-010 | 中 | API | 非冪等操作使用 PUT | 待處理 |
| CR-011 | 中 | API | 列表 API 缺少分頁 metadata | 待處理 |
| CR-012 | 中 | API | 查詢參數缺乏 enum 與邊界驗證 | 待處理 |
| CR-013 | 低 | API | 建立與刪除的 HTTP status 不精確 | 待處理 |
| CR-014 | 高 | 型別 | 單一 CardDto 無法正確代表摘要與詳情 | 待處理 |
| CR-015 | 中 | 型別／維護性 | 前端存在重複 Card DTO 與大量 any | 待處理 |
| CR-016 | 中 | 型別 | Tag delete 前後端回傳型別不一致 | 待處理 |
| CR-017 | 中 | 前端狀態 | localStorage 的使用者 ID key 不一致 | 待處理 |
| CR-018 | 高 | 測試 | 缺乏可穩定執行的實質測試 | 已完成 |
| CR-019 | 中 | 維護性 | CardService 過大且重複授權邏輯 | 待處理 |
| CR-020 | 中 | 可測試性 | 業務邏輯直接使用系統時鐘 | 待處理 |
| CR-021 | 中 | 維護性 | Query cache 更新依賴 any 與結構猜測 | 待處理 |
| CR-022 | 低 | 維護性 | 過時／亂碼註解與重複依賴 | 待處理 |

## 問題明細

### CR-001：登入與註冊缺乏速率限制

- 嚴重度：高（公開部署時）。
- 狀態：待處理。
- 相關檔案：`user/AuthController.java`、`security/SecurityConfig.java`。
- 風險：可能遭密碼暴力破解、帳號枚舉或大量註冊。
- 建議：在反向代理或應用層加入 IP 與帳號層級的 rate limiting，並使用一致的登入失敗訊息。
- 完成條件：超過限制時回傳 429；具備正常、超限與解除限制測試。

### CR-002：JWT 儲存與撤銷策略不足

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`security/JwtService.java`、`security/SecurityConfig.java`、前端 `utils/api/request.ts`、`stores/authStore.ts`。
- 風險：JWT 儲存在 `localStorage`，XSS 發生時可被讀取；後端 logout 無法讓已簽發的 24 小時 Token 失效。
- 建議：評估 HttpOnly Cookie，或採短效 access token、refresh token rotation、token version／撤銷機制與 CSP。
- 完成條件：記錄 Token lifecycle 決策；補上過期、錯誤簽章、撤銷與 logout 行為測試。

### CR-003：正式環境資料庫設定不安全

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`src/main/resources/application.yml`。
- 風險：`ddl-auto: update` 可能在部署時自動修改 schema，`show-sql: true` 會增加 log 暴露與噪音。
- 建議：依 profile 分離設定；production 使用 migration 並設定 `validate` 或 `none`，關閉 SQL 顯示。
- 完成條件：development／test／production 設定可區分，且 production 不自動改 schema。

### CR-004：403／404 差異可透露資源存在性

- 嚴重度：低。
- 狀態：待處理。
- 相關檔案：`card/CardService.java`、`tag/TagService.java`。
- 風險：其他使用者可透過 403 與 404 的差異推測 ID 是否存在。
- 建議：以 `findByIdAndUserId` 類型查詢直接限制 owner，查不到一律回 404。
- 完成條件：跨使用者 ID 不透露資源是否存在，並有授權測試。

### CR-005：API 錯誤回應格式不一致

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`controller/advice/GlobalExceptionHandler.java`、`exception/ErrorResponse.java`、前端 `utils/api/request.ts`。
- 風險：自訂 ErrorResponse、ResponseStatusException 與 Spring 預設 ProblemDetail 格式不同，前端固定讀取 `data.message` 可能遺失訊息。
- 建議：統一 `code`、`message`、`status`、`timestamp`、`path` 與 `fieldErrors`。
- 完成條件：主要 400／401／403／404／409／500 回應皆符合相同 schema，並有 controller 測試。

### CR-006：Card request 驗證不足，可能造成 500

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardRequest.java`、`card/CardService.java`。
- 風險：nullable `isArchived` 可能在設定 primitive boolean 時造成 NPE；intervalDays、URL、Tag 數量與長度缺少合理限制。
- 建議：明確區分 create／update DTO，補上 `@NotNull`、`@Min`、`@Max`、URL 與集合限制。
- 完成條件：缺欄位、負數、過長資料與無效 URL 回傳 400，不產生 500。

### CR-007：Card 狀態 request 缺省值會被當成操作

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardStatusRequest.java`、`card/CardController.java`。
- 風險：缺少 starStatus／archivedStatus 時會被當成 false；nextIntervalDays 缺少時會成為 0。
- 建議：拆成 StarRequest、ArchiveRequest、SnoozeRequest，加入明確 validation。
- 完成條件：缺少必要狀態時回傳 400，且不修改資料。

### CR-008：安全上下文錯誤被靜默轉為 null

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`util/SecurityUtil.java`。
- 風險：真正的認證問題被隱藏，可能在 Repository 或 owner 比較時轉成難以診斷的 500。
- 建議：缺少有效 principal 時拋出明確的未認證例外。
- 完成條件：無效安全上下文穩定回傳 401，並保留可診斷 log。

### CR-009：Validation 只回傳第一個欄位錯誤

- 嚴重度：低。
- 狀態：待處理。
- 相關檔案：`controller/advice/GlobalExceptionHandler.java`。
- 風險：使用者必須逐次修正欄位，API client 也無法一次取得完整問題。
- 建議：回傳 fieldErrors map 或陣列。
- 完成條件：多欄位無效時，單次回應包含所有欄位錯誤。

### CR-010：非冪等操作使用 PUT

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardController.java`、前端 `utils/api/cardApi.ts`。
- 風險：read 會累加 openCount，重送相同 PUT 會得到不同結果；star／snooze 也混合狀態與事件語意。
- 建議：狀態設定使用 PATCH，事件使用 POST，或明確設計 idempotency。
- 完成條件：API method 與重送行為有明確契約及測試。

### CR-011：列表 API 缺少分頁 metadata

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardController.java`、`card/CardService.java`、前端 `components/Board-Flex.vue`。
- 風險：前端以筆數猜測 hasNext；剛好整頁時多送空請求；Random 看板可能重複資料或無限載入。
- 建議：回傳 items、page、pageSize、hasNext、total 等 metadata，並明確定義 Random 分頁策略。
- 完成條件：前端不再以陣列長度猜測下一頁，Random 不重複或有明確 session 策略。

### CR-012：查詢參數缺乏 enum 與邊界驗證

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardListRequest.java`、`card/BoardType.java`、`card/CardController.java`。
- 風險：任意 operator 被默認為 OR，page／threshold／tagIds 缺乏限制。
- 建議：使用 enum，並為頁碼、頁數、門檻與 ID 數量加入 validation。
- 完成條件：無效查詢穩定回傳 400，不默默改變語意。

### CR-013：建立與刪除的 HTTP status 不精確

- 嚴重度：低。
- 狀態：待處理。
- 相關檔案：`user/AuthController.java`、`card/CardController.java`、`tag/TagController.java`。
- 風險：API contract 難以由 status 表達建立或無內容結果。
- 建議：建立使用 201；不回 body 的刪除使用 204；若保留刪除前 DTO，清楚記錄 200 contract。
- 完成條件：前後端型別、status 與文件一致。

### CR-014：單一 CardDto 無法正確代表摘要與詳情

- 嚴重度：高。
- 狀態：待處理。
- 相關檔案：後端 `CardSummaryResponse.java`、`CardDetailResponse.java`；前端 `types/card.ts`。
- 風險：後端 ID 是 Long，前端宣告 string；summary 缺少多個前端必填欄位；showContentPreview 不存在於 Card response。
- 建議：拆分 CardSummary、CardDetail、CreateCardRequest、UpdateCardRequest 與互動 response；評估由 OpenAPI 產生型別。
- 完成條件：每個 API client 使用對應型別，不再用單一 CardDto 假裝代表所有 response。

### CR-015：前端存在重複 Card DTO 與大量 any

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`types/card.ts`、`utils/useCardStatus.ts`、`utils/api/cardApi.ts`、`utils/api/authApi.ts`。
- 風險：欄位變更容易只更新其中一套；API 邊界與 Query cache 缺少編譯期保護。
- 建議：移除區域 CardDTO，集中 request／response 型別，逐步消除 API 與 cache 的 any。
- 完成條件：API public methods 與 Query cache 不使用未說明的 any。

### CR-016：Tag delete 前後端回傳型別不一致

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：後端 `tag/TagController.java`、前端 `utils/api/tagApi.ts`。
- 風險：前端宣告 `Promise<TagDto>`，後端實際回傳 204 無 body。
- 建議：前端改為 `Promise<void>`，或後端明確回傳刪除結果，兩者擇一。
- 完成條件：型別、HTTP status 與實際 body 一致。

### CR-017：localStorage 的使用者 ID key 不一致

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：前端 `stores/authStore.ts`。
- 風險：初始化讀取 `id`、登入寫入 `userId`、登出刪除 `id`，造成重整後無法還原及登出後殘留。
- 建議：統一 key，集中常數與清理行為；評估前端是否真的需要持久化 userId。
- 完成條件：登入、重整、登出流程測試通過且沒有殘留 key。

### CR-018：缺乏可穩定執行的實質測試

- 嚴重度：高。
- 狀態：已完成（2026-08-09）。
- 相關檔案：後端 `build.gradle.kts`、`src/test/resources/application-test.yml`、`src/test/java/com/echogallery/support/IntegrationTestBase.java`、`src/test/java/com/echogallery/EchoGalleryApplicationTests.java`、`src/test/java/com/echogallery/security/SecurityAndOwnershipIntegrationTests.java`；前端 `package.json`、`package-lock.json`、`vitest.config.ts`、`src/stores/authStore.test.ts`、`src/types/board.test.ts`；根目錄 `.gitignore`。
- 風險：後端只有 contextLoads，且目前會因資料庫環境前置條件失敗；前端沒有測試指令。
- 建議：建立 test profile 與隔離資料庫，優先補 JWT、多租戶、validation、互動行為、錯誤 schema 與前端狀態測試。
- 完成條件：乾淨環境可執行測試，核心安全與業務規則有回歸保護。
- 實作 Commit：`89c1804`（後端 PostgreSQL 隔離測試環境）、`483ca28`（JWT 與多租戶授權整合測試）、`11819a2`（前端 Vitest 與核心狀態測試）。
- 驗證證據：2026-08-09 於全新本機 clone 執行；Docker daemon 正常，後端 `.\gradlew.bat test` 通過 7 個測試，前端 `npm ci` 安裝成功且掃描為 0 個漏洞，`npm run test:run` 通過 2 個測試檔案共 4 個測試，`npm run build` 通過。
- 驗收結論：測試可在不依賴原工作目錄、開發資料庫或 `.env` 的環境重新安裝與執行；validation、錯誤 schema 及其他業務測試由對應 CR 持續擴充，不影響本項測試基礎驗收。

### CR-019：CardService 過大且重複授權邏輯

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardService.java`、`card/CardRepository.java`。
- 風險：每個操作重複取得 user、findById、owner 比對與 DTO mapping，修改時容易漏掉授權。
- 建議：建立 user-scoped query／findOwnedCard，分離查詢、互動與 mapping 職責。
- 完成條件：授權檢查集中且有測試，service 方法可聚焦於單一業務行為。

### CR-020：業務邏輯直接使用系統時鐘

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：`card/CardService.java`。
- 風險：回流、星標冷卻與午夜邊界測試無法穩定重現。
- 建議：注入 Clock，測試使用固定時間。
- 完成條件：時間相關 service 測試不依賴測試執行當下時間。

### CR-021：Query cache 更新依賴 any 與結構猜測

- 嚴重度：中。
- 狀態：待處理。
- 相關檔案：前端 `utils/useCardStatus.ts`。
- 風險：同時處理一般陣列、InfiniteData 與詳情 cache，新增 query key 或 response wrapper 後容易漏更新。
- 建議：建立 query key factory，將 summary／detail／infinite cache patch 分離並補 optimistic rollback 測試。
- 完成條件：cache 結構具名且有型別，主要 mutation 成功與失敗路徑都有測試。

### CR-022：過時／亂碼註解與重複依賴

- 嚴重度：低。
- 狀態：待處理。
- 相關檔案：`build.gradle.kts`、部分 Java／Vue 檔案。
- 風險：註解仍描述尚未整合 JWT，或包含亂碼；Actuator dependency 重複宣告，增加維護噪音。
- 建議：獨立做純清理 commit，不與功能修改混合。
- 完成條件：移除過時註解與重複依賴，建置結果不變。

## 建議優先處理 Top 5

1. CR-018（已完成）：已建立可穩定執行的測試環境，後續 CR 應持續在此基礎補上回歸測試。
2. CR-006、CR-007、CR-012：補齊 request validation 與邊界限制。
3. CR-014、CR-015、CR-016、CR-017：統一前後端 contract 與前端狀態型別。
4. CR-001、CR-002：強化公開認證端點與 JWT lifecycle。
5. CR-005、CR-010、CR-011、CR-013：統一例外格式與 API 語意。

## 建議分批與 Commit 邊界

不建議只用一個大型 commit 修完全部問題。最低應拆成三批，實務上建議使用五個以上的邏輯階段，而且每個階段仍可再拆成數個原子 commit：

1. 測試基礎：CR-018（已完成）、CR-020。
2. 輸入與錯誤：CR-005～CR-009、CR-012。
3. Contract 與前端型別：CR-014～CR-017、CR-021。
4. 認證安全：CR-001、CR-002、CR-004。
5. API 與維護性：CR-003、CR-010、CR-011、CR-013、CR-019、CR-022。

依賴順序不是絕對，但建議先建立測試保護，再修改安全、contract 與 API 行為。API endpoint、資料庫 schema 或認證策略變更，必須先說明計畫並取得使用者確認。

## 單一 CR 的重複處理流程

每次只選一項 CR 或一組高度相關問題，重複以下流程：

1. 指定 CR 編號與目標，不先要求修改。
2. Agent 重新讀取目前程式碼，確認問題仍存在及影響範圍。
3. Agent 提出修改計畫、API／資料庫影響、測試策略與 commit 邊界。
4. 使用者確認計畫。
5. Agent 實作並依影響範圍驗證。
6. Agent 回報差異、測試、未驗證項目與知識維護評估。
7. 使用者手動 stage 後輸入「準備推版」。
8. Commit 完成後，將 CR 狀態與驗證證據更新為「已完成」。

### 可復用 Prompt：分析單一 CR

```text
請處理 docs/11_全專案CodeReview追蹤.md 中的 CR-XXX。

這一輪先不要修改檔案。請重新檢查目前程式碼，確認問題是否仍存在，並提供：
1. 根因與實際風險
2. 受影響檔案
3. 修改計畫
4. API、資料庫與相容性影響
5. 測試策略與完成條件
6. 建議的 commit 邊界

若文件內容已過時，請以目前程式碼為準並指出差異，等待我確認後再實作。
```

### 可復用 Prompt：確認後實作

```text
我確認 CR-XXX 的修改計畫，請開始實作。

請只處理這項 CR 與已確認的必要相依修改，不要順手處理其他 CR。
完成後執行適用的測試，回報修改檔案、原因、測試結果、未驗證項目，
並提出追蹤文件的狀態更新建議。不要執行 git add、commit 或 push。
```

### 可復用 Prompt：準備推版

```text
準備推版。請只根據 staged 變更，提供中文檔案說明、是否需要拆分 commit、
符合 Conventional Commits 的中文 commit 訊息，以及測試與風險摘要。
不要執行 git add、commit 或 push。
```

### 可復用 Prompt：完成後更新追蹤

```text
請針對 CR-XXX 執行任務復盤，核對完成條件與測試證據。
若已符合，請提出將狀態改為「已完成」的文件更新計畫；
若未符合，請列出仍缺少的項目。先不要修改檔案。
```

## ExecPlan 使用原則

本文件是跨多次 commit 的 Code Review backlog 與追蹤來源，不應直接視為一份要求 Agent 一次完成全部內容的 ExecPlan。

當單一 CR 具備以下特徵時，才建議從本文件衍生獨立 ExecPlan：

- 需要跨前端、後端、設定或資料庫。
- 預計需要多個工作階段或多個 commit。
- 存在 API migration、資料相容性或回復策略。
- 需要保留關鍵決策、進度、意外發現與驗證證據。

ExecPlan 應只涵蓋一個可驗收目標，例如「統一 Card API contract」或「建立 JWT 與多租戶安全測試基礎」，並持續記錄 Progress、Decisions、Surprises、Validation 與 Outcomes。完成後再回寫本追蹤文件的 CR 狀態。

不建議在尚未建立可靠測試前，讓 Agent 無人監督地一次自動修改全部安全、API 與資料契約問題。可將自動化用於唯讀盤點、測試執行、差異整理與狀態提醒；實際修改仍以單一 CR、明確計畫與人工確認為界線。

## 更新紀錄

| 日期 | 變更 | 說明 |
|---|---|---|
| 2026-08-09 | 完成 CR-018 | 建立前後端測試基礎，並於全新本機 clone 完成後端測試、前端安裝、測試與正式建置驗證 |
| 2026-08-08 | 建立 | 建立全專案靜態 Code Review 問題清單、Top 5、分批策略與可復用流程 |
