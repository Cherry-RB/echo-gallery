# README：Echo Gallery

Echo Gallery 是一個旨在解決「收藏死亡」問題的個人化內容回流系統。它讓你能夠建立外部連結卡片或自建筆記，並透過自定義的回流頻率，讓有價值的內容在未來適時地回到你的瀑布流中，重新與你相遇。

目前以四種內容角色串起使用情境：卡片保存值得再遇見的內容；實驗場透過探索線記下行動與發現；創作台持續製作作品；展覽館收錄已完成成果，追蹤試煉嘗試及後續價值產出。四者可獨立使用，來源關聯選填，不要求走完固定流程，也不以等級為個人評分。

目標追蹤是選用的橫向功能，可將既有內容納入一段期間的方向，透過相關卡片閱讀、站外補記及復盤調整做法。目標到期不會自動判定完成；目前提醒為站內提示。

主導覽包含總覽、目標看板、今日回流、全部卡片、實驗場、創作台、展覽館、標籤管理與卡片查詢。總覽分為「現在行動／近期回顧／回流狀態」；議事廳已退出主導覽與總覽，舊功能及資料仍保留，並非已完整刪除。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-red)](https://openjdk.org/)
[![Vue](https://img.shields.io/badge/Vue-3.x-green)](https://vuejs.org/)

---

## ✨ 主要功能 (Features)

* **卡片與回流**：建立連結或筆記卡片，自訂回流頻率，透過今日限量批次重新遇見內容；全部卡片提供更新、熱門、隨機起點、封存及稍後再看檢視。
* **實驗場與探索線**：設定下一步、記下發現，再依需要整理為歷史或卡片；卡片可以用於作品，探索與創作保留各自脈絡。
* **創作台**：作品分為「主專注／構思／已寫稿件」三個純文字區域，提供獨立自動儲存、衝突保護、複製與文字下載；選擇外部文件來源時，站內三區仍可使用。
* **展覽館**：保存已完成成果、試煉嘗試與價值產出；關聯作品可依主要稿件來源閱讀站內已寫稿件，或開啟外部文件。
* **目標看板與復盤**：選取追蹤來源、固定目標於總覽、分批閱讀相關卡片，並補記站外進展與保存復盤。未完成自動保存，完成後可直接編輯同一筆復盤。

目標指標依已開放能力及歷史涵蓋範圍呈現，不以未捕捉的資料推測完整活動；功能規則、部署與待驗收項目見下方設計文件。

---

## 🛠️ 技術選型 (Tech Stack)

本專案採用全端架構開發，模組技術選型如下：

| 領域 | 技術與工具 |
| :--- | :--- |
| **後端 (Backend)** | Java 21, Spring Boot 4.x, Spring Security |
| **前端 (Frontend)** | Vue 3, TypeScript, Vite |
| **資料庫 (DB)** | PostgreSQL |
| **容器化 (Infra)** | Docker, Docker Compose |
| **狀態管理** | TanStack Query（伺服器資料）、Pinia（本機狀態） |

---

## 🚀 快速開始 (Getting Started)

本專案採用 Monorepo 架構，包含後端服務與前端介面。

### 前置需求
* [Docker Desktop](https://www.docker.com/products/docker-desktop/) (確保 Docker Daemon 已啟動，用於運行 PostgreSQL 與後端環境)
* [Java 21 JDK](https://adoptium.net/)
* [Node.js](https://nodejs.org/)（依目前 Vite 的需求：20.x 至少 20.19，或 22.12 以上；建議使用符合此條件的 LTS 版本）

### 環境設定
在專案根目錄建立 `.env` 檔案（可參考 `.env.example`），資料庫設定須與實際使用的 PostgreSQL 一致：

```bash
# 範例內容
POSTGRES_USER=echo_user
POSTGRES_PASSWORD=your_secure_password
POSTGRES_DB=echogallery
DB_USERNAME=echo_user
DB_PASSWORD=your_secure_password
JWT_SECRET=replace_with_a_random_secret_at_least_32_bytes
```

以上密碼與 JWT 值須自行替換。Docker Compose 會讀取根目錄 `.env` 來設定 PostgreSQL；直接用 IDE 或 Gradle 啟動後端時，須另外將 `DB_USERNAME`、`DB_PASSWORD` 與 `JWT_SECRET` 設為該程序的環境變數，Gradle 不會自動載入根目錄 `.env`。未設定 `DB_USERNAME`／`DB_PASSWORD` 時，後端會依序使用 `POSTGRES_USER`／`POSTGRES_PASSWORD`。

後端連線網址預設為 `jdbc:postgresql://localhost:5432/echogallery`；資料庫名稱或位置不同時，另外設定 `JDBC_DATABASE_URL`。正式環境的前端來源可用 `FRONTEND_URL` 設定；不要提交實際環境變數或密鑰。

### 啟動流程

#### 步驟 1: 啟動基礎設施 (Docker)
在專案根目錄下，先啟動 PostgreSQL；以下流程由本機 Gradle 啟動後端：

```bash
docker compose up -d postgres

```

啟動完成後，如果要關閉：

```bash
docker compose down
```

#### 步驟 2: 啟動後端

進入後端目錄並編譯執行：

首次建立資料庫或既有資料庫部署新版前，先核對 schema 與備份，再由維護者審閱並手動執行所需 SQL。目前本機與正式設定皆使用 `ddl-auto: validate`，只啟動 PostgreSQL 不會自動建立完整應用資料表。展覽館、目標與作品三區的資料結構須配合前後端版本；部署邊界見[展覽館追蹤](docs/27_奇珍展覽館需求設計與實作追蹤.md#6-資料庫與-demo-安全)、[目標實作規劃](docs/32_目標追蹤與目標看板實作規劃.md)及[作品純文字設計](docs/33_作品純文字工作區設計說明.md)。SQL 草稿不納入 Git，程式測試通過不代表目標資料庫已完成升級。

```bash
cd echo-gallery-backend
./gradlew bootRun

```

Windows PowerShell 使用 `.\gradlew.bat bootRun`。

#### 步驟 3: 啟動前端

開啟新終端機，進入前端目錄：

```bash
cd echo-gallery-frontend
npm ci
npm run dev

```

### 驗證

前端於 `echo-gallery-frontend/` 執行 `npm run test:run` 與 `npm run build`；後端於 `echo-gallery-backend/` 執行 `.\gradlew.bat test`（其他 shell 使用 `./gradlew test`）。後端整合測試使用 Testcontainers，須有可用的 Docker。可見 UI 異動仍需另外驗收瀏覽器效果、窄螢幕與鍵盤操作。

---

## 🏗️ 專案架構 (Project Structure)

本專案遵循 Monorepo 設計，結構清晰：

```text
echo-gallery/
├── docs/                 # 技術文件與開發紀錄
│   ├── ECHO_GALLERY_MVP.md # MVP 產品規劃與需求設計
│   └── 00_docs-index.md    # 技術文件索引
├── echo-gallery-backend/ # Spring Boot 後端 API 服務
├── echo-gallery-frontend/# Vue 3 + TypeScript 前端應用
├── docker-compose.yml    # 環境基礎設施編排
└── README.md             # 專案入口文件

```

---

## 設計文件與開發紀錄

若需深入了解開發動機或 MVP 規劃等更多說明文件，請參閱以下文檔：

* [產品設計與 MVP 規劃](docs/ECHO_GALLERY_MVP.md)：定位、需求初衷與決策演進。
* [設計系統](docs/DESIGN_SYSTEM.md)：全專案的排版、視覺及互動原則。
* [奇珍展覽館需求與實作追蹤](docs/27_奇珍展覽館需求設計與實作追蹤.md)：現行規則、交付與部署邊界。
* [探索線與活動整理設計](docs/30_探索線與土壤移除需求設計.md)：下一步、行動、發現、歷次整理與卡片素材。
* [目標追蹤與復盤需求](docs/31_目標追蹤與復盤及目標看板需求說明.md)及[實作規劃](docs/32_目標追蹤與目標看板實作規劃.md)：目標來源、看板、期間觀測、復盤與實際交付範圍。
* [作品純文字工作區設計](docs/33_作品純文字工作區設計說明.md)：三區編輯、外部文件來源、資料結構與驗收狀態。
* [開發紀錄索引](docs/00_docs-index.md)：其他歷史與技術文件；不直接視為現行規格。
