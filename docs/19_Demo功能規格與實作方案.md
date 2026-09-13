# Demo 功能規格、實作方案與展示驗收

> 本文件是 Demo 的唯一規格文件，整併原「使用者故事與展示企劃」、「支援功能與實作調整評估」、「測資規格」與「展示腳本與截圖驗收」。判斷實際行為仍以程式碼、設定與自動測試為準。

## 1. 產品目的與範圍

EchoGallery 讓使用者把自己認可的內容，從社群即時演算法的注意力洪流中帶出來，再以自己設定的理由與節奏重新遇見。它不決定使用者應該喜歡什麼、不控制外部平台推薦，也不承諾改變外部平台結果。

使用者遇見值得保留的內容時建立 Card，留下「為什麼保存」與回流間隔；到期後，Today 提供有限批次，使用者可查看、稍後再看、暫停或封存。Demo 的目的，是讓第一次接觸者看見同一套流程能容納技術、視覺素材與文字筆記，而不是把使用者硬分成職業或人格類型。

本輪不包含 AI 推薦、語意理解、平台演算法整合、自動通知、依議題自動推薦 Card，亦不複製、嵌入或儲存第三方文章、影片、圖片內容。

## 2. 使用者體驗規格

一般 Email／密碼登入與註冊是主要入口。登入表單下方僅在 Demo 開關啟用時顯示次要快速體驗入口：

> 想先看看不同內容如何回流？選擇展示內容庫，立即體驗可操作資料。

使用者先選內容庫，才顯示約 40 至 60 字的情境說明與「開始體驗」按鈕。不可顯示 Demo 密碼，不可將內容庫包裝成人格分類，也不可用 Demo 取代一般登入。

登入後的帳號選單僅在 Demo 工作階段顯示「重新開始此展示」及「會清除本次展示中的操作」說明。確認後會重建同一內容庫的初始資料並留在登入狀態。一般帳號不顯示此操作；Today、Card、Tag、Work 的既有操作與 UI 不因 Demo 而改變。

Demo 是匿名、瀏覽器內以 JWT 維持的工作階段：重整或關閉再開瀏覽器時，JWT 尚未到期即可繼續；按「登出」會清除本機 JWT，沒有帳密或取回機制，下一次快速體驗會建立新的工作階段。舊資料僅保留到到期清理，使用者不能重新進入。

## 3. 三個內容庫與內容權利

| key | 內容庫 | 核心敘事 | 補充展示 |
| --- | --- | --- | --- |
| `tech` | 技術與學習收藏 | 把已篩選的知識接回學習節奏 | 標籤、搜尋、回流間隔、Redis 學習脈絡 |
| `visual` | 視覺靈感與創作素材 | 讓收藏過的素材再次成為創作起點 | 稍後再看、候選／已採用素材、秋日咖啡店插畫 |
| `writing` | 文字片段與生活觀察 | 重新遇見尚未完成的想法 | 原創 `content`、暫停、封存、下班路上 |

- Card 標題、摘要、收藏理由與筆記內容皆為原創 Demo 文字。
- 外部 URL 僅作跳轉入口；技術庫優先官方文件，視覺庫優先大型文化機構 Open Access 或公版館藏頁面。
- 不複製或重用外部來源的長文、圖片或截圖；文字庫不假冒真人日記、引文或他人作品。
- 每個 URL 在修改測資時應重新確認可開啟、標題與目的地相符。

## 4. 資料模型、初始化與相對日期

可執行測資的唯一內容來源是 [`demo-libraries.json`](../echo-gallery-backend/src/main/resources/demo/demo-libraries.json)。不得在 Java、Markdown 或其他檔案複製 Card 文案、網址或狀態資料。`DemoCatalog` 負責解析與檢查三庫各 15 張資料；`DemoSessionService` 只負責建立使用者副本、以建立時刻計算相對日期、重設與清理。

每次建立 Demo 時系統建立隨機匿名 `User`，標記 `is_demo_session`、`demo_library`、`demo_expires_at`，並複製該庫的 Tags、15 張 Cards、一個 Work 與其 WorkCards。不同訪客使用不同 user id，不共用可修改資料。

| 狀態 | 每庫數量 | JSON／初始化規則 | 展示目的 |
| --- | ---: | --- | --- |
| Today 首批 | 5 | `dayOffset = -5` 至 `-1` | 首次 Today 固定取五張 |
| Today 下一批 | 5 | `dayOffset = -1`，初始未曾 offer | 點「今天想多看一批」後再取五張 |
| 未到期 | 3 | `dayOffset = 60`、`75`、`90` | 保留尚未到期的回流結構 |
| 暫停 | 1 | `intervalDays = null`、`dayOffset = null`、未封存 | 不進 Today |
| 封存 | 1 | `archived = true` | 不進 Today |

`lastOfferedAt` 不得從其他工作階段複製；新建與重設的資料必須重新初始化。重設會先刪除該使用者的 Work updates、WorkCards、Works、Cards、Tags，再由 JSON 建立，避免影響其他 Demo 或一般帳號。

## 5. API、權限與生命週期

| API | 行為 | 權限／結果 |
| --- | --- | --- |
| `POST /api/auth/demo-sessions` | 以 `library` 建立匿名 Demo 與 JWT | 功能開啟時公開；每次建立新使用者副本 |
| `POST /api/auth/demo-sessions/feature-status` | 回傳 Demo 功能是否開啟 | 公開，供登入頁決定是否顯示入口 |
| `POST /api/auth/demo-sessions/reset` | 重建目前同一 Demo 使用者的資料並發新 JWT | 僅有效、未到期的 Demo JWT |

一般資料存取仍由既有 ownership 限制。JWT filter 會拒絕到期 Demo JWT，也會在 `app.demo.enabled=false` 時拒絕既有 Demo JWT；背景清理工作僅負責刪除到期資料。預設 TTL 為 24 小時，清理固定延遲為 900000 ms（15 分鐘）。

### 設定矩陣

| Profile／位置 | Demo 預設 | 可覆寫方式 | 其他重點 |
| --- | --- | --- | --- |
| `application.yml` | `DEMO_ENABLED` 未設定時 `false` | `DEMO_ENABLED` | TTL、cleanup 預設皆在此定義 |
| `application-prod.yml` | `true` | `DEMO_ENABLED=false` | `ddl-auto=validate`、關閉 SQL log |
| `application-test.yml` | `true` | 測試 property | `ddl-auto=create` |
| 本機 VS Code | 依 `.env` | `.env` 設 `DEMO_ENABLED=true` | 不使用 `application-local.yml` 或 local profile |

Docker Compose 目前只啟動 PostgreSQL；後端 Docker service 為註解狀態，並未承載 Demo 或 Spring profile 設定。

## 6. 資料庫 migration 與部署順序

正式 profile 使用 `ddl-auto=validate`，所以必須先完成 additive migration，才可部署使用 Demo 欄位的後端。尚未導入 Flyway；以下 SQL 是目前必須與功能版一同保存、在正式資料庫先備份後人工執行的版本。

```sql
ALTER TABLE users
  ADD COLUMN IF NOT EXISTS is_demo_session BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS demo_library VARCHAR(40),
  ADD COLUMN IF NOT EXISTS demo_expires_at TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_users_demo_expiry
  ON users (is_demo_session, demo_expires_at);
```

部署順序：備份正式資料庫 → 執行 SQL → 查驗三欄與索引存在 → 部署後端 → 確認 health 與一般登入 → 以一個 Demo session 驗證建立、Today 首批、下一批、重設與到期拒絕。不可只部署程式碼並期待 production 的 `validate` 自動建欄。

## 7. 已完成驗證與仍需注意的風險

`DemoSessionIntegrationTests` 已驗證三庫建立、資料隔離、相對日期、首批及下一批各五張、修改後重設、到期 JWT 拒絕與 cleanup 僅刪除到期資料。前端 store 測試驗證建立與重設時更新 JWT／Demo 標記；Today 前端測試與前端正式建置亦應在每次版更執行。

公開建立 Demo session 的端點目前沒有應用程式層限流。若正式環境要對不受信任網路公開 Demo，必須在反向代理、WAF 或應用程式加入 IP／速率限制與監測；否則匿名建立會消耗資料庫、bcrypt 與清理資源。這是公開上線前的必要風險處置，不應由 TTL 清理取代。

## 8. 三分鐘展示腳本與截圖驗收

共通流程：從一般登入頁選快速體驗 → 進入 Today 看首批五張 → 開一張看收藏理由、摘要與外部入口 → 點「今天想多看一批」看第二批五張 → 對一張做稍後再看 → 於暫停或封存頁說明停止回流 → 用標籤／搜尋主動探索。主動探索是補充入口，不取代 Today 回流。

### 技術與學習收藏

1. 選擇「技術與學習收藏」。
2. Today 開啟一張 Redis 或 Spring 官方文件，說明收藏理由與回流間隔。
3. 點「今天想多看一批」，確認還有五張可展示。
4. 進入「Redis 學習脈絡」Work，展示候選與已使用素材。

截圖：登入快速體驗、Today 首批、Today 下一批、技術 Card 詳情、Redis Work 素材。

### 視覺靈感與創作素材

1. 選擇「視覺靈感與創作素材」。
2. Today 查看咖啡店企劃素材，對其中一張示範稍後再看。
3. 進入「秋日咖啡店插畫」Work，說明素材是自行保存後再遇見，不是平台推薦。

截圖：登入選擇、Today、稍後再看後狀態、素材 Card、Work 候選／已採用。

### 文字片段與生活觀察

1. 選擇「文字片段與生活觀察」。
2. Today 開原創片段，展示 `content` 與收藏理由。
3. 示範封存已完成片段或暫停目前不想再遇見的筆記，並開啟「下班路上」Work。

截圖：登入選擇、Today、文字 Card 詳情、封存或暫停、下班路上 Work。

### 共通截圖／操作驗收清單

- 一般 Email／密碼登入與註冊仍為登入頁主要入口。
- Demo 僅在功能開啟時顯示，選取內容庫後才出現說明與開始按鈕。
- 三庫各自建立後有 15 張資料；首批、下一批均為五張；彼此內容與操作隔離。
- Today、Card、Tag、Work 沒有為 Demo 改寫互動規則。
- 「重新開始此展示」清除操作並恢復初始資料，不影響另一個 Demo session。
- 登出後不可回到匿名 Demo session；重新開始快速體驗會建立新資料。
- 來源連結可開啟且不在產品內嵌第三方內容。
- 不使用 AI 推薦、平台演算法控制或自動通知等未實作說法。

第一次接觸者應能在 30 秒內回答：這張 Card 為何保存、為何今天出現、現在可如何處理、三庫的內容型態差異。若無法回答，優先補強收藏理由、回流週期與 Today 文案，而不是增加卡片數量。
