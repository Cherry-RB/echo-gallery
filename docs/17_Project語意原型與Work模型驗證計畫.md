# Echo Gallery Project 語意原型與 Work 模型驗證計畫

> 文件狀態：產品語意驗證中，尚未決定正式資料模型
> 記錄日期：2026-08-26

## 一、背景

目前 MVP 的 Work 功能原本定位為具體作品孵化處，但實際建立的內容包含：

- 隨興創作。
- 閱讀心得。
- 可能的長篇。
- 未來可能加入的求職、AI Agent 等方向。

這些項目大多不是一件可以直接完成的作品，而是具有培育目標、持續累積素材，並可能產生多個具體成果的計畫。因此現有 Work 在實際使用中更接近 Project。

## 二、最新產品模型假設

```text
Project = 有培育目標、過程與成果判準的計畫
Card = 教材、參考資料、範例、靈感與經驗
GrowthEntry = 新理解、計畫、行動與反省
Work = 可完成、可檢視或可分享的具體成果
```

課程設計類比：

| Echo Gallery | 課程設計 |
|---|---|
| Project | 課程 |
| Card | 教材、參考資料與範例 |
| GrowthEntry | 學習歷程與練習紀錄 |
| Work | 口頭評量、實作評量或學習成果 |

Project 不是能力本身，而是為了培養能力或達成目標所設計的成果導向計畫。

## 三、Project 與 Work 的判斷問題

### Project

> 我為了什麼目標，正在組織這些素材與練習？

例如：

- 閱讀心得寫作。
- 散文與隨興寫作。
- 長篇故事創作。
- AI Agent 學習與實作。
- 一輪求職準備。

### Work

> 完成後，外部世界會多出哪一個可以被檢視的成果？

例如：

- 《原子習慣》閱讀心得。
- 短詩〈雨夜〉。
- 第一章初稿。
- 一個 AI Agent 原型。
- 第五版履歷。

## 四、預期正式模型

本次不實作下列資料模型，只先記錄方向。

```text
Project
- title
- objective
- description
- outcomeCriteria
- status: IDEA / ACTIVE / PAUSED / DONE / ARCHIVED
- completedAt

ProjectCard
- projectId
- cardId
- note
- linkedAt

Work
- projectId（選填）
- title
- description
- status: IDEA / DRAFT / ACTIVE / DONE / ARCHIVED
- externalUrl
- completedAt

WorkCard
- workId
- cardId
- status: CANDIDATE / USED
- note
- linkedAt
- usedAt
```

CardGrowthEntry 維持獨立，不直接保存 Project 或 Work 外鍵。

## 五、本次 UI-only 語意原型

本次只修改前端可見文案：

- 「作品」改為「培育計畫」。
- `DRAFT` 顯示為「規劃中」。
- description 以「計畫設計」呈現，提示填寫培育目標、成果判準與可能 Works。
- WorkCard 的 `CANDIDATE` 顯示為「素材池」。
- WorkCard 的 `USED` 顯示為「已運用」。
- 作品素材與關聯操作改為計畫素材語意。

刻意不修改：

- 後端 Work、WorkCard 類別。
- `/api/works` API。
- `/works` 路由。
- `works`、`work_cards` 資料表。
- 既有資料與狀態值。
- 真正的 Work entity 與頁面。

這是可逆的語意測試，不是正式 domain migration。

## 六、驗證方式

每個培育計畫的「計畫設計」使用以下結構：

```text
培育目標：

成果判準：

可能產生的 Works：
-
```

實際使用時觀察：

1. 一個培育計畫是否自然產生兩個以上具體 Work。
2. 是否想替每個 Work 個別管理構想、草稿、進行中與完成狀態。
3. 是否需要從 Project 素材池挑選不同 Card 給不同 Work。
4. 是否出現 Project 尚未完成，但其中部分 Work 已完成。
5. 「素材池／已運用」對 Project 是否有意義，或反而多餘。
6. 一個 Work 是否通常只屬於一個 Project。
7. 既有標籤是否已足以取代某些 Project。

## 七、正式遷移條件

符合以下多項條件後，才規劃正式改造：

- 至少兩個培育計畫累積真實 Card。
- 至少一個 Project 形成兩個以上明確 Work。
- 需要獨立追蹤 Work 狀態、素材或外部連結。
- ProjectCard 與 WorkCard 的關聯語意在使用中可清楚區分。
- 現有 UI-only 原型確實比原本作品語意自然。

正式遷移預期為：

```text
現有 works       → projects
現有 work_cards  → project_cards
新增新的 works
新增新的 work_cards
```

由於目前使用 Hibernate `ddl-auto: update`，正式遷移時不可只修改 entity 或資料表名稱，必須設計受控資料遷移、API 變更與前後端回歸測試。

## 八、目前決議

1. 先以 UI-only 方式將現有 Work 當作 Project 使用。
2. 不立即建立真正的 Work。
3. 不把 Project 擴張成任務、截止日期或通用專案管理工具。
4. 先驗證「素材池 → 多個具體成果」是否為真實且重複的流程。
5. 驗證通過後，再另行規劃正式 Project／Work 資料模型。
