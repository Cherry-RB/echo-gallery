先給你一個大大的擁抱。突然面對一整套標準的企業級架構（Spring Boot + Vue 3），覺得力不從心、跟不上程式碼，這是**完全正常**的！

當我們從「寫單一功能」跨入「架構設計」時，程式碼會突然像細胞分裂一樣，從一兩個檔案變成十幾個。這不是你的問題，而是因為現代軟體工程為了追求**可維護性**與**安全性**，把責任切得很細。

我們不聊程式碼細節了，這一次，我們把視角拉高，用最白話、最直覺的邏輯，把這三個讓你困擾的大坑一次理清楚。

---

## 一、 CORS 機制：瀏覽器的「外交保護令」

### 1. 為什麼會發生 CORS？

你的前端（Vue 3）跑在 `http://localhost:5173`，後端（Spring Boot）跑在 `http://localhost:8080`。
對瀏覽器來說，**埠號（Port）不同，就是不同的世界（不同來源）**。

為了保護使用者，瀏覽器有一條鐵律叫「同源政策（Same-Origin Policy）」。如果 A 世界的網頁（5173）隨便用 JavaScript 去撈 B 世界（8080）的資料，瀏覽器會基於安全理由**直接在前端把它攔截並丟出錯誤**。

### 2. CORS 是如何運作的？

CORS（跨來源資源共享）其實是後端給瀏覽器的「白名單通行證」。

1. **預檢請求 (Preflight)**：當前端要發送像 `POST` 或帶有自訂 Header（例如 `Authorization`）的請求時，瀏覽器會偷偷先發送一個 `OPTIONS` 請求問後端：「欸，5173 埠的人想跟你要資料，可以嗎？」
2. **後端回應**：後端如果設定了 CORS 配置，就會回傳 Header 說：「可以，我允許 `http://localhost:5173` 來存取。」
3. **正式送出**：瀏覽器收到通行證，才放行前端真正的 `POST` 請求。

---

## 二、 BatchSize 機制：如何將 N+1 變成 1+1？

這是資料庫效能優化的經典題。假設一個情境：**你要撈出 3 個使用者（User），並顯示他們各自擁有的卡片（Card）。**

### 1. 什麼是可怕的 N+1 問題？

如果沒有任何優化，JPA/Hibernate 會這樣做：

* **1 次查詢**：`SELECT * FROM users;`（撈出 User A, B, C）
* **N 次查詢**（因為有 3 個 User，所以 N=3）：
* `SELECT * FROM cards WHERE user_id = A;`
* `SELECT * FROM cards WHERE user_id = B;`
* `SELECT * FROM cards WHERE user_id = C;`



總共執行了 $1 + 3 = 4$ 次 SQL。如果今天有一萬個使用者，資料庫就會被炸滿 10001 次查詢，系統直接癱瘓。

### 2. @BatchSize 如何把它變成 1+1？

當你在關聯欄位加上 `@BatchSize(size = 100)` 時，Hibernate 的底層實作會變聰明。它會開啟一個「收集器」，**等所有人排好隊，一次進去撈**。

* **1 次查詢**：`SELECT * FROM users;`（得到 A, B, C）
* **底層實作的魔法（變成了 1 次 IN 查詢）**：
Hibernate 發現你需要卡片，它不會笨笨地一個個查，而是把剛剛查到的 A, B, C 的 ID 收集起來，編成一條 SQL：
`SELECT * FROM cards WHERE user_id IN (A, B, C);`

看出來了嗎？原本要跑 4 次的 SQL，現在**雷打不動就是 2 次（1+1）**。這就是 `@BatchSize` 的底層原理：利用 **SQL 的 `IN` 子句**，把多筆重複的查詢打包成一整批（Batch）送出。

---

## 三、 權控機制與檔案大解密：為什麼要寫這麼多檔案？

這是你目前最大的痛點：「為什麼不能全寫在一起？」
答案是：**可以寫在一起，但代價是「牽一髮動全身」的災難。**

如果把門禁、驗票、查資料、做生意全部寫在同一個檔案裡，那個檔案會變成幾千行的「上帝類別（God Class）」，以後只要改個密碼規則，整間公司的系統可能都會壞掉。現代架構講求「單一職責原則（SRP）」，每個人只做好一件事。

我們用一個「高檔藝廊（Echo Gallery）的實體門禁系統」來比喻你建立的所有檔案：

### 1. Stateful（有狀態）vs Stateless（無狀態）

| 機制 | 實體比喻 | 優點 | 缺點 |
| --- | --- | --- | --- |
| **Stateful (Session)** | 藝廊門口設有**置物櫃與名冊**，每個人進來都要登記，並拿到一個置物櫃號碼（Session ID）。 | 後端掌控權極高，隨時可以把某個人的置物櫃鎖起來（強制登出）。 | 佔用伺服器記憶體。如果開連鎖藝廊（多台伺服器），名冊很難同步。 |
| **Stateless (JWT)** | 藝廊不設名冊。後端直接發給你一張**蓋有數位防偽印章的門票（Token）**。 | 後端完全不佔記憶體，擴展性極高，多台伺服器都能靠同個密鑰驗票。 | 門票發出去就收不回來（除非引入 Redis 黑名單），登出較難處理。 |

---

### 2. 後端檔案大拆解：他們在門禁系統裡扮演什麼角色？

#### 🚪 `security` 套件：負責「攔截與驗票」（保安部隊）

這是 Spring Security 的核心，專門在請求到達你的業務邏輯之前進行攔截。

* **`SecurityConfig.java`【藝廊的建築藍圖與門禁規則】**
* **目的**：告訴系統誰可以進來。例如：設定 `/api/auth/` 不需要門票（允許所有人去售票處），其餘路徑一律要驗票；配置 `BCryptPasswordEncoder` 密碼加密器。


* **`JwtAuthenticationFilter.java`【門口的驗票保安】**
* **目的**：所有請求進來，他第一個攔下。檢查請求頭有沒有帶 `Bearer Token`。有帶的話，拆下來交給 `JwtService` 驗證。


* **`JwtService.java`【驗票機/製票機】**
* **目的**：純數學與邏輯工具。負責用 `SECRET_KEY` 去解鎖 Token，看看有沒有過期、簽章對不對，並從裡面讀出使用者的 Email。


* **`CustomUserDetailsService.java`【檔案室管理員】**
* **目的**：當驗票保安知道 Email 後，會叫這個管理員去資料庫（檔案室）把這個人的詳細資料調出來。


* **`CustomUserDetails.java`【標準規格的身分證複印本】**
* **目的**：因為 Spring Security 只認得它自己規範的 `UserDetails` 介面。這個檔案就像是一個轉接頭，把你自訂的 `User` 實體，包裝成 Spring Security 看得懂的標準身分證格式。



#### 💼 `user` 套件：負責「開門後的業務邏輯」（櫃檯與行政）

當保安放行後，請求才會真正進到這裡。

* **`User.java`【資料庫的員工表格藍圖】**
* **目的**：對應資料庫 `users` 資料表的實體（Entity）。


* **`UserRepository.java`【檔案室索引工具】**
* **目的**：用來對資料庫下 SQL 的介面（例如 `findByEmail`）。


* **`AuthDto.java`【標準公文信封】**
* **目的**：規範前端傳進來的資料格式（例如註冊時只能傳 username, email, password），不讓多餘或危險的欄位污染後端。


* **`AuthService.java`【業務經理】**
* **目的**：處理核心邏輯。註冊時負責叫加密器把密碼變成 `passwordHash` 並存檔；登入時負責比對密碼，成功後叫 `JwtService` 簽發一張新 Token 員工證。


* **`AuthController.java`【臨櫃窗口/門市櫃檯】**
* **目的**：暴露 HTTP 網址端點（如 `/api/auth/login`），接收前端的請求，並把結果包成 `ResponseEntity` 奉還給前端。



#### 🛠️ `util` 套件

* **`SecurityUtil.java`【萬用工具夾】**
* **目的**：讓你可以在系統的任何地方，一秒鐘撈出「當前登入的人是誰」，不用每次都寫一長串程式碼。



---

### 3. 前端檔案：硬體與人的互動

* **`auth.ts`【前端的專屬快遞員】**
* **目的**：把 `axios` 封裝起來。當 Vue 頁面需要登入或註冊時，呼叫這個快遞員，他就會自動把資料送到後端的 `/api/auth/login` 櫃檯。


* **`Login.vue`【大廳的互動觸控螢幕】**
* **目的**：使用者看得到的介面。收集使用者輸入的帳密，按下去之後觸發快遞員（`auth.ts`），收到 Token 後偷偷藏進口袋（`localStorage`）。



---

### 💡 總結：請放輕鬆，這是一條必經之路

你之所以會覺得跟不上，是因為你正在從「寫小玩具」**跨越到**「理解工業級架構的分工」。

不要試圖一次背下所有的檔案。每次卡住時，就對著上面那張「藝廊門禁」的比喻看一眼：

> *「喔！我現在要調整密碼長度限制，那是業務邏輯，我要去改 `AuthService` 或 `AuthDto`。」*
> *「喔！我想讓某個新的 API 不需要登入就能看，那是門禁規則，我要去改 `SecurityConfig`。」*

分工明確之後，你會發現雖然檔案多，但每個檔案都出奇的短、出奇的簡單。

看完了這三個機制的白話大解密，這三個概念（CORS、BatchSize、Spring Security 分工）在你的腦海中有沒有稍微連成一條清晰的線了？哪一個部分是你最希望我們接下來用實例再深入聊聊的？

=======================================================================

這是一份為你量身打造、專門用來應徵 **Middle Java 全端工程師**等級的複習與核心學習筆記。

我幫你把先前的白話比喻升級為**面試官想聽的專業術語（例如：Separation of Concerns, Preflight Request, In-clause 批次查詢、Filter Chain 等）**，並採用極高 scannability 的 Markdown 結構。你可以直接複製這段內容存成 `.md` 檔案留存。

---

# 🚀 Middle Java 全端工程師核心技術筆記：架構、優化與安全控制

## 一、 跨域資源共享 (CORS) 機制與底層原理

在前後端分離架構中（如 Vue 3 + Spring Boot），前端與後端通常部署在不同的網域、通訊協定或埠號（Port），此時必然會觸發瀏覽器的**同源政策 (Same-Origin Policy)** 限制。

### 1. 瀏覽器的保護機制：預檢請求 (Preflight Request)

當前端發送**非簡單請求**（例如：使用 `POST/PUT/DELETE`、`Content-Type` 為 `application/json`，或請求頭包含自訂的 `Authorization` Token）時，瀏覽器會自動啟動以下機制：

1. **第一階段：發送 OPTIONS 請求（預檢請求）**
* 瀏覽器會先向後端發送一個 HTTP `OPTIONS` 請求，帶有 `Origin`、`Access-Control-Request-Method` 與 `Access-Control-Request-Headers` 等標頭。
* **目的**：詢問後端伺服器：「我來自 5173 埠，打算用 Bearer Token 發送 POST 請求，你允許嗎？」


2. **第二階段：後端回應與白名單驗證**
* 後端若配置了 CORS 允許該來源，會回傳 `200 OK` 並附帶相關 Headers：
* `Access-Control-Allow-Origin: http://localhost:5173`
* `Access-Control-Allow-Methods: POST, GET, OPTIONS, DELETE`
* `Access-Control-Allow-Headers: Authorization, Content-Type`




3. **第三階段：正式請求送出**
* 瀏覽器驗證後端回傳的 Headers 符合預期後，才會真正送出前端寫的 `POST /api/auth/login` 請求。



> **💡 面試加分點（Middle 等級必備認知）**
> 如果後端配置 CORS 失敗，錯誤通常是**瀏覽器攔截了回應**，而非後端沒有執行程式碼。後端其實有收到請求並處理完畢，只是回傳時沒有帶上正確的 CORS Headers，導致瀏覽器拒絕將資料交給前端的 JavaScript。

---

## 二、 JPA / Hibernate 效能優化：`@BatchSize` 解決 N+1 問題

### 1. 什麼是 N+1 查詢問題？

在定義一對多（`@OneToMany`）或多對一（`@ManyToOne`）的關聯實體時（例如：一個 `User` 擁有多張 `Card`），若採用 `FetchType.LAZY`（延遲載入），當我們查出 $N$ 筆 User 資料並巡覽（Loop）存取各自的 Card 列表時：

* **1 次主要查詢**：`SELECT * FROM users;`（查出 $N$ 個使用者）
* **N 次次要查詢**：針對每個使用者，各自發送一次 `SELECT * FROM cards WHERE user_id = ?;`

這會導致資料庫連接被頻繁佔用，造成嚴重的效能瓶頸。

### 2. `@BatchSize` 的底層實作與降維打擊

在關聯欄位或實體類別上加上 `@BatchSize(size = 100)`，Hibernate 的底層驅動邏輯會從「逐條查詢」轉變為「收集 ID 進行批次加載」。

* **底層機制**：
Hibernate 內部會維護一個批次加載佇列。當偵測到程式碼開始存取第一個 User 的 Card 列表時，它不會立刻只查該 User 的 Card，而是**一口氣把當前 Persistent Context 中最多 100 個未載入的 User ID 收集起來**。
* **SQL 轉化**：
原先的 $N$ 次單條 `WHERE user_id = ?` 查詢，會被底層重構為單一條使用 **`IN` 子句** 的 SQL：
`SELECT * FROM cards WHERE user_id IN (1, 2, 3, ..., 100);`

> **📊 效能收益公式**
> 查詢次數從原先的 $1 + N$ 次，大幅優化降至 $1 + \lceil N / \text{BatchSize} \rceil$ 次。以 3 筆資料為例，直接從 $1+3=4$ 次轉化為 $1+1=2$ 次（即 1+1 機制）。

---

## 三、 企業級權控架構：Spring Security + JWT 縱深防禦

前後端分離專案多採用 **Stateless（無狀態）** 的 JWT 架構，後端不維護 HTTP Session，完全仰賴簽章（Signature）進行身份校驗。

### 1. 架構優缺點評估 (Stateful vs Stateless)

* **Stateful (Session-based)**：後端記憶體壓力大；多台伺服器時需引入 Spring Session 與 Redis 進行分散式 Session 同步。
* **Stateless (JWT-based)**：伺服器具備極佳的水平擴展性（Scalability）；缺點是 Token 一旦簽發，在過期前難以主動使其失效（除非設計 Redis Token 黑名單機制）。

---

### 2. 後端架構元件分工表（關注點分離 Separation of Concerns）

Spring Security 的本質是一個 **Filter Chain（過濾器鏈）**。請求在抵達 Controller 之前，會依序通過各個專職的過濾器。

| 檔案名稱 | 核心職責（Responsibility） | 建立/調整之目的 |
| --- | --- | --- |
| **`SecurityConfig.java`** | **安全配置核心藍圖** | 定義過濾器鏈行為。配置哪些 URL 不需要驗證（如 `/api/auth/**`），哪些需要；將自訂的 `JwtAuthenticationFilter` 加進過濾鏈中；注入 `AuthenticationManager` 與 `PasswordEncoder`（BCrypt）。 |
| **`JwtAuthenticationFilter.java`** | **自訂請求攔截器 (Filter)** | 繼承 `OncePerRequestFilter`。攔截所有進入系統的 HTTP 請求，從 Header 擷取 `Bearer <Token>`。若存在，則交由 `JwtService` 驗證，並在驗證成功後將權限寫入 `SecurityContextHolder`。 |
| **`JwtService.java`** | **Token 封裝與解析工具** | 專職處理 JWT 的加密（`sign`）、解密（`parse`）、檢查過期（`isTokenExpired`）以及從 Claims 中提取使用者帳號。不涉及任何資料庫操作。 |
| **`CustomUserDetailsService.java`** | **身分資料載入實作** | 實作 Spring Security 的 `UserDetailsService` 介面。唯一的任務是定義 `loadUserByUsername(String email)`，透過 `UserRepository` 去資料庫撈出對應的真實數據。 |
| **`CustomUserDetails.java`** | **領域模型轉接器 (Adapter)** | 實作 `UserDetails` 介面。因為 Spring Security 不認得我們自訂的 `User` Entity，此檔案作為結構適配器（Adapter Pattern），將 `com.echogallery.user.User` 包裝成安全框架看得懂的標準規格。 |
| **`User.java`** | **資料庫實體 (Entity)** | ORM 映射物件，對應資料庫的 `users` 表格。 |
| **`UserRepository.java`** | **資料存取層 (Repository)** | 繼承 `JpaRepository`，封裝底層的 SQL 操作，提供 `findByEmail` 等高階抽象方法。 |
| **`AuthDto.java`** | **資料傳輸物件 (DTO)** | 內部包含 `RegisterRequest`、`LoginRequest` 與 `AuthResponse`。嚴格限制前後端通訊的欄位，避免過度暴露資料庫 Entity 結構（防止 Over-posting 攻擊）。 |
| **`AuthService.java`** | **安全業務邏輯層 (Service)** | 核心業務處理。註冊時調用 `PasswordEncoder` 將明文密碼雜湊化後存檔；登入時調用 `AuthenticationManager` 進行密碼驗證，成功後調用 `JwtService` 產生 Token。 |
| **`AuthController.java`** | **網路控制層 (Controller)** | 只負責對外暴露 HTTP 端點（如 `/api/auth/login`），接收反序列化後的 DTO 請求，並回傳標準的 `ResponseEntity`。不包含任何業務邏輯。 |
| **`SecurityUtil.java`** | **架構上下文中介工具** | 封裝 `SecurityContextHolder.getContext().getAuthentication()` 的寫法，讓整個系統不論在 Service 還是其他組件，都能強型別且優雅地一秒獲取當前登入者的 `userId`。 |

---

### 3. 前端架構元件分工

* **`auth.ts`（API 模組化封裝）**：利用宣告式的 API 物件，將前端的網路請求邏輯與 UI 元件完全解耦。未來若 API 路徑變更，只需修改此處，不需要改動任何 `.vue` 檔案。
* **`Login.vue`（檢視與防禦性 UI 互動）**：負責收集使用者互動資料，並透過組合式函數（如 `useAsync`）綁定非同步狀態，在發送請求時自動將按鈕設為 `disabled`，從**前端源頭防範使用者重複點擊引發的重複提交（Race Condition）問題**。

---

### ❓ 為什麼不把這些程式碼全部寫在一起？

如果在面試中被問到這題，Middle 工程師的最佳回答策略是：

> 「這基於軟體工程的**單一職職責原則 (Single Responsibility Principle)** 與**關注點分離 (Separation of Concerns)**。
> 如果將過濾、認證、資料庫存取與業務邏輯塞在同一個類別，會導致系統內部高度耦合（High Coupling），不僅難以進行單元測試（Unit Test），在未來需要變更需求（例如：從 JWT 改為 OAuth2，或者從 PostgreSQL 遷移到 MySQL）時，將引發難以預測的連帶壞死。
> 透過這樣分層，每個人只各司其職，後續維護、擴展與協同開發才能具備高度的靈活性。」

---

這份筆記把你的專案結構和全端觀念做了全面性的收網。你可以安心地把它儲存下來。在這個結構下，你已經把一個標準高規的企業級身分驗證骨架搭好了。

為了讓你應徵 Middle 職缺時更有把握，我們接下來要不要針對面試官很愛延伸的 **「如何防範 JWT 被竊取（XSS/CSRF）」** 或是 **「在 Spring Security 中如何處理自訂異常（例如密碼錯誤時如何精準回傳 JSON 而不是丟出 500 報錯）」** 來做進一步的架構補強？

=========================================================

既然你準備好了，那事不宜遲，我們直接把這兩個在面試中含金量極高、專門用來區分 Junior 與 Middle 等級的「面試大魔王」給攻破。

把它們補進你的知識庫後，你的全端架構邏輯就會變得非常嚴密。

---

## 魔王一：JWT 的安全性防禦（Token 到底該存哪？）

面試官非常喜歡問：「你前端把 Token 存在 `localStorage`，難道不怕被偷嗎？有沒有更安全的作法？」

### 1. LocalStorage vs HttpOnly Cookie

目前前端社群對於 Token 存放主要有兩種派系，各自有其防禦重點：

| 存放位置 | 潛在風險 | 防禦機制 | Middle 級別的權衡思維 |
| --- | --- | --- | --- |
| **LocalStorage** | **XSS 攻擊**<br>

<br>（跨網站指令碼攻擊）<br>

<br>駭客透過惡意腳本直接用 JavaScript 讀取 `localStorage` 把 Token 傳走。 | 確保前端有做好 **HTML 轉義（XSS Filtering）**。<br>

<br>（Vue 3 的 `{{ }}` 預設會自動轉義，但要避免濫用 `v-html`。） | **開發極為便利**，純前端即可控制。在有良好 XSS 防護的現代前端框架中，是中小型專案非常常見且可接受的方案。 |
| **HttpOnly Cookie** | **CSRF 攻擊**<br>

<br>（跨網站請求偽造）<br>

<br>JavaScript 讀不到 Cookie（防 XSS），但瀏覽器發送請求時會自動帶上 Cookie，駭客可誘導使用者點擊惡意連結來偽造請求。 | 後端設置 Cookie 時，必須加上：<br>

<br>1. `HttpOnly`（阻斷 JS 讀取）<br>

<br>2. `Secure`（僅限 HTTPS）<br>

<br>3. `SameSite=Strict`（**核心：阻斷 CSRF 跨站攜帶**） | **安全性最高**。由後端掌控 Token 的寫入與失效，是金融、電商等高安全性專案的標配。缺點是跨網域（Cross-domain）部署時配置較為繁瑣。 |

---

## 魔王二：Spring Security 的異常處理（Exception Handling）

這是一個非常經典的**架構陷阱題**。面試官會問：「如果使用者登入密碼輸入錯誤，或者 Token 過期，你要怎麼讓後端回傳標準的 JSON 錯誤格式（如 `{ "status": 401, "message": "Token 已過期" }`），而不是直接噴出 500 網頁錯誤？」

### 1. 為什麼一般的 `@ControllerAdvice` 會失效？

如果你之前學過 Spring Boot 的全域異常處理（`@ControllerAdvice` 或 `@RestControllerAdvice`），你會發現它在 Spring Security 面前**完全沒用**。

原因就在於我們前面提到的 **Filter Chain（過濾器鏈）** 運行順序：

看到了嗎？Spring Security 的過濾器（如 `JwtAuthenticationFilter`）是在**進入 Controller 之前**就執行的。當過濾器拋出 `ExpiredJwtException`（Token過期）時，請求根本還沒進到 DispatcherServlet，因此屬於 Controller 層的 `@ControllerAdvice` 根本攔截不到這個錯誤！

### 2. 正確的 Middle 級解決方案

我們必須實作 Spring Security 專門提供的兩個機制，並將它們註冊到 `SecurityConfig` 中：

* **`AuthenticationEntryPoint`（處理 401 未驗證）**：當匿名使用者嘗試存取受保護的資源，或者 Token 解析失敗、密碼錯誤時觸發。
* **`AccessDeniedHandler`（處理 403 權限不足）**：當使用者已經登入（有 Token），但嘗試存取他沒有權限的 API（例如一般用戶想呼叫管理員 API）時觸發。

#### 💡 程式碼實作概念（以 401 為例）：

我們會寫一個類別實作該介面，直接用 HttpServletResponse 把 JSON 寫回去：

```java
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, 
                         AuthenticationException authException) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        // 💡 手動將錯誤物件轉成 JSON 字串寫回前端
        response.getWriter().write("{\"status\": 401, \"message\": \"認證失敗，請重新登入\"}");
    }
}

```

然後在 `SecurityConfig.java` 裡面透過 `.exceptionHandling(ex -> ex.authenticationEntryPoint(...))` 注入進去。

---

掌握了這兩個觀念，你在面試 Java 全端職缺時，安全架構這一關基本上就能拿高分了，因為你具備了「知道代碼為何這樣寫」**以及**「了解底層生命週期」的架構師思維。

這兩個觀念是劃分 Junior 與 Middle 的關鍵分水嶺。你希望我們接下來先動手把「全域異常處理」補進你目前的 Spring Boot 程式碼中，還是先聊聊怎麼在前端防範 Token 被偷？

============================================================================

下一步怎麼在前端防範 Token 被偷?

============================================================================

這是一個非常經典且重要的全端資安課題！如果我們在前端把 Token 存在 `localStorage`，防範被偷的核心戰場就是**防範 XSS（跨網站指令碼攻擊）**。因為只要駭客能在你的網頁上執行隨便一段 JavaScript 程式碼，他就能透過 `localStorage.getItem('token')` 輕易把 Token 偷走。

要在前端做到工業級的防禦，通常有三個層次的作法。我們可以從你現有的 `localStorage` 優化開始，一路升級到業界最安全的「雙 Token 混合架構」。

---

## 第一層：死守 `localStorage` —— 徹底封殺 XSS 漏洞

既然 Token 放在 `localStorage` 怕被 JavaScript 讀取，那我們就要確保網頁上**絕對不能執行任何未經授權的惡意腳本**。

### 1. 嚴格禁用 `v-html` 渲染使用者輸入

Vue 3 預設的 `{{ expression }}` 會自動進行 HTML 轉義（Escaping），把 `<script>` 變成字串，這是安全的。但如果你使用了 `v-html`，Vue 就會放棄防禦。

```html
<div v-html="user.comment"></div>

<div>{{ user.comment }}</div>

```

### 2. 防範動態屬性注入（如 `javascript:` 連結）

有時候我們會讓使用者填寫個人網站連結，並用 `:href` 渲染。這也是一個常見的 XSS 漏洞。

```html
<a :href="user.website">個人網站</a>

```

**Middle 級防禦**：在綁定 `href` 前，必須先驗證網址是否以 `http://` 或 `https://` 開頭。

### 3. 配置網頁的 CSP 政策（Content Security Policy）

這是在 HTTP 回應頭（後端配置）或前端 HTML `<head>` 中加入的安全盾牌。它可以限制瀏覽器「只能執行來自特定網域的 JS 檔案」，就算駭客成功注入了 `<script>`，瀏覽器也會拒絕執行它。

```html
<meta http-equiv="Content-Security-Policy" content="default-src 'self'; script-src 'self';">

```

---

## 第二層：治本方案 —— 遷移至 HttpOnly Cookie（防禦 CSRF）

如果你希望 Token 「完全具備免疫 XSS 的體質」，最好的做法就是**不要讓 JavaScript 有機會碰到 Token**。這需要前後端協同調整。

### 1. 後端調整：改用 Cookie 發放 Token

在 `AuthController` 登入成功後，不把 Token 放在 JSON 回傳，而是塞進 HTTP Response 的 Cookie 裡：

```java
ResponseCookie cookie = ResponseCookie.from("token", jwtToken)
    .httpOnly(true)    // 🚀 核心：設定 HttpOnly，前端 JavaScript 徹底無法讀取（防 XSS）
    .secure(true)      // 僅限 HTTPS 傳輸
    .sameSite("Strict")// 🚀 核心：防範 CSRF 攻擊
    .path("/")
    .maxAge(86400)
    .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

```

### 2. 前端調整：Axios 開啟憑證攜帶

一旦改用 Cookie，前端的 `localStorage.setItem()` 程式碼全部刪除。前端連 Token 的影子都看不到。你只需要在 `request.js`（Axios 設定）中加上一行：

```javascript
const service = axios.create({
    baseURL: "/api",
    timeout: 10000,
    withCredentials: true // 🚀 核心：告訴 Axios 每次發請求都要自動帶上 Cookie
});

```

---

## 第三層：終極架構 —— 記憶體 Access Token + HttpOnly Refresh Token

這是目前大型 SPA 專案（如前後端分離的金融或企業系統）最推崇的**雙 Token 混合架構**，它完美平衡了 XSS 與 CSRF 的風險。

### 運作機制拆解：

1. **Access Token（短效期，如 15 分鐘）**：
* 登入成功後由後端經由 JSON 回傳。
* 前端**不要存進 localStorage**，而是直接存進 **Vue 的記憶體中（例如 Pinia 狀態管理、或是單純的全域變數）**。
* **效果**：因為在記憶體中，XSS 很難偷（除非刷新頁面，但刷新頁面變數就清空了）。


2. **Refresh Token（長效期，如 7 天）**：
* 登入成功後，後端透過 **HttpOnly Cookie** 寫入瀏覽器。
* **效果**：JS 碰不到，具備 XSS 免疫體質。



### 前端 Axios 的靜默刷新（Silent Refresh）

當使用者重新整理網頁，記憶體中的 Access Token 消失了，或者 15 分鐘後 Access Token 過期了，Axios 的回應攔截器會自動在幕後幫忙「續約」：

```javascript
// 回應攔截器
service.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;
        
        // 當後端回傳 401（Access Token 過期），且這條請求還沒重試過
        if (error.response.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            
            try {
                // 1. 呼叫重新整理 API（此時瀏覽器會自動帶上 HttpOnly 的 Refresh Token Cookie）
                const res = await axios.post("/api/auth/refresh", {}, { withCredentials: true });
                const newAccessToken = res.data.accessToken;
                
                // 2. 把新的 Token 存回記憶體（如 Pinia），並更新當前請求的 Header
                store.setToken(newAccessToken);
                originalRequest.headers["Authorization"] = `Bearer ${newAccessToken}`;
                
                // 3. 重新發送剛剛失敗的那條請求，使用者完全不會察覺網頁斷掉！ 💡
                return service(originalRequest);
            } catch (refreshError) {
                // 連 Refresh Token 都過期了，才真正強制跳轉登入頁
                router.push("/login");
            }
        }
        return Promise.reject(error);
    }
);

```

---

### 💡 Middle 工程師的面試總結思維

如果在面試中聊到前端 Token 資安，你可以這樣作結：

> 「如果專案時程緊湊，我們會採用 **LocalStorage + 嚴格的 Vue 轉義與 CSP 政策** 來防範 XSS。
> 但如果要追求更高的安全性，我會建議架構重構為 **雙 Token 機制**：將 Access Token 留在前端記憶體中，並將用於續期的 Refresh Token 綁定為後端的 **HttpOnly Cookie**。這樣既能防範 XSS 直接竊取長期憑證，又能透過短效期的 Token 機制將 CSRF 的攻擊面降到最低。」

這套大師級的前端防禦邏輯，在你的專案架構中理清楚了嗎？你想先嘗試在現有的 Vue 專案中引入 **Pinia** 來改寫記憶體 Token 的儲存，還是先維持 `localStorage` 並學習如何配置全域的 CSP 盾牌呢？

=============================================================

你的資安警覺度非常高！看到 `permitAll()` 加上 `/` 就直覺感到不安，這絕對是一個優秀工程師該有的「職業病」素養。

不過，這裡牽涉到瀏覽器的底層機制。如果我們把 CORS 錯誤留著，表面上看起來好像阻擋了請求，但實際上會**讓前端程式碼直接變成瞎子**，而且這對後端的防禦**完全沒有實質幫助**。

我們把這三個糾結點徹底拆解開來，你就會明白為什麼非得解決它不可。

---

## 1. 預檢請求（Preflight Request）到底是什麼？

當你的前端（`localhost:5173`）想要發送一個「可能會對後端資料造成變更」的請求（例如：JSON 格式的 POST、PUT、DELETE）給不同網域的後端時，瀏覽器為了安全，不會直接把請求衝撞過去。

瀏覽器會自動先發送一個**探路先鋒**，這個請求的 HTTP Method 就是 `OPTIONS`，俗稱**預檢請求**。

* **它的對話邏輯是這樣的：**
* **瀏覽器 (OPTIONS)**：「後端大佬，我等一下想幫 `localhost:5173` 傳一個 JSON 格式的 POST 請求過來，你家規矩允許嗎？」
* **後端回應**：「可以啊，我允許這個網域。」（或者不回應、拒絕）。
* **瀏覽器**：確認後端允許，才會真正把你的 `POST /api/cards/list` 發送出去。



---

## 2. 反正 Network 看得到 403，維持 CORS 錯誤不行嗎？

> **答案是：身為開發者的你，看 DevTools 當然看得到；但你的「前端程式碼（Axios）」完全看不到！**

這就是為什麼不能維持 CORS 錯誤的致命原因。

當瀏覽器觸發 CORS 錯誤時，它會基於保護隱私的原則，**對 JavaScript 進行嚴格的資訊封鎖**。後端其實有吐出 401 或 403 的 JSON 給瀏覽器，但瀏覽器一把攔下並氣沖沖地砸碎它，不給 Axios 看。

* **維持 CORS 錯誤的後果：**
你的 Axios 攔截器拿到的 `error.response` 會是 `undefined`，狀態碼會變成 `0` 或找不到，錯誤訊息永遠顯示模糊的 `Network Error`。
* **這會帶來什麼災難？**
前端程式碼**完全無法分辨**這到底是「使用者 Token 過期（401）」、「單純這條路徑沒權限（403）」、還是「後端伺服器整台爆炸斷線了」。既然無法分辨，你就沒辦法在程式碼裡寫 `if (status === 401) { router.push('/login') }` 來引導使用者重新登入，使用者體驗會直接崩潰。

---

## 3. 全放行 `OPTIONS /` 難道不危險嗎？設定白名單不就是要擋下？

別擔心，放行 `OPTIONS` **完全不會降低系統的安全性**，原因有兩個：

### 理由 A：`OPTIONS` 請求本身沒有殺傷力

`OPTIONS` 請求是純粹的「元數據（Metadata）詢問」，它**不允許攜帶任何業務資料（Request Body）**，也絕對不可能觸發你後端的資料庫修改或業務邏輯。它純粹就是一個傳話筒。

### 理由 B：真正的商務請求（GET/POST）依然被死死卡住

我們放行的只是 `HttpMethod.OPTIONS`，當預檢通過後，瀏覽器緊接著發出的 `POST /api/cards/list` 依然會撞上兩大防線：

1. **CORS 白名單檢查**：後端在 `corsConfigurationSource()` 裡依然有嚴格限制 `List.of("http://localhost:5173")`。如果是駭客的惡意網站（例如 `http://hacker.com`）發出 `OPTIONS`，後端回傳的標頭依然不會通過，瀏覽器一樣會阻擋。
2. **Spring Security 門禁**：真正的 `POST` 請求進來時，`.anyRequest().authenticated()` 依然在站崗，沒有攜帶合法 JWT Token 的請求，會立刻被 `CustomAuthenticationEntryPoint` 攔截，絕對進不去你的 Controller。

---

### 💡 總結

放行 `OPTIONS` 不是「對駭客開門」，而是「允許瀏覽器過來詢問我們的安檢規則」。只有讓這個詢問暢通，後端真正的 401/403 狀態碼才能順利傳遞到 Axios 手中，前端才能做出相應的彈出視窗或跳轉登入頁。

現在把 `HttpMethod.OPTIONS` 放行後，重啟後端，前端 Axios 是不是終於能抓到明明白白的 `401` 狀態碼，而不是模糊的 `Network Error` 了呢？

=======================================================================

太讚了！你抓到了這個超級隱蔽的魔鬼細節！

「後端因為編譯錯誤沒真的重啟成功」真的是 Java 開發者的共同陰影。有時候 IDE 表面上看起來在執行，但底層因為 `ObjectMapper` 的 import 噴錯，導致你更新的 CORS 設定根本沒有被載入，這真的會讓人抓漏抓到懷疑人生。

既然你發現註解掉 `HttpMethod.OPTIONS` 的放行規則後 401 依然能順利跑出來，那我要向你「自首」並強烈讚賞你的實驗精神——**你是完全對的，在標準的 Spring Security 6 架構下，確實不需要額外加那行放行！**

我們來還原一下，當 `ObjectMapper` 修正、專案真正成功重啟後，Spring 底層到底發生了什麼神奇的事：

---

## 為什麼現在不寫 `.permitAll()`，CORS 也能過？

這是因為 Spring Security 的 **`CorsFilter`（CORS 過濾器）順序極高**。

當你在 `SecurityConfig` 裡寫了 `.cors(cors -> cors.configurationSource(corsConfigurationSource()))`，Spring 會自動把一個 `CorsFilter` 塞進安全過濾器鏈中。

這個 `CorsFilter` 的位置非常特殊，它被排在**最前線**（甚至在你的 `JwtAuthenticationFilter` 和 `.authorizeHttpRequests()` 門禁檢查之前）。

### 真正的執行流程是這樣的：

1. **`OPTIONS` 預檢請求進來**：
第一關直接撞上 `CorsFilter`。
2. **`CorsFilter` 認出它**：
過濾器檢查發現：「喔！這是 `OPTIONS` 請求，而且來源 `localhost:5173` 就在我們的白名單裡！」
3. **直接攔截並回傳 200**：
`CorsFilter` 會在 Response 加上 CORS 標頭，然後**直接短路（Short-circuit）返回 200 OK** 給瀏覽器。這個 `OPTIONS` 請求**根本沒有機會**往下走到後面的 `.anyRequest().authenticated()` 門禁關卡。
4. **正式請求（POST）進來**：
瀏覽器滿意了，送出真正的 `POST`。再次通過 `CorsFilter`（加上標頭），然後往下走到門禁，因為沒帶 Token，順利觸發你寫好的 `CustomAuthenticationEntryPoint`，吐出 401 JSON。

---

## 💡 這堂盲測帶來的 Middle 級收穫

之前因為 `ObjectMapper` 導致配置沒生效，我們才會誤以為 `OPTIONS` 被門禁擋了。現在真相大白：

> **只要有正確配置 `.cors()` 並提供 `CorsConfigurationSource`，Spring Security 官方預設就會幫你完美處理好 `OPTIONS` 預檢請求，不需要自己手動寫 `permitAll()`。**

手動寫 `permitAll(HttpMethod.OPTIONS, "/")` 通常是用在「沒有開啟 Spring Security CORS，而是自己寫其他 interceptor 擋到」的偏方。你現在把這行拔掉，讓專案維持乾淨、由 `CorsFilter` 統一控管，才是最道地的 Spring 寫法！

現在前後端通訊的任督二脈徹底打通了，401 也能精準捕捉。下一步你打算回頭去優化 Vue 3 前端，讓 Axios 攔截到 401 時自動跳轉到登入頁，還是要繼續完善後端卡片列表 API 的分頁與效能處理呢？

============================================================================

