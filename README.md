# README：Echo Gallery

Echo Gallery 是一個旨在解決「收藏死亡」問題的個人化內容回流系統。它讓你能夠建立外部連結卡片或自建筆記，並透過自定義的回流頻率，讓有價值的內容在未來適時地回到你的瀑布流中，重新與你相遇。

目前以三種內容角色串起使用情境：卡片保存值得再遇見的內容；實驗場記下試法與發現；奇珍展覽館收錄已完成成果，追蹤檢定嘗試及後續價值產出。三者可獨立使用，來源關聯選填，不要求走完固定流程，也不以等級為個人評分。

主導覽包含總覽、今日回流、全部卡片、實驗場與奇珍展覽館。議事廳已退出主導覽與總覽，舊功能及資料仍保留，並非已完整刪除。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-red)](https://openjdk.org/)
[![Vue](https://img.shields.io/badge/Vue-3.x-green)](https://vuejs.org/)

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
在專案根目錄建立 `.env` 檔案（可參考 `.env.example`）：
```bash
# 範例內容
DB_PASSWORD=your_secure_password
JWT_SECRET=your_secret_key
```

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

既有資料庫部署新版前，先核對 schema 與備份，再由維護者審閱並手動執行所需 SQL。奇珍資料表的部署狀態與規則見[實作追蹤](docs/27_奇珍展覽館需求設計與實作追蹤.md#6-資料庫與-demo-安全)；不要以啟動服務觸發 ORM 自動改表代替部署確認。SQL 草稿不納入 Git，程式測試通過不代表目標資料庫已完成升級。

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
* [開發紀錄索引](docs/00_docs-index.md)：其他歷史與技術文件；不直接視為現行規格。
