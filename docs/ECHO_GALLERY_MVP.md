# Echo Gallery 回音畫廊 MVP Scope

* [一、簡介](#一簡介)
* [二、使用方式](#二使用方式)
* [三、MVP 第一版邊界](#三mvp-第一版邊界)
* [四、技術選型/開發規劃](#四技術選型開發規劃)
* [五、功能需求釐清](#五功能需求釐清)

---

## 一、簡介

這是一個由使用者自行控制推播內容的網站。
> EchoGarden 是一個個人化內容推播與複習工具，解決使用者收藏內容後不再回看的問題。使用者可以建立外部連結卡片或自建筆記卡片，設定推播頻率，讓有價值的影音、圖文、想法與筆記在未來自動回到個人瀑布流中。專案包含卡片管理、標籤分類、週期性推播、熱度紀錄與愛心冷卻機制，目標是讓使用者重新掌握內容再出現的節奏，而不是被平台演算法決定注意力。

### 解決痛點

1. **收藏死亡**：使用者常在 Bilibili、Twitter/X、Facebook、文章、影片、貼文中看到好內容，會收藏、轉存、截圖、丟 Notion。但大多數內容收藏後就死了，不會回頭去看。Echo Gallery 能讓使用者再次遇見收藏內容。
2. **演算法不可控**：現在的社群平台會推播內容，但推播權在平台手上。平台關心的是停留時間，不一定是使用者真正想複習、想反覆吸收的內容。Echo Gallery 能讓使用者將推播權拿回來，讓使用者自己設定這張卡片多久出現一次。
3. **有價值內容需要反覆出現**：很多內容不是看一次就夠。例如：觀念、金句、技術文章、學習影片、閱讀筆記、心理提醒、創作靈感、職涯方向、喜歡的作品分析。它們需要週期性回來，才能真的進入生命或知識系統。Echo Gallery 提供間隔重複的影音圖文與社群內容，讓使用者可以專注於接收值得收藏的、有價值的資訊。
4. **自建筆記也能變成推播**：Echo Gallery 不只處理外部平台收藏，也處理內部輸出，使用者可以把自己的想法、閱讀筆記、日記片段、提醒、創作靈感，做成卡片，然後它會重新出現在瀑布流裡。這就變成：「我不只是被外部內容推播，我也可以被過去的自己推播。」

### 設計理念
> 我如何讓有價值的內容，不只是被收藏後沉沒，而是可以適時的重新出現在瀑布流推播，與我相遇？

---

## 二、使用方式

### 建立卡片方式

#### 卡片來源

1. **具體的內容來源**：像是在notion建立筆記或知識庫一樣，複製社群平台的網址，建立一張像是貼文/卡片的內容，方便使用者之後可以直接透過點選跳轉到該內容來源。如：bilibili、youtuber、twitter、facebook、instagram、threads、微博、其他網站等等。

2. **自建內容的卡片**：像是在notion建立筆記或知識庫一樣，建立屬於自己的筆記卡片。如：思考想法、閱讀筆記等等。

#### 填寫內容

片的標題、推薦原因、簡介、內文、類型、連結網址、封面圖片網址、分類標籤、推播頻率等等。

### 推播方式

1. **看板**：應用具備瀑布流推播的看板功能。有以下幾種類型的看板：設定的卡片推播時間序、熱度(收藏數)、當月熱度(收藏數)，與依據自建時間序。

2. **星號**：具備星號收藏功能，使用者可以重複點擊星號，星號一段時間(如：一個月)後就會恢復可以點選的功能，就像bilibili的點讚功能一樣。

---

## 三、MVP 第一版邊界

Echo Gallery MVP 核心

> 使用者可以建立連結或筆記卡片，為卡片填寫收藏原因、簡介、內文、標籤與回流頻率。卡片會依照設定的日期重新出現在瀑布流中。使用者可以透過「已回顧」讓卡片排入下一次回流，也可以透過星標記錄這張卡片反覆對自己有價值的次數。

### 功能邊界

| 編號 | 項目 | 說明 | 完成度 | 完成日期 |
|-----|------|------|-------|----------|
| 1 | [卡片CRUD：連結卡片、筆記卡片](#1-卡片crud連結卡片筆記卡片) | - | - | - |
| 2 | [推播頻率設定：intervalDays](#2-推播頻率設定intervaldays) | - | - | - |
| 3 | [看板：按照推播頻率的推播流](#3-看板按照推播頻率的推播流) | - | - | - |
| 4 | [愛心功能：一個月內不能再次點同一張卡](#4-愛心功能一個月內不能再次點同一張卡) | - | - | - |
| 5 | [分類標籤](#5-分類標籤) | - | - | - |

#### 1. 卡片CRUD：連結卡片、筆記卡片

DTO：
```js
type CardType = "note" | "link";

type ViewMode = "text" | "gallery";

type SourceType =
  | "bilibili"
  | "youtube"
  | "x"
  | "facebook"
  | "article"
  | "other";

type Card = {
    id: string;

    // 基本資訊
    type: CardType;
    title: string;

    // 連結型卡片使用
    url?: string;
    domain?: string;
    sourceType?: SourceType;

    // 卡片摘要，用於瀑布流
    summary?: string;
    // 詳細內容，用於詳情頁
    content?: string;
    // 使用者為什麼收藏這張卡
    reason?: string;

    // 瀑布流顯示開關
    showContentPreview: boolean;

    // 視覺輔助
    coverImageUrl?: string;

    // 分類
    tags: string[];

    // 回流設定
    intervalDays: number | null; // 卡片出現頻率(天)
    nextShowAt: string | null; // 下一次出現的日期
    lastShownAt?: string | null; // 上一次被使用者處理 / 看過的日期

    // 星標 / 熱度
    likeCount: number; // 累積被按愛心數量
    lastLikedAt?: string; // 上一次按愛心的日期
    likeAvailableAt?: string; // 下一次可以再按愛心的日期

    // 狀態
    isArchived?: boolean;

    // 時間
    createdAt: string;
    updatedAt: string;
};
```
#### 2. 推播頻率設定：intervalDays
#### 3. 看板：按照推播頻率的推播流

* 今日推播：
```js
// 第一版
cards.filter(card => new Date(card.nextShowAt) <= today)
// 第二版
const reviewCards = cards.filter(card => {
  if (card.isArchived) return false
  if (!card.nextShowAt) return false
  if(lastShownAt !== today) return false

  return new Date(card.nextShowAt) <= today
})
// 第三版
// 主 feed：reviewCards
const reviewCards = cards.filter(...)
// 不夠補 recently added：recentCards
const recentCards = cards
  .filter(card => !card.isArchived)
  .sort(...)
// 再補 random rediscovery：randomOldCards
const rediscoveryCards = ...
// 最終 feed
const finalFeed = [
  ...reviewCards,
  ...recentCards,
  ...rediscoveryCards
]
```
* 點擊「已回顧」：
```js
nextShowAt = today + intervalDays
lastShownAt = today
```
#### 4. 愛心功能：一個月內不能再次點同一張卡
```js
likeCount += 1
lastLikedAt = today
likeAvailableAt = today + 30 days
```
#### 5. 分類標籤

### MVP 畫面

|  | 項目 | URL | 說明 | 完成度 | 完成日期 |
|-----|------|-----|------|-------|----------|
| 1 | 今日推播 | /feed | 顯示到期卡片 | - | - |
| 2 | 全部卡片 | /cards | 管理所有卡片 | - | - |
| 3 | 卡片詳情 | /card/:id | - | - | - |
| 4 | 新增卡片 | /cards/new | 可選擇連結卡片或是筆記卡片 | - | - |
| 5 | 熱度看板 | /hot | 依 likeCount 排序 | - | - |

---

## 四、技術選型/開發規劃
* 第一階段 Vue + localStorage
* 第二階段 Spring Boot + Supabase Auth + Database
* 第三階段部署上線

---

## 五、功能需求釐清

|  | 日期 | 項目 | 說明 |
|-----|------|------|------|
| 1 | 2026.05.08 | [瀑布流卡片顯示資訊與排版](#1-20260508-瀑布流卡片顯示資訊與排版) | 決議重點：卡片視圖、內文長度、卡片重點 |
| 2 | 2026.05.10 | ||

### 1. 2026.05.08 瀑布流卡片顯示資訊與排版

#### 待釐清需求描述：
瀑布流中卡片需要顯示哪些的資訊？哪些是重點？排版要如何設計？

#### 考量釐清：
* **卡片視圖**：Echo Gallery 的定位為建立起可以跨平台回顧收藏與個人輸出筆記的連結橋接平台。圖片、影音等多媒體資源不是儲存的重點，但在瀑布流中，仍然需要 notion gallery 視圖彈性，提供使用者可以依據偏好，自行選擇以文字為主還是圖片為主的卡片顯示方式。
* **內文長度**：考量到筆記卡片類型的內文長度可能較長，但瀑布流不能顯示太長內容。參考 twitter 或 threads 這種有字數限制的貼文卡片，可以透過下一篇貼文展開敘寫，但是本產品對「卡片」定位是一篇篇各自獨立的內容收藏，彼此之間並沒有轉發或回覆這種關聯性。因此，決議卡片需要再獨立出簡介欄位，再透過詳情頁承接長內容。參考 bilibili 的影片簡介區塊。
* **卡片重點**：不管使用者是否已經看過卡片內容、對卡片有沒有印象，收藏瀑布流的卡片顯示重點，應該要放在提醒使用者為何會選擇收藏這個內容。實際的完整內文或許不用在瀑布流顯示，但是新增「簡介」欄位是需要的，可幫助使用者快速回憶內容，「收藏理由」欄位更是需要的，這是整個產品核心。

#### summary、content、reason 區分：
* summary：給瀑布流看的簡短介紹，有字數上限。
* content：完整內容 / 長筆記。給詳情頁看的完整內容。
* reason：收藏理由。提醒使用者「當初為什麼存這個」。

優先顯示順序：reason > summary > content

> 因為在 Echo Gallery，最重要的是「為什麼這張卡值得再次出現？」

#### 新建開關欄位 showContentPreview
當沒有 summary / reason，用開關判斷在瀑布流顯示 content 的截斷版

#### 瀑布流顯示的卡片資訊：
```text

header：
- 卡片類型
- 標題

body：
- 優先顯示 reason
- 如果沒有 reason，顯示 summary
- 如果沒有 summary，根據設定決定是否顯示 content 截斷

footer：
- domain
- tags
- 星標數
- 下次回流資訊 / 已看過按鈕

```

#### 瀑布流卡片資訊階層

##### 文字模式 Text Mode

參考 twitter。
```text
[筆記] 產品第一版不要做太大

我收藏它是因為：它提醒我 MVP 的核心不是功能完整，而是完成核心閉環。

#產品 #MVP #side-project

來源：chatgpt.com
⭐ 3
下次回流：2026/05/15

[已回顧] [查看詳情] [開啟連結]

```

##### 圖片模式 Gallery Mode

參考 bilibili / notion gallery

有 cover：
```text
[cover image]

產品第一版不要做太大
我收藏它是因為：它提醒我 MVP 的核心不是功能完整...

#產品 #MVP
⭐ 3
```

沒有 cover：
```
[文字預覽區塊]

產品第一版不要做太大
我收藏它是因為：它提醒我 MVP 的核心不是功能完整...

#產品 #MVP
⭐ 3
```

### 2. 2026.05.10 瀑布流推播邏輯
* 我感到困惑的是，我應該如何設計? 如果收藏夾裡的內容不多，我要在瀑布流中增加顯示 intervalDays 未來的或是過去的資料嗎? 還是停留在當天即可
* 我不想要特地設計一個已回顧的按鈕，讓使用者還要一個個點選用來追蹤更新 lastShownAt。這樣變成曾經在瀑布流中出現過的卡片，我都要自動更新欄位nextShowAt與lastShowAt?
* card.isArchived=>這個欄位代表什麼?
* 同一天重複曝光=>這個影響不好嗎? 我可以之後預計用一個批次作業，把到期的日期統一處理吧?
