# EchoGallery 技術文檔索引 (Documentation Index)

本目錄收錄 EchoGallery 專案之技術架構、開發紀錄、除錯指南與產品規劃，便於團隊協作、後續維護及技術移轉。

## 狀態與說明

* **目前狀態**: 持續維護中 (Active)
* **後續待辦**: 確保所有文件鏈結有效，並依據專案迭代更新索引內容。

---

## 📋 技術規格與開發文件清單

### 1. [01_專案建置環境與版本.md](./01_專案建置環境與版本.md)

* **屬性**：環境初始化與除錯。
* **內容**：專案開發環境初始化、IDE 設定與底層異常排除。
* **重點**：
  * IDE 設定優化（包含索引位置配置與 JVM 參數調整）。
  * 語言伺服器 (LSP) 異常處理標準作業流程。
  * Windows 環境下通訊埠 (Port 5432) 衝突排除與行程終止方式。
  * 資料庫選型比較與維運成本評估。

### 2. [02_建立與連線DB與啟動應用.md](./02_建立與連線DB與啟動應用.md)

* **屬性**：基礎設施與生命週期。
* **內容**：容器化部署 (Docker)、網路通訊配置與 Spring Boot 啟動機制。
* **重點**：
  * PostgreSQL 容器化部署優勢。
  * 網路認證異常排除與虛擬化環境連線設定。
  * Spring Boot 應用程式與 Docker Compose 的整合機制與啟動生命週期管理。

### 3. [03_CROS機制學習筆記.md](./03_CROS機制學習筆記.md)

* **屬性**：網路安全與開發工具。
* **內容**：瀏覽器同源政策 (Same-Origin Policy) 與前端開發代理機制。
* **重點**：
  * 同源政策定義與網域限制說明。
  * 後端執行異常導致 CORS 誤判之排查邏輯。
  * Vite 開發伺服器 (Dev Proxy) 之配置與運作原理。

### 4. [04_CROS,BatchSize,權控機制.md](./04_CROS,BatchSize,權控機制.md)

* **屬性**：架構設計與效能調優。
* **內容**：安全性過濾機制與資料庫查詢效能優化。
* **重點**：
  * Spring Security 過濾器鏈 (Filter Chain) 運作順序。
  * CORS 預檢請求 (Preflight Request) 處理機制。
  * JPA / Hibernate N+1 查詢問題與 BatchSize 優化配置。

### 5. [05_前端TanStack_Query引用.md](./05_前端TanStack_Query引用.md)

* **屬性**：狀態管理。
* **內容**：前端資料快取與非同步狀態管理機制。
* **重點**：
  * 資料流管理選型分析。
  * `useInfiniteQuery` 之分頁處理機制。
  * 樂觀更新 (Optimistic Updates) 與狀態一致性維護。

### 6. [06_推版與部署方案考量.md](./06_推版與部署方案考量.md)

* **屬性**：DevOps 與配置管理。
* **內容**：專案結構、版本控制與部署策略規劃。
* **重點**：
  * 專案儲存庫架構（Monorepo vs. Multi-repo）評估。
  * 環境變數管理與敏感資訊外部化策略。
  * Docker Compose 基礎設施配置與 CI/CD 準備工作。

### 7. [07_數位編碼與資料表示原理.md](./07_數位編碼與資料表示原理.md)


* **屬性**：計算機基礎。
* **內容**：底層數位編碼與資料轉譯規範。
* **重點**：
  * 位元 (Bit/Byte)、表示層 (Hex/Decimal) 與 ASCII 編碼轉換模型。
  * Base64 編碼原理及其對系統設計（金鑰長度）之影響。
  * 診斷資料解析與編碼異常之標準流程。

### 8. [08_Git 版本控制與開發流程.md](./08_GIT_WORKFLOW.md)

* **屬性**：版本控制。
* **內容**：團隊開發工作流與規範。
* **重點**：
  * 分支命名規範與合併策略。
  * 標準化 Commit 訊息格式 (Conventional Commits)。
  * Git 操作紀錄、異動查詢與版本復原（Revert/Reset/Stash）標準處理機制。

### 9. [09_RWD失效與CDN快取除錯記錄.md](./09_RWD失效與CDN快取除錯記錄.md)

* **屬性**：前端除錯與部署基礎設施（Postmortem）。
* **內容**：正式環境真機 RWD 版面失效之完整排查歷程與根本原因分析。
* **重點**：
  * Vite 正式建置時 CSS 壓縮工具自動套用 Media Query Range Syntax（`width<=1200px`），導致 Safari 16.4 以下版本（含 iOS 16.3.1）無法辨識、整段 `@media` 規則靜默失效。
  * Render 靜態網站背後 Cloudflare CDN 對 `index.html` 的預設快取策略（`s-maxage=300`），導致不同使用者於快取過期前取得不一致版本，干擾排查判斷。
  * 自訂 Response Header 路徑比對需以瀏覽器實際 Request URL（如 `/`）為準，而非僅設定實體檔名（如 `/index.html`）。
  * 無遠端除錯設備（Mac / Android）情況下，透過在頁面內植入臨時診斷元件（讀取 `window.matchMedia`、`getComputedStyle`、`document.styleSheets`）逐層縮小問題範圍之排查手法。
  * 完整的假設驗證時間軸與可遷移之除錯方法論總結。

### 10. [10_今日看板時區判斷錯誤除錯記錄.md](./10_今日看板時區判斷錯誤除錯記錄.md)

* **屬性**：後端除錯與資料庫維運（Postmortem）。
* **內容**：TODAY 看板卡片無法於台灣時間 00:00 準時刷新之完整排查歷程、修復設計與既有資料修復紀錄。
* **重點**：
  * 手動測試 SQL 使用 `CURRENT_DATE` 時，受 DB session timezone（Supabase 預設為 `UTC`）影響，導致「今日」判斷基準比台北實際午夜晚 8 小時。
  * 應用層查詢邏輯 `nextShowAt <= now()` 屬於精確到秒的絕對時刻比較，而建卡邏輯 `ZonedDateTime.now().plusDays(N)` 保留了建立當下的時分秒，導致卡片延遲到「建立當下的時分秒」才刷新，而非台北午夜。
  * `timestamptz`／`ZonedDateTime` 型別本身無須更動（本質為絕對時刻儲存），問題根源在應用層計算「今天」時所選用的時區基準與比較粒度，而非欄位型別。
  * 修復設計：拆分「查詢用排他上界」（`getStartOfTomorrowTaipei()`）與「建卡/回流起算基準」（`getStartOfTodayTaipei()`）兩個獨立時區錨點方法，避免共用同一方法時因內建偏移量疊加造成 off-by-one。
  * 既有資料 migration SQL（`date_trunc` + `AT TIME ZONE 'Asia/Taipei'`）設計與驗證方式；記錄兩次操作誤判：UTC 顯示值誤認為未生效、Supabase SQL Editor 分次執行導致 transaction 未真正 commit。
  * 附帶記錄刪除卡片時觸發外鍵約束（`card_tags` 中介表）之排查與處理方式。
