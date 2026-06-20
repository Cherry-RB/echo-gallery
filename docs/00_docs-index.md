# 📂 EchoGallery 核心技術與架構文檔中心目錄 (docs/ Index)

本目錄收錄 EchoGallery 全端專案（Spring Boot 4.x + Java 21 + Vue 3 + PostgreSQL）在架構設計、開發踩坑、底層原理剖析及產品規劃上的**精華技術資產**。所有文件均已去除實體敏感憑證，轉為生產級的環境變數與通用規範，供團隊與個人未來隨時覆盤、部署移轉及面試使用。

---

## 📋 技術精華與規格文件清單

### 1. [01_專案建置環境與版本.md](./01_專案建置環境與版本.md)
* **文件屬性**：開發環境初始化、IDE 基礎設施調優、核心異常應急處置手冊。
* **內容摘要**：記錄專案啟動初期，整合開發環境（IDE）與資料庫底層核心衝突的解決方案。包含 VS Code 與 JVM 垃圾回收（GC）最佳化參數的對齊。
* **核心重點**：
    * **IDE 調優**：針對 VS Code 本機環境，配置 `settings.json` 中的 `java.sharedIndexes.location`（D槽轉移）及硬核 JVM 優化參數（`-XX:+UseParallelGC -XX:GCTimeRatio=4` 等）。
    * **LSP 崩潰處置**：剖析 VS Code Task Type 'Java' 遺失與語言伺服器協議（LSP）中斷的原理，並確立 `Clean Java Language Server Workspace` 的標準 SOP。
    * **CLI 終極擊殺令**：詳細還原 Windows 系統管理員權限阻斷導致無法透過 `Stop-Service` 停止 `postgresql-x64-17` 的架構原理，提供利用網路通訊埠追蹤技術鎖定並以 `taskkill /F /PID` 強制終結霸佔 `5432` Port 行程的純終端機解決方案。
    * **DB 選型評估**：對比 SQL Server 本地與雲端 Neon/Supabase PostgreSQL 永久免費額度（Free Tier）的維運成本與承載力。

### 2. [02_建立與連線DB與啟動應用.md](./02_建立與連線DB與啟動應用.md)
* **文件屬性**：容器化基礎設施建立、本機網路拓撲、Spring 現代生命週期機制。
* **內容摘要**：圍繞於 Docker PostgreSQL 部署、Windows 本機通訊埠衝突及應用程式自動化連線機制的技術全紀錄。
* **核心重點**：
    * **Docker 選型優勢**：分析 MVP 初期頻繁修改資料表結構（Schema）時，首選 `postgres:16-alpine` 鏡像所帶來的「高容錯、3秒極速重置與系統乾淨度」優勢。
    * **連線認證排除**：解決密碼正確卻拋出 `password authentication failed for user "test_user"` 的阻斷異常。深度解析 WSL2 虛擬機器、Docker Daemon 網絡映射與 Windows 本機實體殘留服務搶佔 `5432` 埠口的底層衝突。
    * **Spring Boot 自動化生命週期鉤子**：解密 Spring Boot 3.1+ / 4.x 引入的 `spring-boot-docker-compose` 相依套件。說明應用程式啟動時如何在 ApplicationContext 初始化階段自動掃描 `docker-compose.yml`、呼叫 Docker API（暗渡陳倉執行 `up -d`）、動態注入屬性（Dynamic Property Source）覆蓋本地設定，以及在關閉時觸發 Shutdown Hook 停止容器的硬核邏輯。

### 3. [03_CROS機制學習筆記.md](./03_CROS機制學習筆記.md)
* **文件屬性**：Web 安全機制、同源政策、開發環境代理伺服器原理。
* **內容摘要**：梳理瀏覽器安全防線、後端異常造成的「假 CORS 錯誤」以及前端開發環境（Vite）的繞過與代理機制。
* **核心重點**：
    * **同源政策（Same-Origin Policy）**：定義瀏覽器核心安全機制中的協定（Protocol）、網域（Domain）、連接埠（Port）三者對齊之必要性。
    * **後端異常引發的 CORS 假象**：深度剖析當後端程式碼發生 `NoClassDefFoundError`（如 Jackson 的 `ObjectMapper` 配置崩潰或套件結構出錯）時，Java 虛擬機生命週期在中途被阻斷，導致響應未能走到 CORS 過濾器即拋出 500 錯誤。由於回傳時**缺少 CORS 標頭**，引發瀏覽器誤報 CORS 的排查邏輯。
    * **Vite Dev Proxy 原理**：說明伺服器與伺服器之間（TCP/IP 連線）不受同源政策限制的本質。詳解 Vite 在本地啟動基於 Node.js 的網頁伺服器（預設 `localhost:5173`）後，如何透過 `vite.config.ts` 中的 `server.proxy` 配置，將 `/api` 請求短路代理至後端 `8080`，在開發階段完美繞過瀏覽器攔截。

### 4. [04_CROS,BatchSize,權控機制.md](./04_CROS,BatchSize,權控機制.md)
* **文件屬性**：系統權限控制架構、效能優化、Spring Security 門禁生命週期。
* **內容摘要**：以拉高視角的白話架構邏輯，釐清 CORS 運作本質、Spring Security 6 安全過濾鏈順序，以及 JPA/Hibernate 的 BatchSize 效能調優。
* **核心重點**：
    * **瀏覽器的外交保護令**：用直覺圖解與觀念釐清前端（5173）與後端（8080）跨來源存取時，後端如何發放「白名單通行證」。
    * **Spring Security 6 過濾器鏈順序**：詳解為什麼在 `SecurityConfig` 配置後，不需在 `HttpRequests` 額外針對 `OPTIONS` 預檢請求寫 `.permitAll()`。核心原因在於 `CorsFilter` 的順序極高（排在最前線，甚至在 `JwtAuthenticationFilter` 之前），一旦預檢請求匹配白名單，過濾器會直接**短路（Short-circuit）返回 200 OK**，根本不會走到後續的認證門禁。
    * **BatchSize 效能調優**：針對資料庫查詢的 **N+1 問題** 進行底層邏輯拆解，說明如何利用 `BatchSize` 讓 JPA 在抓取關聯資料時，將個別的 SQL 查詢打包成批次處理，降低資料庫通訊往返成本。

### 5. [05_前端TanStack_Query引用.md](./05_前端TanStack_Query引用.md)
* **文件屬性**：前端狀態管理、非同步資料流、樂觀更新（Optimistic Updates）、快取架構。
* **內容摘要**：EchoGallery 前端資料流的「核心快取引擎」，詳細紀錄從路由狀態保存到樂觀更新的手術刀式操作。
* **核心重點**：
    * **選型與場景**：為什麼選擇 TanStack Query 來解決詳情頁與瀑布流的資料拉鋸。
    * **無限滾動實戰**：`useInfiniteQuery` 對二維分頁結構的驅動機制。
    * **狀態同步手術**：透過 `useCardStatus.ts` 封裝樂觀更新與 `onMutate` 快照還原邏輯、優化管理全域看板資料。

### 6. [06_推版與部署方案考量.md](./06_推版與部署方案考量.md)
* **文件屬性**：DevOps、版本控制戰略、全端資安防線、十二要素應用原則（Twelve-Factor App）。
* **內容摘要**：整合先前所有討論與 Code Review 修正意見，為 EchoGallery 量身打造的**終極完整版技術架構與安全部署白皮書**。
* **核心重點**：
    * **倉庫架構評估**：深度對比單一倉庫（Monorepo）與獨立倉庫（Split Repos）在開發便利性、代碼一致性、CI/CD 自動化流水線設計上的優劣，確立專案初期的 Monorepo 戰略。
    * **專案內務整理（Housekeeping）**：全面盤點前後端初始化檔案，清理無價值罐頭文字（如 `HELP.md`），將 `docker-compose.yml` 提升至根目錄作為全站基礎設施藍圖，並確立 Gradle Wrapper 腳本（`gradlew`、`gradlew.bat`）在 CI/CD 與跨設備編譯中的保留必要性。
    * **全域 Git 防線**：設計高純度的全域 `.gitignore` 藍圖，精準死守 `pgdata/`、`*.log`、`.env`、前後端依賴與兩大 IDE（IntelliJ/VS Code）環境雜訊。
    * **雲端三大隱性坑洞預警**：前瞻性指出未來走向雲端部署前必須處理的「CORS 動態化、資料庫連線池調優（HikariCP）、敏感資訊外部化（環境變數管理）」架構設計。

### 6. [ECHO_GALLERY_MVP.md](./ECHO_GALLERY_MVP.md)
* **文件屬性**：產品範疇界定書、業務邏輯設計、MVP 開發路徑。
* **內容摘要**：定義 EchoGallery（回音畫廊 / EchoGarden）個人化內容推播與複習工具的產品邊界、解決痛點與核心功能細節。
* **核心重點**：
    * **核心痛點解決**：主打解決「收藏死亡」（在各平台轉存後不再回看）及「演算法不可控」（注意力被動被平台支配）兩大問題，讓有價值的連結與自建筆記卡片重回個人視線。
    * **MVP 第一版邊界**：界定基礎卡片管理、標籤分類、熱度紀錄與愛心冷卻機制。
    * **全端瀑布流與推播核心邏輯（討論中項目）**：
        * 卡片欄位行為定義：探討 `card.isArchived`（封存）之定義。
        * 時間動態推播：針對 `intervalDays`、更新 `lastShownAt` 與 `nextShowAt` 的自動化行為進行邏輯釐清（拒絕設計繁瑣的「已回顧」按鈕，改採進入瀑布流自動更新）。
        * 批次作業規劃：探討同一天重複曝光對使用者體驗的影響，評估未來是否使用**批次作業（Batch Job）**統一處理到期卡片的日期更新。

### 7. [07_數位編碼與資料表示原理.md](./07_數位編碼與資料表示原理.md)
* **文件屬性**：計算機科學基礎、資料表示架構、編碼轉換原理。
* **內容摘要**：釐清從底層硬體 (Bits) 到人類可讀 (ASCII) 的轉譯規範。
* **核心重點**：
    * **數位倉庫比喻**：定義了硬體計量 (Bit/Byte)、表示層 (Hex/Decimal)、翻譯層 (ASCII) 的三層模型。
    * **編碼與安全的數學邏輯**：詳解 Base64 編碼對資料長度的影響，為系統中的金鑰、Token 長度定義提供理論依據。
    * **除錯視角**：提供工程師在處理資料解析、字元編碼異常時的標準診斷邏輯（從機器視角回推到意義視角）。
