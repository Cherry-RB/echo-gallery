# Repository Guidelines

## 專案結構與模組組織

Echo Gallery 採用 monorepo 架構，包含兩個應用程式：

- `echo-gallery-frontend/`：使用 Vue 3、TypeScript、Vite、Pinia 與 TanStack Query。頁面位於 `src/views/`，共用元件位於 `src/components/`，API 用戶端位於 `src/utils/api/`，共用型別位於 `src/types/`；靜態樣式與資源放在 `src/assets/` 和 `src/style.css`。
- `echo-gallery-backend/`：使用 Java 21 與 Spring Boot。正式程式碼位於 `src/main/java/com/echogallery/`，依功能分為 `card`、`tag`、`user`、`security` 等套件；設定檔位於 `src/main/resources/application.yml`。測試在 `src/test/java/` 下採用相同套件結構。
- `docs/`：存放產品決策、環境設定筆記與除錯紀錄；僅在任務需要相關背景時選擇性閱讀。
- `docker-compose.yml`：定義本機 PostgreSQL 服務。

## 產品定位與文件使用原則

Echo Gallery 的核心定位是個人化內容回流工具，目標是讓有價值的收藏、外部內容與個人想法依使用者設定的節奏重新出現，而不是作為完整內容備份或通用文檔儲存庫。

文件用途如下：

- `README.md`：提供專案簡介、技術架構與基本啟動方式。
- `docs/ECHO_GALLERY_MVP.md`：記錄產品理念、MVP 規劃與需求演進，其中可能包含已被後續實作取代的歷史決策。
- `docs/` 其他文件：主要保存開發過程、環境筆記、除錯紀錄與事故復盤，不一定代表目前實作。

代理不得預設完整讀取 `docs/`。只有在使用者明確要求、任務涉及產品定位、需求語意、架構取捨或歷史決策，或現有程式碼不足以說明設計原因時，才選擇性閱讀相關文件或章節。

判斷目前系統行為時，以現有原始碼、設定檔、型別定義與測試為主要依據。若需求文件、README 與目前實作不一致，代理應指出差異並向使用者確認，不得自行以歷史文件覆蓋目前行為，也不得因此擴大修改範圍。

## 建置、測試與開發指令

請從各指令指定的應用程式目錄執行：

```powershell
docker compose up -d postgres                   # 啟動 PostgreSQL
cd echo-gallery-backend; .\gradlew.bat bootRun  # 啟動後端 API
cd echo-gallery-backend; .\gradlew.bat test     # 執行後端測試
cd echo-gallery-backend; .\gradlew.bat build    # 編譯、測試並封裝
cd echo-gallery-frontend; npm ci                 # 依鎖定版本安裝相依套件
cd echo-gallery-frontend; npm run dev            # 啟動 Vite 開發伺服器
cd echo-gallery-frontend; npm run build          # 執行型別檢查與正式環境建置
cd echo-gallery-frontend; npm run preview        # 預覽建置完成的前端
```

`.env.example` 僅供設定參考；代理不得建立或修改 `.env`。

## 程式風格與命名慣例

遵循 `.editorconfig`：使用 UTF-8、LF 換行、檔尾換行，並移除行尾空白。Vue、TypeScript、JSON、CSS 與 YAML 使用 2 個空格縮排；Java 與 Gradle 檔案使用 4 個空格。Vue 元件及頁面使用 PascalCase（如 `CardDetail.vue`），composable 使用 `use` 前綴（如 `useTags.ts`），Java 型別使用 PascalCase。後端套件名稱使用小寫，並依功能劃分。

目前未設定獨立的格式化或 lint 工具；請遵循相鄰程式碼風格，並在提交前確認 `npm run build` 通過。

## 測試規範

後端透過 Gradle 使用 JUnit Platform。測試類別命名為 `*Tests.java`，並放在 `src/test/java` 下對應的套件中。行為異動應加入聚焦的 service 或 controller 測試。前端目前沒有測試指令；請以 TypeScript 正式環境建置及瀏覽器手動操作驗證變更，特別注意響應式版面與驗證流程。

## Commit 與 Pull Request 規範

使用本儲存庫的 Conventional Commits 格式：`type(scope): description`，其中 `type` 與 `scope` 使用英文、description 使用中文且結尾不加句號，例如 `feat(tag): 新增標籤選擇器` 或 `fix(card): 正確處理封存狀態`。每個 commit 僅包含一項邏輯變更，分支名稱使用 `feat/<name>`、`fix/<name>` 或 `docs/<name>` 等格式。

Pull Request 應說明問題與解決方式、列出驗證指令、連結相關 issue，並指出設定或資料庫變更。可見的 UI 異動需附上截圖。行為或設定流程改變時，代理應評估相關文件是否需要同步更新並提出建議；若文件修改不在本次需求範圍內，須經使用者確認後才執行。

### Git 推版協助

當使用者輸入「準備推版」、「產生 commit 建議」或其他語意相同的要求時，代理應執行以下唯讀檢查：

1. 使用 `git status` 確認工作區狀態。
2. 使用 `git diff --staged` 分析本次已暫存的變更。
3. 使用 `git diff --staged --check` 檢查空白與格式問題。
4. 檢查 staged 檔案是否包含 `.env`、憑證、密鑰或其他敏感設定；不得讀取 `credentials/` 或 `secrets/` 目錄內容。
5. 僅以 staged 內容產生 commit 建議，不得將 unstaged 或 untracked 檔案內容混入本次 commit 說明；可以提醒使用者另有未暫存或未追蹤檔案。
6. 若沒有 staged 變更，應明確告知使用者，不得根據 unstaged 內容假設本次要提交的範圍。
7. 以中文逐項說明 staged 檔案的修改內容與原因。
8. 評估 staged 內容是否包含多項獨立邏輯；必要時提出拆分 commit 的建議。
9. 提供符合 `type(scope): 中文描述` 格式的 Conventional Commit 訊息。
10. 回報已執行、通過、失敗及尚未執行的驗證項目。
11. 未經使用者明確要求，不得執行 `git add`、`git commit`、`git push` 或建立 Pull Request。

Commit type 使用：

- `feat`：新增功能。
- `fix`：錯誤修正。
- `docs`：文件變更。
- `refactor`：不改變外部行為的重構。
- `test`：測試變更。
- `style`：不影響邏輯的格式調整。
- `perf`：效能改善。
- `chore`：建置、工具或維護工作。

## 安全規則

- 禁止修改 `.env` 檔案。
- 禁止讀取 `credentials/` 或 `secrets/` 目錄。
- 修改 API endpoint 前，必須先向使用者說明修改計畫。
- 工作區中的既有修改視為使用者所有。代理不得覆蓋、還原或順手整理與本次任務無關的變更；若修改範圍重疊，應先說明風險。
- Git 寫入操作遵循「Git 推版協助」規範，預設由使用者自行檢查並提交變更。

## 工作流程

1. 修改前先分析現有程式碼，並向使用者說明對現況與需求的理解。
2. 提出具體修改計畫，等待使用者確認後才開始修改。
3. 修改後依影響範圍執行驗證：
   - 修改前端程式碼：在 `echo-gallery-frontend/` 執行 `npm run build`。
   - 修改後端程式碼：在 `echo-gallery-backend/` 執行 `.\gradlew.bat test`。
   - 同時修改前後端或 API contract：兩者皆執行。
   - 僅修改文件：執行適用的文件、編碼與差異檢查，不需執行應用程式測試。
   - 可見的 UI 修改：除建置外，列出需要手動驗證的操作與響應式尺寸。
   - 若測試因環境前置條件失敗，應回報實際原因，不得為了讓測試通過而擅自修改 `.env`。
4. 回報修改的檔案清單、每項修改的原因、測試結果，以及任何尚未驗證的部分。

## 任務復盤與知識維護

每次完成造成 repository 變更的任務後，代理必須評估本次工作是否產生可重複使用的專案知識，並依下列原則提出建議：

- 適用於多數任務且長期有效的協作規則，建議更新 `AGENTS.md`。
- 涉及架構決策、除錯紀錄、環境設定或操作背景，建議建立或更新 `docs/`。
- 具有固定輸入、輸出及執行步驟，且預期會重複使用的專門流程，建議建立 Skill。
- 僅適用於單次需求的細節，不建立長期文件。

代理應在修改型任務的完成報告中列出「知識維護評估」，分別說明 `AGENTS.md`、Skills 與 `docs/` 是否需要調整；若沒有值得沉澱的內容，應明確回報無需調整。純問答、說明、狀態查詢及未修改 repository 的分析任務，除非發現明確問題，否則不必輸出此評估。

當使用者輸入「執行任務復盤」、「評估知識維護」或其他語意相同的要求時，即使當前對話尚未修改 repository，代理也應針對使用者指定的任務或可取得的最近一次變更進行評估；若缺少必要上下文，應先說明可取得的依據與限制，不得臆測已遺失的任務內容。

代理不得未經使用者確認，自動新增或修改 `AGENTS.md`、Skills 或非本次需求範圍內的文件。

## 語言偏好

- 所有對使用者的回覆使用中文。
- 新增或修改的程式碼註解使用中文。
