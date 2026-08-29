# Echo Gallery VPS 部署設計與執行計畫

## 文件資訊

| 項目 | 內容 |
|---|---|
| 文件性質 | 持續維護的部署設計、執行手冊與進度追蹤文件（Current） |
| 建立日期 | 2026-08-12 |
| 目前階段 | Phase 0 repository 部署準備進行中，尚未開始建立 VPS |
| 主要目標 | 將 Echo Gallery 從 Render 代管部署，安全且可回復地演進為 VPS 自管部署 |
| 第一版範圍 | VPS 主機上的 Nginx／Certbot、Vue 靜態檔案、Docker 化 Spring Boot、沿用 Supabase PostgreSQL |
| 暫不包含 | 自管 PostgreSQL、完整 CI/CD、全服務容器化、零停機部署、高可用叢集 |

> 本文件記錄目前的部署設計與預定流程，不代表所有步驟均已執行。執行任何主機、DNS、正式環境或資料操作前，仍須重新核對當時的原始碼、平台設定與官方文件。

---

## 一、文件目的

Echo Gallery 已經部署於 Render，資料庫使用 Supabase PostgreSQL，並持續由開發者本人實際使用。下一階段希望從 PaaS（Platform as a Service）代管環境演進至 VPS（Virtual Private Server）自管環境，以學習並掌握 Linux、網路、Nginx、HTTPS、Docker 與部署維運。

本文件的目的如下：

1. 記錄目前可確認的部署現況。
2. 說明 Render 與 VPS 在責任分工上的差異。
3. 保存經評估後採用的第一版 VPS 架構。
4. 將大型部署工作拆成可逐步驗收的小階段。
5. 為每個階段定義前置條件、操作方向、驗證方式與停止點。
6. 保留 Render 作為回退環境，降低第一次 VPS 部署的風險。
7. 避免同時遷移應用程式、資料庫、網域與架構，造成問題難以定位。

本文件不是要求一次執行到底的命令清單。第一次部署時，應一次只執行一個階段；確認結果符合完成條件後，才進入下一階段。

---

## 二、閱讀方式與狀態標記

### 2.1 資訊分類

文件中的資訊分為三類：

- **已確認現況**：已由目前 repository、設定檔、測試或使用者描述確認。
- **設計決策**：本次部署採用的方向，但尚未代表已實作。
- **待確認資訊**：必須在購買 VPS、設定 DNS 或實際部署前取得，不能預先臆測。

### 2.2 待辦狀態

執行追蹤只使用以下狀態：

```text
待處理 → 處理中 → 待驗證 → 已完成
                    ↘ 暫緩
```

- **待處理**：尚未開始。
- **處理中**：正在修改或執行。
- **待驗證**：操作完成，但尚未通過全部驗收。
- **已完成**：符合該項完成條件，並留下驗證證據。
- **暫緩**：經評估後目前不執行，必須記錄原因。

### 2.3 安全邊界

- 禁止將真實密碼、JWT secret、資料庫連線字串或私鑰寫入本文件。
- 禁止建立、修改或提交 `.env`。
- 禁止讀取 `credentials/` 或 `secrets/` 目錄。
- 文件中的環境變數只列名稱與用途，不填入真實值。
- DNS 切換、關閉 Render、資料庫匯入及刪除服務，都屬於需再次確認的正式環境操作。

---

## 三、目前已確認的專案與部署現況

### 3.1 現有線上架構

依目前可取得資訊，正式環境為：

```text
使用者瀏覽器
   │
   ├─ Render 前端服務：Vue 3 正式建置
   │
   └─ Render 後端服務：Spring Boot API
                         │
                         └─ Supabase PostgreSQL
```

這是一套真正運作中的 production 部署，不是 mock environment。過去已處理的實際部署問題包括：

- Supabase PostgreSQL 建立與連線。
- Render 後端部署。
- Gradle Wrapper JAR 未進入 Git 所造成的部署問題。
- Linux `126` 權限不足，無法執行 `./gradlew`。
- Render IPv4 與 Supabase IPv6 連線相容性問題。
- Render 前端部署。
- TypeScript 正式建置檢查問題。
- Vue SPA 重新整理時的 404 fallback。
- 前後端跨域設定與 `FRONTEND_URL` 白名單。

### 3.2 Repository 現況

目前 repository 已具備：

- Vue 3、TypeScript、Vite 前端。
- Spring Boot 4.x、Java 21 後端。
- PostgreSQL 資料層。
- Spring Security、JWT 與 BCrypt。
- 後端 Dockerfile。
- 本機 PostgreSQL 用 Docker Compose。
- 前後端測試基礎。
- `/actuator/health` 健康檢查端點。
- 前端 API 預設路徑 `/api`。
- CORS 前端網址環境變數 `FRONTEND_URL`。

目前尚未具備完整 VPS 自管部署配置：

- 前端沒有 VPS 發布流程文件。
- 沒有 Nginx 正式站台設定範本。
- 沒有 VPS 專用後端 Compose。
- 沒有 development／production profile 分離。
- production 仍可能使用 `ddl-auto: update` 與 `show-sql: true`。
- 沒有 Flyway 或其他 schema migration 工具。
- 沒有 VPS 備份、還原與 rollback 手冊。
- 沒有主機初始化、安全強化及監控流程。

### 3.3 目前測試基線

2026-08-12 規劃時重新驗證：

- 後端 `./gradlew test`：通過。
- 前端 `npm run test:run`：2 個測試檔、4 個測試通過。
- 前端 `npm run build`：通過。
- 前端建置有大型 chunk 警告，但目前不阻擋部署。

這些結果只代表當時的程式碼基線。每次實際部署應重新執行並記錄結果。

---

## 四、Render 與 VPS 的責任差異

### 4.1 Render 目前代管的工作

Render 類型的平台通常協助處理：

- 建置與啟動命令。
- Process 生命週期與異常重啟。
- 靜態檔案發布。
- SPA rewrite。
- 網域綁定。
- HTTPS 憑證。
- 環境變數介面。
- 外部網路入口。
- 部分 log 與健康狀態資訊。
- 平台作業系統與底層主機維護。

實際有哪些設定存在 Render 後台，必須在遷移前逐項盤點，不能只看 repository 推測。

### 4.2 VPS 自管後新增的責任

VPS 提供的主要是一台可遠端登入的 Linux 主機。使用者需自行處理：

- 作業系統更新。
- SSH 帳號與金鑰安全。
- 防火牆與公開 port。
- Docker 安裝與更新。
- Nginx 安裝與設定。
- DNS 與網域指向。
- HTTPS 憑證申請與續期。
- Spring Boot 容器啟停與重啟。
- 前端靜態檔案發布。
- production secrets 注入。
- log、磁碟、記憶體與服務健康監控。
- 備份、還原與故障處理。
- 應用版本部署與 rollback。

因此，從 Render 遷移到 VPS 不是單純更換執行位置，而是將平台原本代管的責任逐步接回來。

---

## 五、方案比較與選型

### 5.1 Caddy 與 Nginx

| 面向 | Caddy | Nginx |
|---|---|---|
| 授權費用 | 免費 | 免費 |
| Vue 靜態檔案 | 支援 | 支援 |
| Reverse proxy | 支援 | 支援 |
| SPA fallback | 支援 | 支援 |
| HTTPS | 預設高度自動化 | 通常搭配 Certbot |
| 設定難度 | 較低 | 稍高 |
| 使用普及度 | 現代且持續成長 | 業界非常普及 |
| 學習資源 | 充足 | 非常充足 |
| 本專案原始規劃 | 可用 | 原 MVP 已規劃 Nginx |

### 5.2 決策

第一版選擇 **Nginx + Certbot**，理由如下：

1. 延續 MVP 文件原先的 VPS 架構方向。
2. Nginx 的靜態檔案、reverse proxy 與 SPA 設定具高度通用性。
3. 教學、官方文件與故障排查資源多。
4. 適合作為作品集中的自管部署經驗。
5. Nginx、Certbot 與 Let’s Encrypt 本身不增加軟體授權費。

選用 Nginx 的代價是需理解並維護 HTTPS 憑證流程。這屬於學習成本，不是額外授權費用。

### 5.3 主機 Nginx 或容器 Nginx

第一版採用：

```text
主機 Nginx + 主機 Certbot
```

暫不採用：

```text
Nginx 容器 + Certbot 容器
```

原因是容器化 Certbot 需要額外解決：

- 憑證 volume 共享。
- ACME challenge 目錄共享。
- 第一次尚無憑證時的 Nginx 啟動順序。
- 憑證續期後的 Nginx reload。
- 容器重建與憑證持久化。

主機安裝能直接使用 Certbot 的 Nginx 整合與 systemd timer，較適合第一次部署。

### 5.4 前端是否容器化

第一版不強制建立前端 runtime container。

Vue 正式建置後是靜態檔案：

```text
dist/
├── index.html
└── assets/
```

將 `dist/` 發布至 VPS 的網站目錄後，即可由主機 Nginx 提供。前端容器化可留到後續 CI/CD 或全容器化階段。

### 5.5 PostgreSQL 是否搬入 VPS

第一版繼續使用 Supabase PostgreSQL，不搬入 VPS。

理由：

- 使用者目前已有正式資料。
- 專案尚未導入 Flyway migration。
- 尚未建立自動備份與異機保存。
- 尚未做 `pg_restore` 還原演練。
- 尚未建立磁碟、容量與資料庫升級監控。
- 第一次 VPS 部署應將主機與應用問題和資料遷移問題分開。

未來是否自管 PostgreSQL 應另立獨立目標與執行計畫。

---

## 六、推薦的第一版 VPS 架構

```text
Internet
   │
   │ TCP 80 / 443
   ▼
Ubuntu VPS
   │
   ├─ Nginx（直接安裝於主機）
   │    ├─ /             → Vue dist 靜態檔案
   │    ├─ /assets/*     → Vue 建置資源
   │    ├─ 前端路由       → fallback 至 index.html
   │    └─ /api/*        → http://127.0.0.1:8080
   │
   ├─ Certbot（直接安裝於主機）
   │    └─ 申請、安裝與續期 HTTPS 憑證
   │
   └─ Docker Compose
        └─ Spring Boot backend
             ├─ host 綁定 127.0.0.1:8080
             └─ TLS 連線至 Supabase PostgreSQL
```

遷移期間保留：

```text
Render 前端 + Render 後端
```

作為正式流量尚未切換或 VPS 發生問題時的回退環境。

---

## 七、各元件責任

### 7.1 Vue／Vite

- 執行 TypeScript 檢查。
- 將 Vue 原始碼編譯成 `dist/`。
- 透過相對路徑 `/api` 呼叫後端。
- 不負責監聽 production 的 80／443。
- `.env.production` 只會影響建置內容，不會提供網站或申請 HTTPS。

### 7.2 Nginx

- 對外監聽 80／443。
- 提供 Vue 靜態檔案。
- 使用 SPA fallback 支援 Vue Router 重新整理。
- 將 `/api/*` 轉送到 Spring Boot。
- 傳遞必要的 `Host`、`X-Real-IP`、`X-Forwarded-For`、`X-Forwarded-Proto` headers。
- 可加入靜態資源快取、request body 大小與 timeout 等限制。

### 7.3 Certbot／Let’s Encrypt

- 驗證網域控制權。
- 申請公開 TLS 憑證。
- 協助 Nginx 啟用 HTTPS。
- 透過 systemd timer 或排程自動續期。
- 使用 `certbot renew --dry-run` 驗證續期流程。

### 7.4 Spring Boot

- 提供 `/api` 商務 API。
- 驗證 JWT。
- 執行卡片、標籤、使用者與回流邏輯。
- 連線至 Supabase PostgreSQL。
- 提供 `/actuator/health` 給本機健康檢查。
- 不直接暴露在公網。

### 7.5 Docker Compose

第一版只管理 Spring Boot 後端容器：

- 定義 image build 或 image 版本。
- 注入環境變數。
- 將容器 port 綁定到 `127.0.0.1:8080`。
- 設定 restart policy。
- 設定 health check。
- 設定 log rotation。

### 7.6 Supabase PostgreSQL

- 第一版繼續保存正式資料。
- VPS 後端透過 Supabase 提供的連線資訊連線。
- 實際使用 direct connection、pooler、TLS 或 IPv4 相容方案，需在部署前依目前 Supabase 專案設定重新確認。

### 7.7 Render

- 遷移期間維持原服務。
- VPS 正式切換前作為既有 production。
- 正式切換後暫時作為 rollback 目的地。
- 未經確認不得關閉或刪除服務。

---

## 八、網域、DNS 與 HTTPS 設計

### 8.1 建議的同源架構

建議使用同一個網站來源：

```text
https://gallery.example.com/          → Vue
https://gallery.example.com/api/...   → Spring Boot
```

優點：

- 前端可以繼續使用 `/api`。
- 瀏覽器不需要跨網域呼叫 API。
- CORS 複雜度降低。
- 切換主機時不必重新將完整 API URL 編入前端。
- Nginx 成為唯一公開入口。

實際網域尚待使用者提供或選擇，本文件不預設正式網域名稱。

### 8.2 DNS

DNS 的角色是將網域名稱指向 VPS 公開 IP。常見做法為建立或修改：

```text
A record: gallery.example.com → VPS IPv4
```

若使用 IPv6，才另外評估 AAAA record。第一次部署可以先以 IPv4 降低變因。

DNS 切換前應確認：

- 網域目前由哪個平台管理。
- 原 Render DNS record 的類型與內容。
- 是否能建立測試子網域。
- DNS TTL 是否可調整。
- 正式切換時如何改回 Render。

### 8.3 HTTPS

正式登入與 JWT 傳輸必須使用 HTTPS。預定流程：

1. DNS 已指向 VPS。
2. VPS 的 80／443 可由公網連入。
3. Nginx 的 HTTP 站台可正常回應。
4. 使用 Certbot Nginx plugin 申請憑證。
5. 驗證 HTTP 會轉向 HTTPS。
6. 驗證瀏覽器憑證有效。
7. 執行續期 dry-run。

在沒有正式網域的本機環境，不需模擬公開 Let’s Encrypt 憑證。

---

## 九、網路與 Port 安全設計

### 9.1 公開 port

VPS 原則上只公開：

| Port | 用途 | 備註 |
|---:|---|---|
| 22 | SSH | 優先限制來源 IP；只使用 key 登入 |
| 80 | HTTP | HTTPS redirect 與 ACME challenge |
| 443 | HTTPS | 網站正式流量 |

### 9.2 不公開 port

| Port | 服務 | 原因 |
|---:|---|---|
| 8080 | Spring Boot | 只允許主機 Nginx 存取 |
| 5432 | PostgreSQL | 第一版使用 Supabase，不在 VPS 提供 DB |
| 2375／2376 | Docker daemon | 暴露 daemon 等同提供高權限控制面 |

後端 port 應綁定：

```text
127.0.0.1:8080:8080
```

而非：

```text
8080:8080
```

Docker 發布 port 可能影響主機防火牆行為，不能只依賴 UFW；Compose 也必須避免將不必要服務發布至所有網路介面。

### 9.3 SSH 原則

- 使用 SSH key，不使用日常密碼登入。
- 建立非 root 管理帳號。
- 確認新帳號和 key 登入成功後，才調整 root 或密碼登入政策。
- 不可在尚未驗證第二條連線前關閉目前唯一可用的登入方式。
- 私鑰只保存在可信任裝置，不提交 Git。

---

## 十、Production 設定與敏感資訊

### 10.1 預計使用的環境變數

實際名稱需在實作前核對。目前至少包含：

| 名稱 | 用途 | 是否敏感 |
|---|---|---|
| `JWT_SECRET` | JWT 簽章密鑰 | 是 |
| `JDBC_DATABASE_URL` | Supabase JDBC 連線字串 | 是 |
| `DB_USERNAME` | 資料庫帳號 | 是 |
| `DB_PASSWORD` | 資料庫密碼 | 是 |
| `FRONTEND_URL` | 後端 CORS 允許的正式前端 URL | 否 |
| `SPRING_PROFILES_ACTIVE` | 啟用 production profile | 否 |

可能需要新增的變數必須由實作計畫確認，不在本文件預先定案。

### 10.2 保存原則

- 真實值只保存在 VPS 的受限位置或 VPS provider secret 機制。
- repository 只提交不含真實值的範本。
- 不把 secret 放入 Dockerfile、image、Nginx 設定或 Markdown。
- 限制 secret 檔案的 Linux 權限。
- 備份檔若包含資料庫內容，同樣視為敏感資料。

### 10.3 前端環境變數

前端的 `VITE_*` 變數會被編入瀏覽器可下載的 JavaScript，因此不能放秘密。若採同源 `/api`，應避免把 Render 後端完整 URL 寫死於新的 VPS build。

現有 `.env.production` 的實際內容必須由使用者自行確認或在明確授權下安全盤點；代理不得修改 `.env`。

---

## 十一、後端 Production Profile 規劃

目前 `application.yml` 仍包含開發用途設定：

```text
ddl-auto: update
show-sql: true
format_sql: true
```

預定將環境責任分離：

### Development

- 允許本機方便開發的資料庫設定。
- 是否保留 `ddl-auto: update` 需另行評估。
- 可保留必要 SQL 除錯能力。

### Test

- 已有 `application-test.yml`。
- 使用 Testcontainers 隔離 PostgreSQL。
- `ddl-auto: create` 只作用於測試容器。

### Production

- `show-sql: false`。
- `ddl-auto` 最終應使用 `validate` 或 `none`。
- 資料庫 schema 由 migration 管理。
- 啟用必要 proxy／forwarded headers 支援。
- 限制 Actuator 公開範圍。
- 設定合適的 log level。
- 不提供敏感預設值。

### Migration 前置問題

由 `update` 直接改成 `validate` 之前，必須先確認現有 Supabase schema 與 Entity 一致，並建立 Flyway baseline／migration 策略。不可只改一行設定後直接部署正式環境。

---

## 十二、Code Review 項目與部署門檻

Code Review 文件是持續追蹤來源，實作前仍須重新核對程式碼。

### 12.1 測試子網域部署前

允許尚未修完全部 CR，但至少應：

- 確認 CR-006 的現有實作與測試是否符合完成條件。
- 確認 production secret 不會進入 Git 或 image。
- 確認後端只綁定 localhost。
- 確認 HTTPS 啟用前不傳送正式登入憑證。
- 確認測試子網域不影響正式 Render 流量。

### 12.2 正式切換前優先處理

| CR | 項目 | 部署關聯 |
|---|---|---|
| CR-001 | 登入與註冊缺乏速率限制 | 公開 VPS 會直接承受網路攻擊流量 |
| CR-002 | JWT 儲存與撤銷策略 | 需明確記錄 token lifecycle 與過渡風險 |
| CR-003 | 正式資料庫設定不安全 | 與 production profile、schema migration 直接相關 |
| CR-007 | Card 狀態 request 缺省值 | 避免公開 API 因缺欄位意外修改資料 |
| CR-008 | 安全上下文錯誤轉為 null | 避免認證異常形成難診斷的 500 |
| CR-012 | 查詢參數缺乏驗證 | 避免公開查詢接收無界限輸入 |

### 12.3 不阻擋第一版 VPS 測試部署

以下問題仍需處理，但通常不阻擋測試子網域：

- HTTP status 精確度。
- 分頁 metadata。
- Card DTO 拆分。
- 前端 `any` 清理。
- CardService 維護性重構。
- Query cache 型別改善。
- 過時註解與依賴清理。

正式切換前仍應重新評估所有高嚴重度問題，而不是只依本表。

---

## 十三、分階段執行計畫

### 週末執行模式：VPS Deployment v1

第一次執行本計畫時，可將它視為一個兩天封閉式小專案。這個週末的成功標準是：

> 外網可以透過網域打開 Vue 前端，前端可以透過同源 `/api` 呼叫 Spring Boot，後端可以正常讀寫既有 Supabase PostgreSQL 資料。

本次目標是完成一次可用、可重啟且可回退的部署，不要求先完整理解 VPS、Linux、Docker、Nginx、TLS、網路與 CI/CD。部署中遇到的問題只有在阻擋下一個驗收點、可能造成資料遺失或涉及安全邊界時才立即深入；其餘問題記入後續 backlog。

預計投入星期六與星期日各 6～8 小時，並保留 3～5 小時 debug buffer。時間是範圍控制工具，不是要求在資訊不足時勉強切換正式流量。

| 階段 | 目標 | 大約投入 |
|---|---|---:|
| ① VPS 起機 | SSH 登入，Docker、Compose 與 Nginx 可用 | 1～2h |
| ② 專案部署準備 | 取得指定 commit、建立主機端秘密設定、完成 build | 1～2h |
| ③ 後端啟動 | Spring Boot container 正常，能連線 Supabase，API 可由本機驗證 | 1～3h |
| ④ 前端發布 | Vue build 完成，由 Nginx 正常提供 | 1～2h |
| ⑤ Nginx 串接 | `/` 提供 Vue，`/api` reverse proxy 至 Spring Boot | 1～3h |
| ⑥ Domain 與 HTTPS | DNS、Certbot、HTTPS 與續期驗證 | 1～2h |
| ⑦ 真機驗收 | 登入、核心 CRUD、容器與 VPS 重啟驗證 | 1～2h |
| Debug buffer | 處理實際環境差異與整合問題 | 3～5h |

星期六優先讓系統活起來：

```text
VPS
  ↓
Docker Compose
  ↓
Spring Boot
  ↓
Supabase PostgreSQL
  ↓
Vue
```

星期六的最低停止點是能在 VPS 上驗證 Spring Boot 可正常讀寫既有資料，並能以 HTTP 開啟 Vue。若當天尚未完成網域或 HTTPS，不因此擴大研究範圍。

星期日再完成公開入口與長期使用所需的最低可靠性：

```text
網域
  ↓
Nginx reverse proxy
  ↓
HTTPS
  ↓
同源 /api、環境變數與 CORS
  ↓
container restart
  ↓
VPS reboot
```

#### Weekend Definition of Done

- [ ] VPS 可以使用 SSH key 登入，且保留已驗證的安全登入方式。
- [ ] Docker Engine 與 Docker Compose plugin 正常。
- [ ] Spring Boot container 正常，且 8080 未公開至外網。
- [ ] Spring Boot 可以正常讀寫既有 Supabase PostgreSQL 資料。
- [ ] Vue 可由 Nginx 正常提供，巢狀路由重新整理不會 404。
- [ ] 前端可透過同源 `/api` 呼叫後端，瀏覽器沒有 CORS 或 Mixed Content 錯誤。
- [ ] Domain 正確指向 VPS。
- [ ] HTTPS 憑證有效，HTTP 會轉向 HTTPS。
- [ ] `docker compose down` 後重新 `docker compose up -d`，後端可恢復連線且既有資料仍可存取。
- [ ] VPS reboot 後，Nginx、Docker 與應用服務可自動恢復。
- [ ] 手機使用外部網路可完成登入與核心 Card CRUD。
- [ ] Render 與原 DNS 設定仍可供回退，尚未刪除或停用。

此處的 `docker compose down`／`up -d` 驗證是確認應用容器可重建並重新連線 Supabase，不是驗證 VPS PostgreSQL volume。第一版不在 VPS 自管 PostgreSQL，也不執行正式資料搬遷。

以上項目全部完成後，VPS Deployment v1 即可宣告完成。未完成的進階工作不得被重新定義成 v1 尚未部署完成。

#### VPS Deployment v2 backlog

- GitHub Actions 與自動部署。
- Supabase 至 VPS PostgreSQL 的受控資料遷移。
- PostgreSQL named volume、異機自動備份與還原演練。
- Log rotation 與集中式日誌。
- Monitoring 與磁碟容量告警。
- 進一步的 firewall 與 SSH hardening。
- Docker image 與 JVM 資源最佳化。
- Staging／production 環境分離。
- 零停機部署、高可用與多主機架構。

其中資料庫遷移必須另立執行計畫，至少涵蓋匯出、匯入、資料筆數與關聯驗證、短暫停止寫入、正式切換、異機備份、還原演練及 Supabase 觀察期。`docs/16_卡片花園培育方案與MVP觀察計畫.md` 與 `docs/17_Project語意原型與Work模型驗證計畫.md` 所規劃的文字欄位、生長紀錄與關聯資料不構成第一版容量阻礙，但不能取代上述資料保護措施。

#### 學習與故障處理規則

部署時反覆詢問：目前距離下一個可驗收狀態，還差哪一個最小步驟？

```text
問題會阻止目前階段驗收、危及既有資料或突破安全邊界嗎？
├── 會：保存錯誤證據，理解並處理到可以安全繼續
└── 不會：記入 v2 backlog，繼續部署主線
```

常見阻塞點依序檢查：

1. Spring Boot 是否仍使用不適用於正式環境的資料庫連線設定。
2. Vue production API path 是否為同源 `/api`，而非 localhost 或 Render URL。
3. `FRONTEND_URL`、CORS 與 forwarded headers 是否符合正式網域。
4. VPS 與 provider firewall 是否只公開必要的 22、80、443。
5. Spring Boot 的 8080 是否只綁定 localhost。
6. Nginx 的 SPA fallback、`/api` proxy target 與 path 轉送是否正確。

遇到問題時先保存實際錯誤、一次只改一層，避免同時修改 DNS、Nginx、Docker 與應用設定。

### Phase 0：Repository 部署準備

#### 目標

在不碰 VPS、Render、Supabase 資料與正式 DNS 的前提下，讓 repository 具備可審查的 VPS 部署藍圖。

#### 預計工作

- [ ] 重新盤點 Render 的 build、start、rewrite、environment 與 health check 設定。
- [x] 確認前端正式建置使用同源 `/api`，未寫死 Render URL。
- [x] 建立後端 production profile。
- [x] 建立 VPS 後端 Compose。
- [x] 建立 Nginx HTTP 設定範本。
- [x] 加入 restart policy、health check 與 log rotation。
- [x] 建立不含秘密的 production 環境變數清單。
- [x] 建立部署、更新、rollback 與故障排查文件。
- [x] 建置後端 Docker image。
- [x] 執行前後端測試及前端 build。

#### 完成條件

- 所有部署設定可由差異審查理解。
- 沒有真實 secret 進入 Git。
- 後端 image 可成功建置。
- 後端容器能使用非正式資料庫或隔離環境啟動。
- 前端正式 build 通過。
- Render 現有服務不受影響。

#### 停止點

完成 Phase 0 後先停下，重新檢視檔案與操作文件，再決定 VPS 供應商。

### Phase 1：VPS 選購與主機初始化

#### 目標

取得一台安全、可登入、可更新的 Ubuntu VPS，但尚不部署 Echo Gallery。

#### VPS 規格評估原則

初期單人使用、資料庫外置時，可從小型 VPS 評估：

- 1～2 vCPU。
- 建議 2 GB RAM 起步；1 GB 可能需要嚴格限制 Java 記憶體或 swap。
- 20 GB 以上 SSD，依 image、log 與備份需求調整。
- 支援 Ubuntu LTS。
- 提供 IPv4。
- 提供 snapshot／backup 選項。
- 機房鄰近主要使用者，降低延遲。

實際規格、價格與供應商政策可能變動，購買前必須重新查詢。軟體 Nginx、Certbot、Docker 與 Let’s Encrypt 通常不需授權費；主要成本為 VPS、網域、snapshot、備份儲存與可能的流量費。

#### 預計工作

- [ ] 選擇供應商、區域、Ubuntu LTS 與規格。
- [ ] 建立 SSH key 或確認既有 key 使用方式。
- [ ] 建立 VPS。
- [ ] 第一次登入並記錄主機資訊。
- [ ] 建立非 root sudo 使用者。
- [ ] 驗證新使用者可用 SSH key 登入。
- [ ] 更新系統套件。
- [ ] 設定主機時區與時間同步。
- [ ] 設定 UFW 或等效防火牆。
- [ ] 安裝 Docker Engine 與 Compose plugin。
- [ ] 安裝 Nginx。
- [ ] 確認服務狀態與開機自動啟動。
- [ ] 建立 provider snapshot（若方案支援）。

#### 驗證

- 新 SSH session 可登入。
- `sudo` 可正常使用。
- 只開放規劃中的 port。
- Docker hello-world 或等效檢查通過。
- Nginx 預設測試頁可由 VPS IP 存取。
- VPS 重開機後 SSH、Docker、Nginx 可恢復。

#### 安全停止點

在新帳號與 SSH key 尚未驗證前，不停用目前唯一的登入方式。防火牆調整時保留既有 SSH session，並用第二個 session 驗證。

### Phase 2：測試子網域與 HTTP Nginx

#### 目標

讓測試子網域指向 VPS，先建立不含正式應用的 HTTP 網站入口。

#### 預計工作

- [ ] 盤點 DNS provider。
- [ ] 建立測試子網域 A record。
- [ ] 等待 DNS 生效。
- [ ] 建立 Nginx server block。
- [ ] 放置簡單測試頁。
- [ ] 驗證 domain 與 Host routing。
- [ ] 檢查 Nginx 設定語法。
- [ ] reload Nginx。

#### 驗證

- 瀏覽器可透過測試子網域看到測試頁。
- DNS 指向正確 VPS IPv4。
- Nginx access／error log 可供排查。
- 不影響正式 Render 網域。

### Phase 3：部署後端並連線 Supabase

#### 目標

在 VPS 啟動 Spring Boot 容器，僅由 VPS 本機存取，並連線至既有 Supabase。

#### 預計工作

- [ ] 將指定 commit 部署至 VPS。
- [ ] 建置或取得後端 image。
- [ ] 在 VPS 安全建立 production secret 設定。
- [ ] 啟用 production profile。
- [ ] 啟動後端 Compose。
- [ ] 檢查容器狀態與 log。
- [ ] 由 VPS 本機呼叫 `/actuator/health`。
- [ ] 確認 Supabase 連線模式、TLS 與 IPv4 相容性。
- [ ] 確認 8080 無法由外部直接連線。

#### 驗證

- `127.0.0.1:8080/actuator/health` 回報健康。
- 外部無法直接存取 VPS 的 8080。
- 後端能讀取正式資料，但不先執行破壞性操作。
- log 不包含明文密碼、JWT secret 或完整敏感連線字串。
- 容器重啟後能恢復。

#### 停止條件

若 Hibernate 嘗試進行非預期 schema 修改、Supabase TLS 失敗、連線模式不明或 log 出現秘密，立即停止，不進入前端部署。

### Phase 4：部署 Vue 與設定 Reverse Proxy

#### 目標

讓測試子網域同時提供 Vue 與 `/api`。

#### 預計工作

- [ ] 執行前端測試。
- [ ] 執行 `npm run build`。
- [ ] 將 `dist/` 發布至版本化或可回退的 VPS 目錄。
- [ ] 將 Nginx root 指向當前版本。
- [ ] 設定 SPA fallback。
- [ ] 設定 `/api` reverse proxy。
- [ ] 傳遞必要 forwarded headers。
- [ ] 設定合理 timeout 與 request body 限制。
- [ ] 檢查 Nginx 語法並 reload。

#### 驗證

- 首頁可載入。
- 靜態 assets 回傳成功。
- 直接重新整理 Vue 巢狀路由不會 404。
- `/api` 能正確到達 Spring Boot。
- 瀏覽器看不到對 8080 的直接請求。
- 登入、登出、卡片 CRUD、星標、封存、稍後再看、標籤與看板完成 smoke test。
- 行動裝置與桌面尺寸進行基本手動驗證。

### Phase 5：啟用 HTTPS

#### 目標

測試子網域全程使用有效 HTTPS。

#### 預計工作

- [ ] 確認 DNS、80 與 443 狀態。
- [ ] 安裝官方建議的 Certbot 發行方式。
- [ ] 使用 Nginx plugin 申請憑證。
- [ ] 驗證 HTTP 轉 HTTPS。
- [ ] 檢查瀏覽器憑證鏈。
- [ ] 執行 `certbot renew --dry-run`。
- [ ] 確認 systemd timer 或續期排程。

#### 驗證

- 網站只透過 HTTPS 使用。
- 登入請求不經明文 HTTP。
- 憑證網域正確且未過期。
- 續期 dry-run 通過。
- Nginx reload 後仍可正常提供服務。

### Phase 6：穩定性與回復演練

#### 目標

在切換正式流量前，證明部署可以更新、重啟與回退。

#### 預計工作

- [ ] 重開 VPS。
- [ ] 驗證 Nginx、Docker 與 backend 自動恢復。
- [ ] 模擬後端容器停止並重新啟動。
- [ ] 驗證 Nginx 在後端失效時的行為與 log。
- [ ] 部署一個無資料變更的測試版本。
- [ ] 回退到前一版本。
- [ ] 檢查磁碟、RAM 與 container log 增長。
- [ ] 建立服務狀態檢查清單。
- [ ] 建立緊急聯絡／供應商 console 登入方式。

#### 驗證

- 主機重開後網站自動恢復。
- 可以辨認 Nginx、Spring Boot、DNS、TLS 各層錯誤。
- 可以在不改資料庫的情況下回退應用版本。
- Render 仍可作為外部 rollback 目標。

### Phase 7：正式 DNS 切換

#### 目標

將正式流量由 Render 切換至 VPS，同時保留可回復性。

#### 前置條件

- 測試子網域已穩定。
- 部署前 CR 門檻已重新驗收。
- 前後端測試與 build 通過。
- HTTPS 與續期通過。
- VPS 重啟與應用 rollback 已演練。
- 已記錄 Render 原 DNS 設定。
- 正式資料庫沒有同時進行 migration。

#### 預計工作

- [ ] 記錄目前正式 DNS 完整設定。
- [ ] 視需要提前調低 TTL。
- [ ] 部署預定正式 commit。
- [ ] 執行部署前 smoke test。
- [ ] 修改正式 DNS 指向 VPS。
- [ ] 從不同網路驗證 DNS 與 HTTPS。
- [ ] 執行完整功能 smoke test。
- [ ] 觀察 Nginx、backend 與 Supabase 指標。
- [ ] 保留 Render，不立即刪除。

#### Rollback 觸發條件

以下任一情況應考慮切回 Render：

- 正式網域無法穩定取得有效憑證。
- 登入或核心 Card 功能失敗。
- 後端持續重啟或記憶體不足。
- Supabase 連線大量失敗。
- 發生資料完整性疑慮。
- 8080 或其他內部服務意外公開。
- 無法在合理時間內定位問題。

#### Rollback 步驟概念

1. 暫停新的 VPS 部署操作。
2. 保留 VPS log 與現場資訊。
3. 將 DNS record 改回已記錄的 Render 設定。
4. 驗證 Render 前端、後端與 Supabase 功能。
5. 等待 DNS cache 更新。
6. 確認使用者流量恢復後，再離線分析 VPS 問題。

### Phase 8：觀察期與 Render 退場

#### 目標

確認 VPS 足以長期承載後，才評估降低或停止 Render 成本。

#### 預計工作

- [ ] 定義觀察期長度。
- [ ] 每日檢查可用性、錯誤、磁碟與記憶體。
- [ ] 驗證至少一次正式應用更新。
- [ ] 驗證至少一次憑證續期或續期排程狀態。
- [ ] 確認 rollback 不再依賴未保存的 Render 設定。
- [ ] 經使用者確認後才停用 Render。

即使停止 Render，也不應立即刪除所有設定紀錄；應保存必要的部署與回退歷史，但不得保存秘密。

---

## 十四、應用發布與 Rollback 設計

### 14.1 發布原則

- 每次部署指定明確 Git commit，不部署不明確的工作目錄狀態。
- 部署前執行適用測試與 build。
- 前端靜態檔案採版本化目錄或可原子切換的 current link。
- 後端 image 使用明確 tag，不只使用無法追溯的 `latest`。
- 部署不應自動修改正式 schema，除非本次明確包含已驗證 migration。
- 每次發布留下時間、commit、執行者與驗證結果。

### 14.2 前端回退概念

```text
/var/www/echo-gallery/releases/<version>/
/var/www/echo-gallery/current → releases/<version>/
```

部署新版本後只切換 `current` 指向；若失敗可切回前一個 release。實際檔案位置與權限在實作時定案。

### 14.3 後端回退概念

- 保留前一版 image tag。
- 更新 Compose 所引用的 tag。
- 啟動後先檢查 health。
- 新版失敗時改回前一 tag。
- 若新版包含不可逆資料庫 migration，單純回退 image 可能不安全，必須另有 migration rollback 或 forward-fix 設計。

---

## 十五、PostgreSQL 備份與未來遷移

### 15.1 第一版

- 正式資料繼續保存在 Supabase。
- 不在第一次 VPS 部署時搬資料庫。
- 確認 Supabase 現有備份能力、保留政策與方案限制。
- 在進行 schema migration 或資料搬遷前建立可驗證的邏輯備份。

### 15.2 未來自管資料庫的必要條件

- [ ] 確認 PostgreSQL 來源與目標版本。
- [ ] 導入 Flyway 或等效 migration。
- [ ] 建立 baseline。
- [ ] 建立 `pg_dump` 排程。
- [ ] 備份加密並保存至 VPS 外部。
- [ ] 定義備份保留週期。
- [ ] 在隔離環境完成 `pg_restore`。
- [ ] 記錄 RPO（可接受資料遺失量）。
- [ ] 記錄 RTO（可接受復原時間）。
- [ ] 監控磁碟容量。
- [ ] 限制 5432 公開存取。
- [ ] 設計維護窗口與停機流程。
- [ ] 設計 DNS／應用回退與資料一致性策略。

沒有實際還原成功的備份，不應視為已具備備份能力。

---

## 十六、監控與日常維運最低需求

第一版不導入大型監控平台，但至少應能回答：

- 主機是否在線？
- Nginx 是否正常？
- Spring Boot 容器是否健康？
- Supabase 是否可連線？
- 磁碟是否即將滿？
- 記憶體是否持續不足？
- 憑證何時過期？
- 最近是否大量出現 4xx／5xx？
- 目前部署的是哪個 commit／image tag？

最低維運項目：

- [ ] Nginx access log 與 error log。
- [ ] Docker container log rotation。
- [ ] Spring Boot health endpoint。
- [ ] `docker compose ps` 狀態檢查。
- [ ] 主機磁碟、記憶體與負載檢查。
- [ ] Certbot timer 與到期日檢查。
- [ ] Ubuntu 安全更新提醒。
- [ ] VPS provider 狀態與 console 存取方式。

後續可再評估外部 uptime monitor、集中 log、告警與 metrics，不列為第一版阻塞條件。

---

## 十七、安全檢查清單

### 主機

- [ ] 使用受支援的 Ubuntu LTS。
- [ ] 非 root 日常管理帳號。
- [ ] SSH key 登入已驗證。
- [ ] 防火牆只開必要 port。
- [ ] 系統套件已更新。
- [ ] 時間同步正常。
- [ ] VPS provider 帳號已啟用 MFA（若支援）。

### Docker

- [ ] Docker 來自官方支援來源。
- [ ] Docker daemon 未公開。
- [ ] backend 只綁 `127.0.0.1:8080`。
- [ ] 容器具有 restart policy。
- [ ] image tag 可追溯。
- [ ] log 有大小與檔案數限制。
- [ ] image 不包含秘密。

### Nginx／TLS

- [ ] Nginx 設定語法檢查通過。
- [ ] HTTP 轉向 HTTPS。
- [ ] 憑證與網域相符。
- [ ] Certbot dry-run 通過。
- [ ] `/api` 只轉往本機 backend。
- [ ] SPA fallback 正常。
- [ ] 不暴露敏感檔案與目錄 listing。

### 應用

- [ ] production profile 生效。
- [ ] `show-sql` 關閉。
- [ ] schema 管理策略已確認。
- [ ] JWT secret 強度足夠且未提交。
- [ ] CORS 與實際來源一致。
- [ ] Actuator 只公開必要端點。
- [ ] rate limiting 風險已處理或明確接受。
- [ ] 前後端測試與 build 通過。

### 資料

- [ ] Supabase 連線使用 TLS。
- [ ] 資料庫權限不超出需要。
- [ ] 備份方案已確認。
- [ ] 正式遷移前完成還原演練。
- [ ] log 不輸出敏感資料。

---

## 十八、Smoke Test 清單

每次部署至少檢查：

### 網站與路由

- [ ] 首頁載入。
- [ ] 靜態 CSS／JavaScript／圖片載入。
- [ ] 直接重新整理巢狀 Vue route 不會 404。
- [ ] HTTP 自動轉 HTTPS。
- [ ] 瀏覽器沒有 mixed content。

### 認證

- [ ] 登入成功。
- [ ] 錯誤密碼得到預期錯誤。
- [ ] 登出後本機 token 清理。
- [ ] 過期／無效 token 得到 401。
- [ ] 未登入不能讀取受保護 API。

### 核心功能

- [ ] 今日看板。
- [ ] 全部卡片看板。
- [ ] 新增卡片。
- [ ] 查看與編輯卡片。
- [ ] 星標。
- [ ] 稍後再看。
- [ ] 封存與取消封存。
- [ ] 標籤清單與標籤中心。
- [ ] 搜尋。
- [ ] 側邊欄統計。

### 安全與隔離

- [ ] 使用者只能存取自己的資料。
- [ ] 外部無法存取 8080。
- [ ] 外部無法存取 5432。
- [ ] 回應與 log 未洩漏 secret。

### 響應式

- [ ] 桌面寬度。
- [ ] 平板寬度。
- [ ] 行動裝置寬度。
- [ ] Safari 相容性重點畫面。

---

## 十九、故障定位順序

遇到網站無法使用時，依層次排查，不一次修改多項設定：

```text
1. DNS 是否指向正確 IP
2. VPS 是否可連線
3. 防火牆是否允許 80 / 443
4. Nginx 是否運作、設定是否有效
5. TLS 憑證是否有效
6. Vue 靜態檔案是否存在且權限正確
7. /api 是否被 Nginx 正確轉送
8. backend 容器是否健康
9. backend log 是否有應用錯誤
10. Supabase 是否可連線
11. API contract 或前端程式是否有問題
```

常用證據來源將在實作手冊中具體化：

- DNS 查詢結果。
- Nginx configuration test。
- systemd service status。
- Nginx access／error log。
- Docker Compose service status。
- Docker container log。
- Spring Boot health response。
- 瀏覽器 Network 與 Console。

排查時先保存錯誤訊息，不要為了嘗試而同時更改 DNS、Nginx、Docker 和應用設定。

---

## 二十、目前明確不做的事情

第一版不做：

- 關閉或刪除 Render。
- 將 Supabase PostgreSQL 搬到 VPS。
- 在 VPS 公開 PostgreSQL 5432。
- 將 Docker daemon 暴露到網路。
- Nginx／Certbot 容器化。
- 前端 runtime 容器化。
- Kubernetes。
- 多台 VPS 或高可用叢集。
- 自動水平擴充。
- 完整 CI/CD 自動部署。
- 零停機藍綠部署。
- 在部署任務中順便完成全部 CR。
- 在同一次發布中導入 OTPAR schema 並搬遷資料庫。

這些項目不是永久否決，而是避免第一次 VPS 部署範圍失控。

---

## 二十一、預計的 Repository 變更

實際檔名與內容須在各修改任務開始前提出計畫並取得確認。可能包含：

```text
deploy/
├── nginx/
│   └── echo-gallery.conf.example
└── README 或操作手冊

compose.production.yml 或其他明確命名的 VPS backend Compose

echo-gallery-backend/src/main/resources/
└── application-prod.yml
```

可能調整：

- 後端 Dockerfile 的執行使用者、health check 或 JVM 設定。
- 後端 production profile。
- `.env.example` 的非敏感變數名稱；不得修改 `.env`。
- README 或 docs index。
- 未來 Flyway migration 檔案。

不建議直接覆蓋現有本機 `docker-compose.yml`，因為它目前服務於 Windows／IDE 連線本機 PostgreSQL的開發流程，與 VPS 後端部署需求不同。

---

## 二十二、任務與 Commit 邊界建議

部署準備應拆成小型、可驗證的邏輯變更，例如：

1. `docs(deploy): 建立 VPS 部署設計與執行計畫`
2. `chore(config): 分離後端正式環境設定`
3. `chore(deploy): 建立 VPS 後端 Compose`
4. `chore(deploy): 新增 Nginx 站台設定範本`
5. `chore(deploy): 補上容器健康檢查與日誌限制`
6. `docs(deploy): 新增 VPS 初始化與部署操作手冊`
7. `chore(db): 導入資料庫 migration 基礎`（獨立計畫）
8. `fix(security): ...`（依各 CR 分別處理）

API endpoint、資料庫 schema、認證策略與正式 DNS 變更都必須先說明影響並取得確認。

---

## 二十三、總待辦追蹤表

| 編號 | 階段 | 工作 | 狀態 | 驗證證據／備註 |
|---|---|---|---|---|
| VPS-001 | Phase 0 | 盤點 Render 現有部署設定 | 待處理 | 不讀取或記錄秘密 |
| VPS-002 | Phase 0 | 確認前端 production API path | 已完成 | 2026-08-29：預設使用同源 `/api` |
| VPS-003 | Phase 0 | 設計後端 production profile | 已完成 | 2026-08-29：已建立 `application-prod.yml`；真實 schema validation 待 VPS 隔離驗證 |
| VPS-004 | Phase 0 | 建立 VPS backend Compose | 已完成 | 2026-08-29：backend 只綁 `127.0.0.1:8080` |
| VPS-005 | Phase 0 | 建立 Nginx 設定範本 | 已完成 | 2026-08-29：已包含 SPA、靜態快取與 `/api` reverse proxy |
| VPS-006 | Phase 0 | 建立部署與 rollback 手冊 | 已完成 | 2026-08-29：已建立 `deploy/README.md` |
| VPS-007 | Phase 0 | 驗證 image、測試與 build | 已完成 | 2026-08-29：backend test、frontend test/build 與 Docker image build 通過 |
| VPS-008 | 安全 | 驗收 CR-006 並更新追蹤建議 | 待處理 | 程式已有實作，文件未同步 |
| VPS-009 | 安全 | 評估／處理 CR-001 | 待處理 | 正式切換前優先 |
| VPS-010 | 安全 | 記錄 CR-002 JWT lifecycle 決策 | 待處理 | 正式切換前優先 |
| VPS-011 | 安全 | 處理 CR-003 | 待處理 | 與 migration 分階段 |
| VPS-012 | 安全 | 處理 CR-007、008、012 | 待處理 | 逐項確認 commit 邊界 |
| VPS-013 | Phase 1 | 選購 VPS | 待處理 | 當時重新查價格與規格 |
| VPS-014 | Phase 1 | SSH 與主機安全初始化 | 待處理 | 第二條連線驗證後才收緊 |
| VPS-015 | Phase 1 | 安裝 Docker 與 Nginx | 待處理 | 使用官方支援方式 |
| VPS-016 | Phase 2 | 建立測試子網域 | 待處理 | 不影響正式網域 |
| VPS-017 | Phase 3 | 部署 backend 並連 Supabase | 待處理 | 先做 read-only 驗證 |
| VPS-018 | Phase 4 | 部署 Vue 與 `/api` proxy | 待處理 | 完成 smoke test |
| VPS-019 | Phase 5 | 申請 HTTPS 與驗證續期 | 待處理 | `renew --dry-run` |
| VPS-020 | Phase 6 | 重啟與 rollback 演練 | 待處理 | 切正式流量前必須完成 |
| VPS-021 | Phase 7 | 正式 DNS 切換 | 待處理 | 需使用者再次明確確認 |
| VPS-022 | Phase 8 | 觀察與 Render 退場評估 | 待處理 | 不自動關閉 Render |
| VPS-023 | 未來 | 評估自管 PostgreSQL | 暫緩 | 另立獨立計畫 |
| VPS-024 | 未來 | 評估全容器化與 CI/CD | 暫緩 | VPS 第一版穩定後再做 |

---

## 二十四、每次執行的紀錄格式

每次完成一個 VPS 編號後，至少記錄：

```text
日期：
項目：VPS-XXX
執行環境：本機／測試 VPS／正式 VPS
目標：
實際操作摘要：
修改的檔案或主機設定：
驗證指令：
驗證結果：
遇到的問題：
是否需要 rollback：
後續待辦：
相關 commit：
```

不得把 secret、完整連線字串、私鑰或資料庫內容寫入紀錄。

---

## 二十五、名詞解釋

### VPS

供應商提供的一台虛擬 Linux 主機。使用者擁有較完整控制權，也必須自行負責安全、更新與服務維運。

### PaaS

像 Render 一類協助管理建置、啟動、網路入口與部分維運的平台。

### Nginx

HTTP Web Server 與 reverse proxy。此方案中負責提供 Vue 靜態檔案、接收 HTTPS，並把 `/api` 轉給 Spring Boot。

### Reverse Proxy

對外接收請求，再轉交內部服務。瀏覽器只看到 Nginx，不直接接觸 Spring Boot 的 8080。

### Certbot

自動化申請與續期 Let’s Encrypt TLS 憑證的工具，可與 Nginx 整合。

### DNS

將網域名稱解析為伺服器 IP 的系統。

### TLS／HTTPS

加密瀏覽器與伺服器之間的流量，避免登入憑證與 JWT 以明文傳輸。

### SPA fallback

Vue Router 使用瀏覽器路由時，Nginx 找不到實體檔案便回傳 `index.html`，交由 Vue 決定顯示頁面。

### Docker Image

包含應用程式與執行環境的不可變建置產物。

### Docker Container

Docker image 啟動後的執行實例。

### Health Check

用於確認服務是否實際可提供功能，而不只確認 process 存在。

### Smoke Test

部署後針對核心路徑進行的快速驗證，確認網站沒有明顯失效。

### Rollback

新版部署失敗時，回到上一個已知可用版本或原 Render 環境。

### TTL

DNS resolver 可以快取 record 的時間。TTL 較長時，切換後不同使用者可能較久才取得新位置。

### Migration

以可追蹤、可重複的版本化方式修改資料庫 schema，不依賴 Hibernate 自動推測更新。

### RPO／RTO

- RPO：事故後最多可接受遺失多少時間的資料。
- RTO：事故後最多可接受花多少時間恢復服務。

---

## 二十六、決策紀錄

| 日期 | 決策 | 原因 |
|---|---|---|
| 2026-08-12 | VPS 第一版選擇 Nginx，而非 Caddy | 延續 MVP 規劃、學習資源多、具通用部署價值 |
| 2026-08-12 | Nginx 與 Certbot 安裝在 VPS 主機 | 避免第一次部署就處理憑證容器共享與啟動順序 |
| 2026-08-12 | Vue 第一版不建立 runtime container | Vue `dist` 可由主機 Nginx 直接提供，降低複雜度 |
| 2026-08-12 | Spring Boot 使用 Docker | Repository 已有後端 Dockerfile，可保留可重複 runtime |
| 2026-08-12 | PostgreSQL 暫留 Supabase | 將主機遷移與正式資料遷移拆開，降低資料風險 |
| 2026-08-12 | Render 在觀察期保留 | 提供 DNS rollback 與既有 production 回退能力 |
| 2026-08-12 | 不要求先建立完整本機 mock production | 現有 Render 已是真實 production；主機 Nginx、systemd、DNS、TLS 應於測試 VPS 驗證 |

---

## 二十七、下一步

本文件建立後的下一個任務應為 **VPS-001：唯讀盤點 Render 現有部署設定**。

該任務只記錄非敏感資訊，例如：

- 前端服務類型。
- 前端 build command 與 publish directory。
- SPA rewrite 規則。
- 後端 build command 與 start command。
- health check path。
- 使用到的環境變數名稱，不記錄值。
- 正式網域與 DNS record 類型。

盤點完成後，再針對 Phase 0 的 repository 修改提出具體檔案計畫，等待使用者確認後實作。不應直接跳到購買 VPS 或切換正式網域。
