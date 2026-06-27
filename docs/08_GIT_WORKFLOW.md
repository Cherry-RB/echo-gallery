# Git 開發標準流程 (GIT_WORKFLOW.md)

本文件定義專案的版本控制規範，旨在統一開發流程及維護歷史紀錄的清晰度。

---

## 1. 分支管理規範 (Branching Convention)

本專案採用功能分支 (Feature Branch) 開發模式，禁止直接於 `main` 分支進行開發。

| 分支類型 | 命名格式 | 用途說明 |
| --- | --- | --- |
| 功能開發 | `feat/<功能名稱>` | 新增功能 |
| 問題修復 | `fix/<問題名稱>` | 修復 Bug |
| 程式重構 | `refactor/<目標>` | 程式碼優化，不涉及功能異動 |
| 文件更新 | `docs/<項目名稱>` | 僅更新文件內容 |
| 其他雜項 | `chore/<項目名稱>` | 建置環境或工具升級 |

---

## 2. 開發循環流程 (Development Workflow)

### 2.1 初始化任務

```bash
git checkout main
git pull origin main
git checkout -b feat/<功能名稱>
```

### 2.2 開發與 Commit

* 保持 Commit 原子化（一個 Commit 僅對應一個邏輯變更）。
* 訊息格式請參閱「4. Commit 訊息規範」。

### 2.3 與主分支對齊 (Rebase)

在將分支合併至 `main` 前，需同步 `main` 的最新異動。

```bash
git checkout main
git pull origin main
git checkout feat/<功能名稱>
git rebase main
```

*註：若發生衝突，解決衝突後執行 `git add <檔案>`，接著執行 `git rebase --continue`。*

### 2.4 合併與清理

```bash
git checkout main
git merge feat/<功能名稱>
git push origin main
git branch -d feat/<功能名稱>
```

---

## 3. 異常處理與救援機制 (Rescue Kit)

若開發過程中需臨時切換分支處理緊急任務，請利用 `stash` 功能暫存當前進度。

* **暫存當前變更**:
```bash
git stash # 將未 Commit 的變更暫存
```

* **切換分支處理任務**:
```bash
git checkout fix/<任務名稱>
# 執行修復與 Commit...
```

* **復原暫存變更**:
```bash
git checkout feat/<原功能分支>
git stash pop # 將暫存的變更還原
```

### 異動查詢與版本復原

* **檢視檔案狀態**: `git status`
* **檢視詳細差異 (未暫存)**: `git diff`
* **檢視詳細差異 (已暫存)**: `git diff --staged`

#### 版本復原操作

* **撤銷本地未 Commit 的變更**: `git restore <檔案名稱>`
* **撤銷本地已 Commit 但未推送的變更 (二擇一)**:
  * `git reset --soft HEAD~1` (保留修改)
  * `git reset --hard HEAD~1` (捨棄修改)
* **撤銷已推送到遠端的變更**:
```bash
git log --oneline      # 查詢 Commit ID
git revert <Commit ID> # 產生反轉提交
git push origin main
```

---

## 4. Commit 訊息規範 (Conventional Commits)

Commit 訊息應遵循以下結構，以利後續生成 ChangeLog 與邏輯追蹤。

### 格式結構

```text
type(scope): description
```

* **type**: 異動類型 (見下表)。
* **scope**: 影響範圍 (如 `auth`, `db`, `api`)。
* **description**: 簡單扼要說明，使用祈使句 (如 `add`, `fix`)，結尾不加句號。

### 4.1 類型 (Type) 對照表

| Type | 說明 |
| --- | --- |
| `feat` | 新增功能 |
| `fix` | 修復 Bug |
| `docs` | 文件異動 |
| `style` | 程式碼格式調整 (不影響邏輯) |
| `refactor` | 程式碼重構 |
| `perf` | 效能優化 |
| `test` | 測試代碼新增或修改 |
| `chore` | 建置工具或相依套件更新 |

### 4.2 訊息撰寫範例

#### 不建議的紀錄方式

* `git commit -m "fix"` (未描述內容)
* `git commit -m "update code"` (內容模糊)
* `git commit -m "相簿功能加好了"` (非規範格式)

#### 符合規範的紀錄方式

* `git commit -m "feat(gallery): add image upload functionality"`
* `git commit -m "fix(security): prevent unauthorized api access"`
* `git commit -m "docs(readme): add environment setup guide"`

### 4.3 進階撰寫格式

若單行描述不足以說明異動內容，可採用結構化多行格式：

```text
type(scope): description

(空一行)

詳細說明 (Body)：解釋變更原因、實作邏輯異動點及影響範圍。

(空一行)

備註 (Footer)：相關 Issue 編號或注意事項，例如 Closes #123

```

---

## 5. 作業原則 (Operational Guidelines)

1. **工作區淨空**: 在進行 `checkout` 或 `merge` 前，確保 `git status` 顯示工作區為乾淨狀態。
2. **線性歷史**: 在開發分支建議採用 `rebase` 以維持歷史紀錄的線性結構。
3. **頻率控制**: 頻繁執行 Commit 可降低資料遺失風險，並有助於回溯開發軌跡。
4. **訊息效用**: 良好的 Commit 訊息應使團隊成員無需閱讀程式碼即可理解異動的商業邏輯與決策脈絡。
