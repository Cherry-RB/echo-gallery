這份筆記我已經為你逐字、逐技術細節進行了 **Double Check**。

整體邏輯完全正確，技術細節（如 `NoClassDefFoundError` 阻斷 Spring 正常生命周期的機制、瀏覽器同源政策的攔截點、Vite Proxy 伺服器對伺服器通訊）皆符合目前主流全端開發的底層原理。

為了讓這份筆記更具結構性，且完全符合面試時的專業口吻，我微調了部分用詞（將過於口語的詞彙修正為台灣常見的資訊專業術語，如：`Package` 譯為「套件」、`Class` 譯為「類別」、`Exception` 譯為「異常/例外」）。

以下是為你精確校對後的**最終版學習筆記與面試擬答**：

---

# 學習筆記：CORS 機制、後端異常與 Dev Proxy 代理原理

本篇筆記專門梳理 **CORS 跨來源資源共享機制**、**後端異常引發的 CORS 假象**，以及**前端代理（Proxy）的繞過原理**。內容兼顧技術底層邏輯與面試實戰擬答面向。

---

## 一、 CORS 機制與同源政策（Same-Origin Policy）

### 1. 核心概念

* **同源政策（Same-Origin Policy）**：是「瀏覽器」的核心安全機制。當兩個 URL 的**協定（Protocol）**、**網域（Domain）**、連接埠（Port）完全相同時，才稱為「同源」。
* **CORS（Cross-Origin Resource Sharing，跨來源資源共享）**：當前端與後端不同源時（例如前端 `localhost:5173`，後端 `localhost:8080`），瀏覽器基於同源政策預設會阻擋跨源請求。CORS 是一種 W3C 標準，允許伺服器透過**特定 HTTP 回應標頭**，告訴瀏覽器放行該次跨源請求。

### 2. 瀏覽器攔截行為

當前端發起非同源的 Ajax 請求（如 Axios）時，**請求其實會成功到達後端，後端也會正常處理並回傳 Response**。但當 Response 回到瀏覽器時，瀏覽器會檢查回應標頭中是否包含 `Access-Control-Allow-Origin`。如果沒有，或者值不符合前端來源，瀏覽器就會**扣留回傳資料**，並在 Console 拋出 CORS 錯誤。

> 🗣️ **面試官提問：「什麼是 CORS？瀏覽器是如何攔截的？」**
> **💡 建議回答：**
> CORS 是跨來源資源共享。當前端與後端處於不同網域或連接埠時，瀏覽器會觸發同源政策。需要強調的是，**CORS 阻擋通常發生在「回應（Response）返回瀏覽器」的階段，而非「請求（Request）發出」的階段**。
> 後端其實有收到請求並處理完畢，但如果回應標頭（Response Headers）中沒有帶上 `Access-Control-Allow-Origin` 並指定允許該前端來源，瀏覽器基於安全機制就會攔截該筆資料，不讓前端 JavaScript 程式碼讀取。

---

## 二、 後端異常與「CORS 假象」

### 1. 為什麼後端報錯（500）會變成前端的 CORS 錯誤？

在 Spring Boot 專案中，我們雖然配置了 `WebMvcConfigurer` 的 `addCorsMappings` 來允許跨源，但這個配置是有**生命週期與觸發順序**的。

當一個請求進入 Spring Boot，它的處理鏈大致如下：
`請求進來` ➡️ `Filter (過濾器)` ➡️ `Interceptor (攔截器)` ➡️ `Controller/Service 業務邏輯` ➡️ `掛載 CORS 標頭` ➡️ `返回回應`

在本次異常日誌中顯示：
`Servlet.service() ... threw exception [Handler dispatch failed: java.lang.NoClassDefFoundError: com/util/SecurityUtil]`

* **根本原因**：`CardService` 在執行業務邏輯時，因為 `SecurityUtil` 的實體檔案與套件路徑（Package）置於主程式上一層，導致類別加載失敗（`ClassNotFoundException`），系統拋出嚴重的未捕獲錯誤（`NoClassDefFoundError`）。
* **CORS 失效原因**：當程式中途崩潰、直接向外拋出全域未處置的 Error 時，Spring Boot 會直接中斷常規的 Response 包裝流程。這導致請求**根本沒有走到掛載 CORS 標頭的步驟**。
* **結果**：瀏覽器收到一個沒有帶有 `Access-Control-Allow-Origin` 的 500 錯誤回應，瀏覽器不理會後端是否崩潰，只因為「沒有跨源通行標頭」而直接判定並顯示為 CORS 錯誤。

> 🗣️ **面試官提問：「你在開發中遇過最難忘的 CORS 錯誤是什麼？怎麼排查？」**
> **💡 建議回答：**
> 我遇過一種**「由後端 500 異常引發的 CORS 假象」**。當時前端主控台一直報 CORS 阻擋，但後端其實已經配置了全域的 `WebMvcConfigurer` 跨源設定。
> 經由分析後端日誌後，發現根本原因不是 CORS 沒配好，編譯期也沒報錯，而是 Service 內部在執行期拋出了 `NoClassDefFoundError`（因工具類別置於主程式外層，導致環境找不到該類別）。因為後端程式嚴重崩潰，直接中斷了 Spring 的正常回應生命週期，導致回應返回時**遺失了 CORS 標頭**，進而引發瀏覽器誤報 CORS。解決方法是先修正後端的代碼 Bug 與 Package 結構，後端正常回傳後，CORS 設定自然就生效了。

---

## 三、 繞過機制：Vite Dev Proxy 運作原理

### 1. 為什麼伺服器之間沒有 CORS 跨源問題？

CORS 和同源政策**純粹是瀏覽器（如 Chrome, Safari）內建的安全限制**。如果脫離了瀏覽器環境（例如 Postman、Node.js 伺服器、Java 伺服器之間互相調用 API），通訊是直接透過 TCP/IP 連線，是不受同源政策限制的。

### 2. Vite Dev Proxy 的「代理機制」

在前端開發環境中，Vite 會在本地啟動一個基於 Node.js 的網頁伺服器（預設 `localhost:5173`）。

當我們配置了 Proxy 代理：

```typescript
// vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true
    }
  }
}

```

* **步驟 A**：前端 Axios 不再直連後端 `8080`，而是向同源的 Vite 伺服器發送請求：`http://localhost:5173/api/cards/1`。
* **步驟 B**：對瀏覽器而言，這是 `5173` 到 `5173` 的**同源請求**，完全符合同源政策，安全放行。
* **步驟 C**：Vite 伺服器（Node.js 端）收到請求後，在後台（伺服器對伺服器）將請求轉發給後端 `http://localhost:8080/api/cards/1`。
* **步驟 D**：後端將資料回傳給 Vite，Vite 再原封不動回傳給瀏覽器。

透過引入一個同源的中介層，成功在開發期「繞過」了瀏覽器的非同源檢查。

### 3. 生產環境（Production）的對應部署

在開發環境使用 Vite Dev Proxy 幫我們代理。當專案打包（`npm run build`）部署到正式環境後，Vite 開發伺服器就不存在了。這時在生產環境，通常會架設 **Nginx** 配置反向代理（Reverse Proxy），原理與 Vite Proxy 完全相同：讓前端與 Nginx 同源，再由 Nginx 根據網址路徑（如 `/api`）轉發給後端伺服器。

> 🗣️ **面試官提問：「開發環境中，除了後端配置外，還有什麼方法可以解決 CORS？生產環境又該如何處理？」**
> **💡 建議回答：**
> 在開發環境中，最常用且一勞永逸的方法是使用前端建構工具（如 Vite 或 Webpack）的 **Dev Proxy（開發代理伺服器）**。
> 原理是利用**「同源政策只存在於瀏覽器端，伺服器與伺服器之間通訊不受 CORS 限制」**的特性。讓前端程式碼統一對同源的開發伺服器（如 `localhost:5173`）發送相對路徑請求，再由底層的 Node.js 將請求轉發給後端（如 `8080`）。
> 當專案部署到生產環境時，則會在最前端架設 **Nginx 作為反向代理伺服器**，同樣將前端靜態資源與 `/api` 後端請求收歸在同一個網域與連接埠下，徹底在物理層面避免跨源問題。