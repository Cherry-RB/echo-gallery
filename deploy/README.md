# Echo Gallery VPS 部署操作手冊

本手冊對應 `docs/13_VPS部署設計與執行計畫.md` 的 VPS Deployment v1。第一版由主機 Nginx 提供 Vue 靜態檔案，Spring Boot 由 Docker Compose 啟動，正式資料繼續保存在 Supabase PostgreSQL。

## 一、安全邊界

- 不在 repository、指令歷史或部署紀錄中保存真實密碼、JWT secret 或完整資料庫連線字串。
- PostgreSQL 不部署至 VPS，也不公開 5432。
- Spring Boot 只綁定 `127.0.0.1:8080`，外部流量統一經過 Nginx。
- 正式 DNS、Certbot 與 Render 退場必須依 `docs/13_VPS部署設計與執行計畫.md` 分階段執行。
- Render 與原 DNS 設定在觀察期內保留，作為回退路徑。

## 二、檔案責任

| 檔案 | 用途 |
|---|---|
| `compose.production.yml` | 建置及啟動 Spring Boot，設定 localhost port、health check、restart policy 與 log rotation。 |
| `deploy/backend.env.example` | production 環境變數名稱與格式範例，不含真實值。 |
| `deploy/nginx/echo-gallery.conf.example` | Vue SPA、靜態資源快取與 `/api` reverse proxy 範本。 |
| `echo-gallery-backend/src/main/resources/application-prod.yml` | 關閉開發級 SQL 輸出、驗證 schema 並限制 Actuator exposure。 |

## 三、VPS 目錄

```text
/opt/echo-gallery/                       repository
/etc/echo-gallery/backend.env            backend secrets，權限 600
/var/www/echo-gallery/releases/<commit>/ Vue build 產物
/var/www/echo-gallery/current            指向目前 release 的 symlink
/etc/nginx/sites-available/echo-gallery  Nginx 正式設定
```

## 四、建立 backend secrets

先由 `deploy/backend.env.example` 核對需要的變數名稱，再於 VPS repository 外建立 `/etc/echo-gallery/backend.env`。實際檔案至少需要：

```text
JDBC_DATABASE_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
FRONTEND_URL
JAVA_TOOL_OPTIONS
```

完成後確認檔案只允許 root 與部署管理者讀取。不要把實際內容貼進 issue、聊天、log 或 commit。

## 五、啟動後端

從 repository 根目錄執行：

```bash
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f compose.production.yml config --quiet
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f compose.production.yml build backend
APP_VERSION=$(git rev-parse --short HEAD) docker compose -f compose.production.yml up -d backend
docker compose -f compose.production.yml ps
curl --fail http://127.0.0.1:8080/actuator/health
```

預期 Compose 顯示 backend 為 running／healthy，health endpoint 回傳成功。若 production profile 的 schema validation 失敗、Supabase TLS 失敗或 log 出現秘密，立即停止，不繼續設定公開流量。

查看後端 log 時限制輸出範圍，並確認內容不含秘密：

```bash
docker compose -f compose.production.yml logs --tail=200 backend
```

## 六、發布 Vue

在 `echo-gallery-frontend/` 安裝鎖定版本並建置：

```bash
npm ci
npm run test:run
npm run build
```

將 `dist/` 內容發布到以 commit SHA 命名的新 release 目錄，再把 `/var/www/echo-gallery/current` symlink 切向該版本。不要直接覆蓋目前 release，才能在前端異常時快速切回上一版。

正式 build 不需要設定 `VITE_API_BASE_URL`；前端預設使用同源 `/api`。若設定該變數，必須確認它不是 localhost、Render URL 或 HTTP URL。

## 七、設定 Nginx

1. 複製 `deploy/nginx/echo-gallery.conf.example` 到 Nginx `sites-available`。
2. 將 `gallery.example.com` 改成實際測試子網域。
3. 確認 `root` 指向 `/var/www/echo-gallery/current`。
4. 啟用站台前先執行 `sudo nginx -t`。
5. 語法通過後執行 `sudo systemctl reload nginx`。

先以 HTTP 測試首頁、SPA route 與 `/api`。DNS 正確生效後，再依當時 Certbot 官方安裝方式申請憑證；完成後驗證 HTTP 轉 HTTPS 與續期 dry-run。

## 八、Smoke test

依序驗證：

1. `curl --fail http://127.0.0.1:8080/actuator/health`。
2. 瀏覽器開啟首頁及直接重新整理巢狀路由。
3. 確認瀏覽器請求 `/api`，且沒有直接存取 8080。
4. 登入並執行 Card 建立、讀取、修改及刪除。
5. 驗證標籤、Today、查詢及培育計畫核心流程。
6. 從手機行動網路重做登入與核心 Card CRUD。
7. 執行 `docker compose -f compose.production.yml down` 後重新 `up -d`，確認既有 Supabase 資料仍可存取。
8. 重開 VPS，確認 Nginx、Docker 與 backend 自動恢復。

## 九、應用更新與回退

每次發布都記錄 commit SHA。後端回退時切回已驗證的 commit，使用相同 SHA 重新 build 並啟動；前端回退時將 `current` symlink 切回上一個 release，執行 `nginx -t` 後 reload。

若正式網域出現登入、核心 Card 功能、TLS、後端持續重啟或資料完整性問題，停止新的變更、保存 log，並依 `docs/13_VPS部署設計與執行計畫.md` 將 DNS 回退至 Render。
