# Echo Gallery VPS 部署操作手冊

本手冊對應 `docs/13_VPS部署設計與執行計畫.md` 的 VPS Deployment v1。第一版由主機 Nginx 提供 Vue 靜態檔案，Spring Boot 由 Docker Compose 啟動，正式資料繼續保存在 Supabase PostgreSQL。

## 一、安全邊界

- 不在 repository、指令歷史或部署紀錄中保存真實密碼、JWT secret 或完整資料庫連線字串。
- PostgreSQL 不部署至 VPS，也不公開 5432。
- Spring Boot 只綁定 `127.0.0.1:8080`，外部流量統一經過 Nginx。
- JDBC URL 不放 username 或 password；帳號與密碼使用獨立環境變數，URL 要求 PostgreSQL TLS。
- HTTP 階段只供 ACME challenge 與 HTTPS redirect 驗證；HTTPS 完成前不登入或輸入任何憑證。
- DuckDNS、Certbot 與未來的 Render 退場必須依 `docs/13_VPS部署設計與執行計畫.md` 分階段執行。
- Render 既有網址在觀察期內保留，作為 VPS 發生問題時的替代入口。

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
/etc/letsencrypt/                        Certbot 管理的憑證；不得加入 repository
```

### 3.1 日常登入與主機基線檢查

從 Windows PowerShell 登入；`<VPS_PUBLIC_IP>` 只在本機使用，不寫進 repository：

```powershell
ssh -i "$env:USERPROFILE\.ssh\echo_gallery_vps" deploy@<VPS_PUBLIC_IP>
```

登入後確認身分、時間、記憶體、swap、磁碟、防火牆與服務：

```bash
whoami
hostname
timedatectl
free -h
swapon --show
df -h /
sudo ufw status verbose
systemctl is-active docker
systemctl is-active nginx
```

日常操作使用 `deploy`，需要系統權限時才加 `sudo`。SSH key／第二條連線尚未驗證前，不得關閉 root 或 password login；既有 VPS 已完成 `PermitRootLogin no`、`PubkeyAuthentication yes`、`PasswordAuthentication no` 與 `KbdInteractiveAuthentication no` 驗證。

### 3.2 首次建立管理帳號與 SSH 強化（已完成紀錄）

以下是這台 VPS 第一次初始化時使用的流程，保留作為日後重建主機的依據。**現有主機已完成，不要再次建立同名帳號或重複附加設定。**

先由仍可登入的 `root` 工作階段建立 `deploy`，並把既有的 SSH 公鑰授權複製給它：

```bash
adduser deploy
usermod -aG sudo deploy
install -d -m 700 -o deploy -g deploy /home/deploy/.ssh
install -m 600 -o deploy -g deploy \
  /root/.ssh/authorized_keys \
  /home/deploy/.ssh/authorized_keys

id deploy
stat -c '%U %G %a %n' \
  /home/deploy/.ssh \
  /home/deploy/.ssh/authorized_keys
sudo -l -U deploy
```

從本機另開一個終端，以 `deploy` 建立新連線並確認 `sudo whoami` 會輸出 `root`。只有這項驗證成功後，才停用 root 與密碼式 SSH 登入：

```bash
printf '%s\n' \
  'PermitRootLogin no' \
  'PubkeyAuthentication yes' \
  'PasswordAuthentication no' \
  'KbdInteractiveAuthentication no' \
  | sudo tee /etc/ssh/sshd_config.d/00-echo-gallery-hardening.conf >/dev/null

sudo /usr/sbin/sshd -t
sudo /usr/sbin/sshd -T \
  | grep -E '^(permitrootlogin|passwordauthentication|kbdinteractiveauthentication|pubkeyauthentication) '
sudo systemctl reload ssh
```

套用後必須保留原有 `deploy` 工作階段，另開新視窗驗證：

- `deploy` 新連線成功；
- `root` 新連線失敗；
- 原有 `deploy` 連線仍可使用。

### 3.3 首次作業系統初始化（已完成紀錄）

設定台北時區：

```bash
sudo timedatectl set-timezone Asia/Taipei
timedatectl
```

這台 2 GB RAM VPS 建立了 2 GB swap。下列建立指令只適用於 `swapon --show` 沒有結果、而且 `/swapfile` 尚不存在的新主機；現有主機不要重跑：

```bash
swapon --show
ls -lh /swapfile

sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
sudo systemctl daemon-reload

swapon --show
free -h
grep -n '/swapfile' /etc/fstab
```

UFW 採用「預設拒絕外部主動連線，只允許 SSH、HTTP、HTTPS」。一定要先允許 `22/tcp`，並確認第二條 SSH 連線成功，再啟用防火牆：

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp comment 'SSH'
sudo ufw allow 80/tcp comment 'HTTP'
sudo ufw allow 443/tcp comment 'HTTPS'
sudo ufw show added
sudo ufw enable
sudo ufw status verbose
```

### 3.4 Docker 安裝基準（Ubuntu 24.04 / amd64）

本次從 Docker 官方 Ubuntu 套件庫安裝。未來重建時應先對照 Docker 官方文件，確認 repository 與套件名稱仍適用，再執行這份歷史基準：

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

printf '%s\n' \
  'Types: deb' \
  'URIs: https://download.docker.com/linux/ubuntu' \
  'Suites: noble' \
  'Components: stable' \
  'Architectures: amd64' \
  'Signed-By: /etc/apt/keyrings/docker.asc' \
  | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null

sudo apt update
sudo apt install -y \
  docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

systemctl is-active docker
systemctl is-enabled docker
sudo docker --version
sudo docker compose version
sudo docker run --rm hello-world
```

本次驗證基準是 Docker `29.7.2`、Docker Compose `v5.5.0`；這是部署當時的版本紀錄，不是要求未來固定使用相同版本。

## 四、建立 backend secrets

先由 `deploy/backend.env.example` 核對需要的變數名稱，再於 VPS repository 外建立 `/etc/echo-gallery/backend.env`。實際檔案至少需要：

```text
JDBC_DATABASE_URL
DB_USERNAME
DB_PASSWORD
JWT_SECRET
FRONTEND_URL
DB_POOL_MAX_SIZE
DB_POOL_MIN_IDLE
JAVA_TOOL_OPTIONS
```

`DB_POOL_MAX_SIZE=3` 與 `DB_POOL_MIN_IDLE=1` 是共用 Supabase Session Pooler 時的保守起始值。若未設定，應用程式也會採用相同預設值；調高前必須把 Render 舊／新 instance、VPS 與其他資料庫 client 一併計入連線預算。

設定結構應保持分離：`JDBC_DATABASE_URL` 只包含 host、port、database 與 `sslmode=require`；`DB_USERNAME`、`DB_PASSWORD` 各自保存。不得把 `user` 或 `password` 放進 JDBC URL query parameter，避免 Hibernate 將完整 URL 寫入 log。

完成後確認檔案只允許 root 與部署管理者讀取。不要把實際內容貼進 issue、聊天、log 或 commit。

## 五、啟動後端

從 repository 根目錄執行：

```bash
cd /opt/echo-gallery
APP_VERSION=$(git rev-parse --short HEAD)

sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml config --quiet
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml build backend
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml up -d backend
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
```

預期 Compose 顯示 backend 為 running／healthy，health endpoint 回傳成功。若 production profile 的 schema validation 失敗、Supabase TLS 失敗或 log 出現秘密，立即停止，不繼續設定公開流量。

查看後端 log 時限制輸出範圍，並確認內容不含秘密：

```bash
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml logs --tail=200 backend
```

若只要確認 log 是否出現 `password=`，不可輸出匹配行：

```bash
if sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml logs --no-color backend 2>&1 | grep -q 'password='; then
  echo "WARNING: log contains password parameter"
else
  echo "OK: log does not contain password parameter"
fi
```

## 六、發布 Vue

VPS 不永久安裝 Node。首次部署驗證使用 Node 22.20.0 與 npm 11.6.1 的暫時 container；版本是已驗證基線，不代表永遠不得升級。從 repository 根目錄執行：

```bash
sudo docker run --rm \
  --user "$(id -u):$(id -g)" \
  --env HOME=/tmp \
  --volume /opt/echo-gallery/echo-gallery-frontend:/app \
  --workdir /app \
  node:22.20.0-bookworm-slim \
  sh -c 'npx --yes npm@11.6.1 ci && npm run test:run && npm run build'
```

將 `dist/` 內容發布到以 commit SHA 命名的新 release 目錄，再把 `/var/www/echo-gallery/current` symlink 切向該版本：

```bash
APP_VERSION=$(git rev-parse --short HEAD)
sudo install -d -m 755 "/var/www/echo-gallery/releases/$APP_VERSION"
sudo cp -a /opt/echo-gallery/echo-gallery-frontend/dist/. "/var/www/echo-gallery/releases/$APP_VERSION/"
sudo chown -R root:root "/var/www/echo-gallery/releases/$APP_VERSION"
sudo ln -sfn "/var/www/echo-gallery/releases/$APP_VERSION" /var/www/echo-gallery/current
readlink -f /var/www/echo-gallery/current
```

不要直接覆蓋目前 release，才能在前端異常時快速切回上一版。若 `npm ci` 回報 `package.json` 與 lockfile 不一致，停止並在本機或修正分支更新 lockfile；不得在 VPS 改用未審查的 `npm install` 或 `npm audit fix --force`。

Repository 的 `.env.production` 將 `VITE_API_BASE_URL` 設為同源 `/api`，因此 VPS 執行一般 production build 時不需額外覆蓋。Render Static Site 則由 Dashboard 的同名環境變數在 build 階段覆蓋為 Render 後端位置；兩邊都使用 `npm run build`，但部署平台負責提供不同設定。

發布 VPS 產物前，必須確認 build 環境沒有殘留 Render 或 localhost 的 `VITE_API_BASE_URL`。部署後再從瀏覽器 Network 驗證 Request URL 使用目前 VPS 網域下的 `/api`，而不是直接呼叫 Render 後端。

## 七、設定 Nginx

以 `<DOMAIN>` 代表實際網域：

```bash
sudo cp /opt/echo-gallery/deploy/nginx/echo-gallery.conf.example /etc/nginx/sites-available/echo-gallery
sudo nano /etc/nginx/sites-available/echo-gallery
sudo ln -sfn /etc/nginx/sites-available/echo-gallery /etc/nginx/sites-enabled/echo-gallery
sudo unlink /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl enable --now nginx
sudo systemctl reload nginx
```

在編輯器中將 `gallery.example.com` 改成 `<DOMAIN>`，並確認 `root` 指向 `/var/www/echo-gallery/current`、`/api/` 代理至 `http://127.0.0.1:8080`。若 `default` symlink 已不存在，`unlink` 顯示找不到檔案即可略過，不要刪除 `sites-available/default`。

先以 HTTP 測試首頁、SPA route 與 `/api`。DNS 正確生效後，再依當時 Certbot 官方安裝方式申請憑證；完成後驗證 HTTP 轉 HTTPS 與續期 dry-run。

首次部署使用 Certbot snap 與 Nginx plugin；未來執行前仍須核對官方支援方式：

```bash
sudo snap install --classic certbot
sudo ln -s /snap/bin/certbot /usr/local/bin/certbot
sudo certbot --nginx -d <DOMAIN> --redirect
sudo nginx -t
sudo certbot renew --dry-run
```

若 Certbot 或 symlink 已存在，不重複安裝或強制覆蓋；先以 `command -v certbot` 與 `certbot --version` 確認。DuckDNS token 不需要放入 VPS，除非未來明確建立 IP 自動更新流程；token 必須視為 secret。

## 八、Smoke test

依序驗證：

1. `curl --fail http://127.0.0.1:8080/actuator/health`；這是 backend health 的唯一既定檢查位置。
2. 瀏覽器開啟首頁及直接重新整理巢狀路由。
3. 確認瀏覽器請求 `/api`，且沒有直接存取 8080。
4. 登入並執行 Card 建立、讀取、修改及刪除。
5. 驗證標籤、Today、查詢及培育計畫核心流程。
6. 從手機行動網路重做登入與核心 Card CRUD。
7. 執行 `docker compose -f compose.production.yml down` 後重新 `up -d`，確認既有 Supabase 資料仍可存取。
8. 重開 VPS，確認 Nginx、Docker 與 backend 自動恢復。

公開網域的 `/actuator/health` 未由 Nginx 代理，會落入 Vue SPA fallback；收到 `index.html` 不代表 backend 健康。外部 reverse proxy 可用未登入 API 的 401 驗證：

```bash
curl --silent --output /dev/null --write-out 'API HTTP %{http_code}\n' https://<DOMAIN>/api/sidebar/tags/top
```

VPS 內部 health、外部 HTTPS 與服務狀態的最小檢查：

```bash
systemctl is-active docker
systemctl is-active nginx
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
curl --silent --output /dev/null --write-out 'HTTPS %{http_code}\n' https://<DOMAIN>
```

## 九、應用更新與回退

### 9.1 一般更新

每次發布都記錄 commit SHA。先確認 VPS repository 沒有未提交修改，再更新指定部署分支：

```bash
cd /opt/echo-gallery
git status --short --branch
git pull --ff-only origin <DEPLOY_BRANCH>
APP_VERSION=$(git rev-parse --short HEAD)
```

依變更範圍重新 build backend 或 frontend。後端使用 SHA tag，不使用不可追溯的 `latest`；frontend 建立新 release 後才切換 `current` symlink。

### 9.2 Render 與 VPS 共用 Supabase 時的發布順序

Render 舊 instance、新部署 instance 與 VPS 會共同消耗 Supabase Session Pooler client 額度。即使新版本已限制 Hikari pool，第一次把修正部署至 Render 時仍有舊 instance 存在，採以下順序：

1. 確認 Render 目前可用，停止 VPS backend。
2. 將已驗證的部署分支 fast-forward 合併至 `main` 並 push。
3. 等待 Render backend／frontend 新版完成並驗證 Render health 與功能。
4. 恢復 VPS backend。
5. 再次確認 Render 與 VPS 同時 healthy，且 log 沒有 `EMAXCONNSESSION`。

停止與恢復 VPS backend：

```bash
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml stop backend
sudo env APP_VERSION="$APP_VERSION" docker compose -f compose.production.yml up -d backend
```

### 9.3 Backend 回退

若上一版 image 仍保存在 VPS，使用已驗證的 `<PREVIOUS_SHA>` 啟動，不需更動資料庫：

```bash
sudo env APP_VERSION=<PREVIOUS_SHA> docker compose -f compose.production.yml up -d backend
sudo env APP_VERSION=<PREVIOUS_SHA> docker compose -f compose.production.yml ps
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health
```

若 image 不存在，先切至已驗證 commit 並重新 build。涉及 schema migration 時不得只回退 image，必須依 migration 計畫處理相容性。

### 9.4 Frontend 回退

先列出既有 release，再將 symlink 切回已驗證的 `<PREVIOUS_SHA>`：

```bash
sudo ls -la /var/www/echo-gallery/releases
sudo ln -sfn "/var/www/echo-gallery/releases/<PREVIOUS_SHA>" /var/www/echo-gallery/current
readlink -f /var/www/echo-gallery/current
sudo nginx -t
sudo systemctl reload nginx
```

若 DuckDNS 網址出現登入、核心 Card 功能、TLS、後端持續重啟或資料完整性問題，停止新的變更、保存 log，並暫時改用既有 Render 網址。兩者目前使用不同網址，不需要為此修改 DuckDNS；未來若自有網域正式切到 VPS，才使用降低 TTL 後切回 Render 的 DNS 回退方案。
