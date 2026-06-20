# README：Echo Gallery

Echo Gallery 是一個旨在解決「收藏死亡」問題的個人化內容回流系統。它讓你能夠建立外部連結卡片或自建筆記，並透過自定義的推播頻率與演算法，讓有價值的內容在未來適時地回到你的瀑布流中，重新與你相遇。

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-21-red)](https://openjdk.org/)
[![Vue](https://img.shields.io/badge/Vue-3.x-green)](https://vuejs.org/)

---

## 🛠️ 技術選型 (Tech Stack)

Echo Gallery 採用現代化全端技術架構，追求穩定性與開發效率。

| 領域 | 技術與工具 | 說明 |
| :--- | :--- | :--- |
| **後端 (Backend)** | Java 21, Spring Boot 4.x, Spring Security | 企業級後端核心，高安全性與擴充性 |
| **前端 (Frontend)** | Vue 3, TypeScript, Vite | 高效渲染與強型別開發體驗 |
| **資料庫 (DB)** | PostgreSQL | 關係型資料結構，支援複雜查詢與關係 |
| **容器化 (Infra)** | Docker, Docker Compose | 環境一致性部署，一鍵啟動基礎設施 |
| **狀態管理** | TanStack Query | 前端非同步資料流與快取管理 |

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
├── docs/                 # 核心技術資產與知識庫 (詳見下方)
├── echo-gallery-backend/ # Spring Boot 後端 API 服務
├── echo-gallery-frontend/# Vue 3 + TypeScript 前端應用
├── docker-compose.yml    # 全站基礎設施編排
└── README.md             # 專案入口文件

```

---

## 📚 知識資產中心 (Knowledge Base)

若您需要深入了解專案的技術選型、開發踩坑紀錄、架構設計思考或 MVP 規劃，請參閱內部的文檔目錄：

👉 進入技術文檔中心：docs/00_docs-index.md

這裡記錄了：

* **開發環境與 IDE 配置**：解決 LSP 與 Java 環境衝突。
* **安全性機制**：CORS 機制、Spring Security 過濾鏈剖析。
* **效能調優**：JPA BatchSize 與前端狀態管理 (TanStack Query)。
* **產品規劃**：MVP 邊界與推播邏輯設計。

---