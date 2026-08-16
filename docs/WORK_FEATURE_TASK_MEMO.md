# Work 功能任務追蹤 Memo

- 建立日期：2026-08-16
- 基線分支：`feat/OTPAR`
- 目前功能分支：`feat/work-management`
- 目前狀態：任務 3 後端作品素材關聯完成；下一步進入任務 4 前端作品頁面
- 需求來源：`ECHO_GALLERY_CREATION_INCUBATOR_CHANGE_REQUIREMENTS.md`

---

## 一、這次要完成什麼

新增「作品 Work」功能，讓使用者可以建立一件具體作品，並把既有 Card 加入作品中作為候選素材或已使用素材。

核心關係：

```text
Card   N <------> N   Work
          WorkCard
```

重要規則：

- 一張 Card 可以加入多個 Work。
- 同一張 Card 不可重複加入同一個 Work。
- `WorkCard.status` 只表示 Card 在某個 Work 中是否已被使用。
- `Card.growthStatus` 與 `WorkCard.status` 必須保持獨立。
- 移除 WorkCard 關聯時，不可刪除 Card。
- 封存 Work 時，不可刪除 Card。
- 所有 Work 與 Card 操作都必須檢查登入使用者 ownership。

---

## 二、開始前要確認的決策

- [x] Work status 採用 `IDEA / DRAFT / ACTIVE / DONE / ARCHIVED`。
- [x] 新 Work 預設為 `IDEA`。
- [x] 第一版暫不加入 Work type。
- [x] 第一版以 `ARCHIVED` 取代 Work hard delete。
- [x] `USED` 可以退回 `CANDIDATE`，退回時清除 `usedAt`。
- [ ] WorkCard 的 `note` 欄位保留 nullable，第一版 UI 是否提供編輯待確認。

---

## 三、分支準備

- [x] 確認 `growthStatus` 相關 commit 已存在於基線分支。
- [x] 確認工作區中未提交文件要保留在哪個分支。
- [x] 從 `feat/OTPAR` 建立 `feat/work-management`。
- [x] 建立分支後再次確認 `git status`。

---

## 四、任務拆分

### 任務 1｜建立 Work 資料模型

預計內容：

- [x] 新增 `WorkStatus`。
- [x] 新增 `Work` entity。
- [x] 新增 `WorkRepository`。
- [x] 定義 title、description、status、externalUrl、completedAt 與 timestamps。
- [x] 建立 Work table SQL。
- [x] 加入 user foreign key 與必要 index 的 JPA schema 定義。
- [x] 新增 Work 持久化測試。

完成條件：

- [x] Work 可以正常寫入及讀取。
- [x] Work 必須屬於一個 User。
- [x] 預設狀態正確。
- [x] entity 欄位長度與 nullable 定義明確；URL request 驗證留待任務 2。
- [x] 後端測試通過：`./gradlew test`，2026-08-16。

建議 commit：

```text
feat(work): 建立作品資料模型
```

### 任務 2｜完成 Work 基本管理

預計內容：

- [x] 建立 Work request／response DTO。
- [x] 新增 `WorkService`。
- [x] 新增 `WorkController`。
- [x] 支援新增 Work。
- [x] 支援 Work list。
- [x] 支援 Work detail。
- [x] 支援修改 Work。
- [x] 支援封存 Work。
- [x] 檢查 Work ownership。
- [x] 新增 Controller／Service 整合測試。

完成條件：

- [x] 使用者只能讀寫自己的 Work。
- [x] 建立、查看、修改與封存皆可正常操作。
- [x] 非法 URL 或欄位內容會收到合理錯誤。
- [x] Work 管理整合測試通過：`./gradlew test --tests com.echogallery.work.WorkManagementIntegrationTests`，2026-08-16。

建議 commit：

```text
feat(work): 新增作品管理 API
```

### 任務 3｜建立 Card 與 Work 的關聯

預計內容：

- [x] 新增 `WorkCardStatus`：`CANDIDATE / USED`。
- [x] 新增明確的 `WorkCard` entity。
- [x] 新增 `WorkCardRepository`。
- [x] 建立 WorkCard table SQL。
- [x] 加入 `(work_id, card_id)` unique constraint。
- [x] 加入 foreign key 與必要 index。
- [x] 支援 Card 加入 Work，預設為 `CANDIDATE`。
- [x] 支援 Card 從 Work 移除。
- [x] 支援 `CANDIDATE / USED` 切換。
- [x] 正確設定或清除 `usedAt`。
- [x] 支援 Work Detail 取得素材列表與 Card 顯示資訊。
- [x] Work List 回傳候選與已使用素材數量。
- [x] 檢查 Work 與 Card ownership。
- [x] 將 DB duplicate constraint 轉換成合理 API 錯誤。

完成條件：

- [x] 同一 Card 可以加入多個 Work。
- [x] 同一 Card 不可重複加入同一 Work。
- [x] User A 不可連結 User B 的 Card 或 Work。
- [x] WorkCard 狀態彼此獨立。
- [x] WorkCard 狀態不會自動修改 Card growth status。
- [x] unlink 後 Card 仍存在。
- [x] WorkCard 持久化測試通過：`./gradlew test --tests com.echogallery.work.WorkCardPersistenceTests`，2026-08-16。
- [x] WorkCard 加入／移除／狀態切換／素材列表整合測試通過：`./gradlew test --tests com.echogallery.work.WorkCardManagementIntegrationTests`，2026-08-16。
- [x] Work List 素材數量聚合與使用者隔離測試通過，2026-08-16。

建議 commit：

```text
feat(work): 新增作品素材關聯功能
```

### 任務 4｜製作作品頁面

預計內容：

- [ ] 新增 Work TypeScript types。
- [ ] 新增 Work API client。
- [ ] 建立 TanStack Query keys 與 mutations。
- [ ] 新增「作品」導覽入口。
- [ ] 新增 Work List。
- [ ] 新增 Work create／edit UI。
- [ ] 新增 Work Detail。
- [ ] 顯示候選素材與已使用素材。
- [ ] 支援加入、移除 Card。
- [ ] 支援 `CANDIDATE / USED` 切換。
- [ ] 處理 loading、empty 與 error state。

完成條件：

- [ ] 使用者可以從前端建立及編輯 Work。
- [ ] Work Detail 能正確顯示候選與已使用素材。
- [ ] mutation 成功後相關畫面會更新。
- [ ] API 失敗時 UI 不會假裝成功。
- [ ] 前端測試與 build 通過。

建議 commit：

```text
feat(work): 新增作品管理頁面
```

### 任務 5｜整合 Card Detail

預計內容：

- [ ] Card Detail 顯示所在 Works。
- [ ] 顯示每個 Work 中的 `CANDIDATE / USED`。
- [ ] 新增「加入作品」操作。
- [ ] Work options 排除已加入的 Work。
- [ ] 加入成功後刷新 Card 與 Work 相關 query。
- [ ] 可以從 Card Detail 前往 Work Detail。

完成條件：

- [ ] 同一 Card 可以從 Card Detail 加入多個 Work。
- [ ] UI 不提供重複加入同一 Work 的選項。
- [ ] API 與 DB 仍保留防止重複的最後防線。
- [ ] Card growth status 與 WorkCard status 分開顯示。
- [ ] 前端測試與 build 通過。

建議 commit：

```text
feat(card): 新增卡片加入作品流程
```

### 任務 6｜全面驗收與文件

後端驗收：

- [ ] Work 建立、查詢、修改、封存與 ownership 正常。
- [ ] 同一 Card 可以加入多個 Work。
- [ ] 同一 Card 不可重複加入同一 Work。
- [ ] `CANDIDATE → USED` 會設定 `usedAt`。
- [ ] `USED → CANDIDATE` 依決策處理 `usedAt`。
- [ ] unlink 不刪除 Card。
- [ ] 封存 Work 不刪除 Card。
- [ ] WorkCard status 不修改 Card growth status。
- [ ] Today／All／Snooze／Archive／Read／Like 沒有退化。

前端驗收：

- [ ] Work List／Detail／Card Detail 流程完整。
- [ ] 桌面版手動驗證完成。
- [ ] 約 375px 行動版手動驗證完成。
- [ ] loading／empty／error state 已確認。
- [ ] `npm run build` 通過。

文件與部署：

- [ ] 更新 `docs/ECHO_GALLERY_MVP.md`。
- [ ] 記錄 Work／WorkCard 模型與使用流程。
- [ ] 記錄 Project 暫不實作。
- [ ] 記錄本次不做項目。
- [ ] Work SQL 已在本機 PostgreSQL 執行成功。
- [ ] Work SQL 已在本機重複執行驗證。
- [ ] 準備部署時才執行 Supabase SQL。
- [ ] 記錄 Supabase SQL 實際執行日期與結果。

建議 commits：

```text
test(work): 補充作品功能回歸測試
docs(work): 記錄作品功能與資料模型
```

---

## 五、SQL 執行追蹤

SQL 目前預計存放於 VS Code Database 套件管理的 SQL 資料夾，不納入 repository。

| SQL | 本機 PostgreSQL | 重複執行 | Supabase 正式環境 | 備註 |
| --- | --- | --- | --- | --- |
| 建立 `works` | 已執行（2026-08-16） | 未驗證 | 未執行 | SQL 存放於 repository 外部 |
| 建立 `work_cards` | 已執行（2026-08-16） | 未驗證 | 未執行 | SQL 存放位置由使用者管理 |

正式環境執行原則：

1. 先在本機驗證 SQL 與後端測試。
2. 確認功能分支準備部署。
3. 備份或確認 Supabase 還原策略。
4. 執行 Supabase SQL。
5. 驗證 constraint、index 與既有 Card 資料。
6. 再部署新版後端與前端。

---

## 六、本次明確不做

- Project entity。
- Parent Work／Sub Work。
- Work 拖曳排序。
- 完整長文編輯器。
- OTPAR schema 拆分。
- 自動成長演算法。
- WorkCard 複雜角色分類。
- 熱度圖、Streak、今日創作桌。
- 自動把 `USED` 等同於 `MATURE`。

---

## 七、進度更新規則

每完成一個任務：

1. 勾選實際完成項目。
2. 記錄測試指令與結果。
3. 記錄 SQL 是否執行及執行環境。
4. 確認沒有把下一個任務的內容順手混入。
5. 建立單一邏輯的 commit。
6. 再開始下一個任務。

若實作中改變 domain rule，先更新本 Memo 的「開始前要確認的決策」，再修改程式碼。
