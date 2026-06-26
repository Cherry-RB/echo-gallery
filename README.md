# README：Echo Gallery

Echo Gallery 是一個旨在解決「收藏死亡」問題的個人化內容回流系統。它讓你能夠建立外部連結卡片或自建筆記，並透過自定義的回流頻率，讓有價值的內容在未來適時地回到你的瀑布流中，重新與你相遇。

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
| **狀態管理** | TanStack Query |

---

## 🚀 快速開始 (Getting Started)

本專案採用 Monorepo 架構，包含後端服務與前端介面。

### 前置需求
* [Docker Desktop](https://www.docker.com/products/docker-desktop/) (確保 Docker Daemon 已啟動，用於運行 PostgreSQL 與後端環境)
* [Java 21 JDK](https://adoptium.net/)
* [Node.js 20+](https://nodejs.org/)

### 環境設定
在專案根目錄建立 `.env` 檔案（可參考 `.env.example`）：
```bash
# 範例內容
DB_PASSWORD=your_secure_password
JWT_SECRET=your_secret_key
```

### 啟動流程

#### 步驟 1: 啟動基礎設施 (Docker)
在專案根目錄下，使用 Docker Compose 啟動資料庫與後端服務環境：

```bash
docker-compose up -d

```

#### 步驟 2: 啟動後端

進入後端目錄並編譯執行：

```bash
cd echo-gallery-backend
./gradlew bootRun

```

#### 步驟 3: 啟動前端

開啟新終端機，進入前端目錄：

```bash
cd echo-gallery-frontend
npm install
npm run dev

```

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

* **產品設計與 MVP 規劃**：docs/ECHO_GALLERY_MVP.md
* 開發紀錄文件：docs/00_docs-index.md
