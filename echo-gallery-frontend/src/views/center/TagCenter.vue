<script setup lang="ts">
import { ref, computed, nextTick, onActivated } from 'vue'
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, More, Edit, Delete, CollectionTag } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { cardApi } from '../../utils/api/cardApi'
import { tagApi } from '../../utils/api/tagApi'
import { useCardStatus } from '../../utils/useCardStatus'
import { shouldMarkReviewedOnOpenDetail, type BoardType } from '../../types/board'
import type { CardDto } from '../../types/card'
import CardItem from '../../components/CardItem.vue';

// 標籤資料結構：id 是唯一識別，name 只作為顯示用途（可被改名）
interface TagDto {
  id: number | string
  name: string
  cardCount?: number
}

const router = useRouter()
const { handleReadCard } = useCardStatus()
const cardsGridWrapperRef = ref<HTMLElement | null>(null)
const savedCardsScrollTop = ref(0)

// 標籤中心對應的 boardType。只要不讓 shouldMarkReviewedOnOpenDetail 回傳 true，
// 從這裡點進卡片詳情就不會被判定為「完成本輪回顧」，不會動到 nextShowAt
const TAG_BOARD_TYPE: BoardType = 'tag'

function handleOpenDetail(card: CardDto) {
  // 卡片列表使用內部滾動容器，Vue Router 的 scrollBehavior 不會保存其位置
  savedCardsScrollTop.value = cardsGridWrapperRef.value?.scrollTop ?? 0

  router.push({
    name: 'CardDetail',
    params: { id: card.id },
    query: { from: TAG_BOARD_TYPE },
  })

  if (shouldMarkReviewedOnOpenDetail(TAG_BOARD_TYPE)) {
    handleReadCard({ id: card.id, sourceBoard: TAG_BOARD_TYPE })
  }
}

onActivated(async () => {
  await nextTick()
  requestAnimationFrame(() => {
    if (cardsGridWrapperRef.value) {
      cardsGridWrapperRef.value.scrollTop = savedCardsScrollTop.value
    }
  })
})

const queryClient = useQueryClient()

// =====================================================
// 1. 狀態管理 (State)
// =====================================================
const tagSearchQuery = ref('')                          // 左側標籤搜尋框
const selectedTagIds = ref<Array<number | string>>([])  // 已勾選的標籤「id」陣列（改用 id，不再用 name）
const operator = ref<'AND' | 'OR'>('OR')                 // 運算邏輯切換 ('AND' | 'OR')

// 重新命名 Dialog 相關狀態
const renameDialogVisible = ref(false)
const currentTag = ref<TagDto | null>(null)
const newTagNameInput = ref('')

// =====================================================
// 2. TanStack Query: 獲取標籤清單
// =====================================================
const { data: tagsData, isLoading: isTagsLoading } = useQuery<TagDto[]>({
  queryKey: ['tags'],
  queryFn: () => tagApi.getTags(),
  placeholderData: [],
})

// 即時過濾左側標籤清單（依名稱搜尋）
const filteredTags = computed(() => {
  const list = tagsData.value ?? []
  if (!tagSearchQuery.value.trim()) return list
  return list.filter((t) =>
    t.name.toLowerCase().includes(tagSearchQuery.value.toLowerCase())
  )
})

// 目前已勾選標籤的完整物件：id 對應到「最新」的 name
// checkbox 的勾選狀態、畫面顯示的 pill 都靠這個，不會因為改名或刪除而跑掉
const selectedTagObjects = computed(() =>
  (tagsData.value ?? []).filter((t) => selectedTagIds.value.includes(t.id))
)

// =====================================================
// 3. TanStack Query: 根據選取的標籤與運算子動態獲取卡片
// =====================================================
const { data: cardsData, isLoading: isCardsLoading } = useQuery<CardDto[]>({
  // queryKey 放 selectedTagIds、operator 這兩個 ref，vue-query 會自動 unwrap 並追蹤變化
  queryKey: ['cards', 'filtered', selectedTagIds, operator],
  queryFn: () => cardApi.getCardsByTags({
    tagIds: selectedTagIds.value,
    operator: operator.value
  }),
  // 只有當有選取標籤時才發請求，否則回傳空陣列避免不必要的 API 浪費
  enabled: computed(() => selectedTagIds.value.length > 0),
  // 切換標籤時保留舊資料，避免畫面先閃空再跳出新資料
  placeholderData: keepPreviousData,
})

// =====================================================
// 4. 標籤管理 Mutations (重新命名與刪除)
// =====================================================
// 重新命名 Mutation
const renameMutation = useMutation({
  mutationFn: ({ id, name }: { id: number | string; name: string }) =>
    tagApi.updateTag(id, { name }),
  onSuccess: () => {
    ElMessage.success('標籤重新命名成功')
    renameDialogVisible.value = false
    // 注意：selectedTagIds 完全不用動，因為它存的是 id，不受改名影響
    queryClient.invalidateQueries({ queryKey: ['tags'] })
    queryClient.invalidateQueries({ queryKey: ['sidebar'] })
    queryClient.invalidateQueries({ queryKey: ['cards'] })
  },
  onError: (err: any) => {
    ElMessage.error(err.response?.data?.message || '重新命名失敗')
  }
})

// 刪除標籤 Mutation
const deleteMutation = useMutation({
  mutationFn: (tag: TagDto) => tagApi.deleteTag(tag.id),
  onSuccess: (_, deletedTag) => {
    ElMessage.success(`標籤「${deletedTag.name}」已刪除，並自動解除相關卡片關聯`)
    // 把被刪除標籤的 id 從勾選清單移除，避免之後查詢還帶著已經不存在的標籤
    selectedTagIds.value = selectedTagIds.value.filter((id) => id !== deletedTag.id)
    queryClient.invalidateQueries({ queryKey: ['tags'] })
    queryClient.invalidateQueries({ queryKey: ['sidebar'] })
    queryClient.invalidateQueries({ queryKey: ['cards'] })
  },
  onError: (err: any) => {
    ElMessage.error(err.response?.data?.message || '刪除標籤失敗')
  }
})

// =====================================================
// 5. 互動行為函數
// =====================================================
// 全選標籤
const handleSelectAll = () => {
  selectedTagIds.value = (tagsData.value ?? []).map((t) => t.id)
}

// 清除選取
const handleClearSelection = () => {
  selectedTagIds.value = []
}

// 打開重新命名對話框
const openRenameDialog = (tag: TagDto) => {
  currentTag.value = tag
  newTagNameInput.value = tag.name
  renameDialogVisible.value = true
}

// Dialog 關閉後清理狀態
// 不論是按「取消」、按 ESC、點外部，還是改名成功後自動關閉，都會走到這裡
const handleDialogClosed = () => {
  currentTag.value = null
  newTagNameInput.value = ''
}

// 確認重新命名
const handleConfirmRename = () => {
  if (!currentTag.value || !newTagNameInput.value.trim()) return
  renameMutation.mutate({
    id: currentTag.value.id,
    name: newTagNameInput.value.trim()
  })
}

// 觸發刪除確認
const handleDeleteTag = (tag: TagDto) => {
  ElMessageBox.confirm(
    `確定要刪除標籤「#${tag.name}」嗎？這將會同步解除所有卡片對此標籤的關聯。`,
    '刪除標籤警告',
    {
      confirmButtonText: '確定刪除',
      cancelButtonText: '取消',
      type: 'warning',
    }
  ).then(() => {
    deleteMutation.mutate(tag)
  }).catch(() => {})
}

// 下拉選單的指令處理（取代原本每個 tag 都要 new 一個 wrapper function 的寫法）
const handleCommand = (command: string | number | object, tag: TagDto) => {
  if (command === 'rename') {
    openRenameDialog(tag)
  } else if (command === 'delete') {
    handleDeleteTag(tag)
  }
}
</script>

<template>
  <div class="tag-center-container">

    <!-- ================= 左側控制面板 (25%) ================= -->
    <aside class="tag-sidebar-panel" v-loading="isTagsLoading">
      <div class="panel-header">
        <h2 class="panel-title">
          <el-icon><CollectionTag /></el-icon> 標籤中心
        </h2>
      </div>

      <!-- 標籤搜尋框 -->
      <div class="search-box">
        <el-input
          v-model="tagSearchQuery"
          placeholder="搜尋標籤名稱..."
          :prefix-icon="Search"
          clearable
          size="default"
        />
      </div>

      <!-- 快捷按鈕與運算邏輯切換器 -->
      <div class="controls-toolbar">
        <div class="action-buttons">
          <el-button size="small" type="primary" link @click="handleSelectAll">全選</el-button>
          <span class="divider">|</span>
          <el-button size="small" type="info" link @click="handleClearSelection">清除</el-button>
        </div>

        <!-- 邏輯切換器 AND / OR -->
        <el-radio-group v-model="operator" size="small">
          <el-radio-button label="OR">或 (OR)</el-radio-button>
          <el-radio-button label="AND">且 (AND)</el-radio-button>
        </el-radio-group>
      </div>

      <!-- 多選標籤清單 -->
      <div class="tag-checkbox-list">
        <el-checkbox-group v-model="selectedTagIds">
          <div
            v-for="tag in filteredTags"
            :key="tag.id"
            class="tag-item-row"
          >
            <el-checkbox :label="tag.id" class="tag-checkbox">
              <span class="tag-name">#{{ tag.name }}</span>
              <span class="tag-count">({{ tag.cardCount ?? 0 }})</span>
            </el-checkbox>

            <!-- 行內更多操作選單 (重新命名 / 刪除) -->
            <el-dropdown trigger="click" @command="(cmd: string | number | object) => handleCommand(cmd, tag)">
              <span class="more-action-btn">
                <el-icon><More /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="rename" :icon="Edit">重新命名</el-dropdown-item>
                  <el-dropdown-item command="delete" :icon="Delete" class="danger-text">刪除標籤</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-checkbox-group>

        <div v-if="filteredTags.length === 0" class="empty-tip">
          沒有找到相符的標籤
        </div>
      </div>
    </aside>

    <!-- ================= 右側卡片網格區 (75%) ================= -->
    <main class="tag-content-panel">
      <!-- 頂部篩選狀態提示列 -->
      <div class="filter-status-bar">
        <div class="status-info">
          <span class="status-label">當前篩選狀態：</span>
          <template v-if="selectedTagIds.length > 0">
            <el-tag
              v-for="t in selectedTagObjects"
              :key="t.id"
              size="small"
              type="primary"
              effect="plain"
              class="active-filter-tag"
            >
              #{{ t.name }}
            </el-tag>
            <span class="operator-badge">({{ operator }})</span>
          </template>
          <span v-else class="no-selection-text">尚未選取任何標籤，請從左側勾選以檢視卡片</span>
        </div>
        <span class="result-count" v-if="selectedTagIds.length > 0">
          共找到 {{ cardsData?.length ?? 0 }} 張卡片
        </span>
      </div>

      <!-- 卡片瀑布流/網格展示區 -->
      <div ref="cardsGridWrapperRef" class="cards-grid-wrapper" v-loading="isCardsLoading">
        <template v-if="selectedTagIds.length > 0">
          <div v-if="cardsData && cardsData.length > 0" class="cards-grid">
            <CardItem
              v-for="card in cardsData"
              :key="card.id"
              :data="card"
              view-mode="text"
              :board-type="TAG_BOARD_TYPE"
              @open-detail="handleOpenDetail"
            />
          </div>
          <div v-else class="empty-cards">
            <p>沒有符合條件的卡片內容</p>
          </div>
        </template>
        <div v-else class="initial-placeholder">
          <el-icon class="placeholder-icon"><CollectionTag /></el-icon>
          <p>請在左側勾選標籤，在此處即時聯動檢視筆記卡片</p>
        </div>
      </div>
    </main>

    <!-- ================= 重新命名對話框 (Dialog) ================= -->
    <el-dialog
      v-model="renameDialogVisible"
      title="重新命名標籤"
      width="400px"
      destroy-on-close
      @closed="handleDialogClosed"
    >
      <div class="dialog-body">
        <p class="dialog-tip">修改標籤名稱將同步更新所有關聯此標籤的卡片：</p>
        <el-input
          v-model="newTagNameInput"
          placeholder="輸入新的標籤名稱..."
          @keyup.enter="handleConfirmRename"
        />
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="renameDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="renameMutation.isPending.value" @click="handleConfirmRename">
            確認修改
          </el-button>
        </span>
      </template>
    </el-dialog>

  </div>
</template>

<style scoped>
/* 整體主畫面左右雙欄佈局 (預設案桌面版) */
.tag-center-container {
  display: flex;
  height: calc(100dvh - 40px);
  box-sizing: border-box;
  background-color: var(--el-bg-color-page);
  overflow: hidden;
}

/* 左側面板 (桌面版 280px) */
.tag-sidebar-panel {
  width: 280px;
  min-width: 260px;
  background: var(--el-bg-color);
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  padding: 16px;
  box-sizing: border-box;
  height: 100%;
}

.panel-header {
  margin-bottom: 12px;
}
.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0;
}

.search-box {
  margin-bottom: 12px;
}

.controls-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}
.divider {
  color: var(--el-border-color);
  font-size: 12px;
}

/* 標籤清單與滾動條 */
.tag-checkbox-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

:deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.tag-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.2s;
}
.tag-item-row:hover {
  background-color: var(--el-fill-color-light);
}

.tag-checkbox {
  display: flex;
  align-items: center;
  flex: 1;
  margin-right: 8px;
  overflow: hidden;
}
:deep(.el-checkbox__label) {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  overflow: hidden;
}
.tag-name {
  color: var(--el-text-color-regular);
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tag-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.more-action-btn {
  cursor: pointer;
  color: var(--el-text-color-secondary);
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
}
.more-action-btn:hover {
  background-color: var(--el-fill-color);
  color: var(--el-text-color-primary);
}
.danger-text {
  color: var(--el-color-danger) !important;
}

.empty-tip {
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  margin-top: 24px;
}

/* 右側卡片內容面板 (桌面版 75%) */
.tag-content-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color-page);
  overflow: hidden;
}

.filter-status-bar {
  padding: 14px 20px;
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.status-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.status-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  font-weight: 500;
}
.no-selection-text {
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}
.active-filter-tag {
  font-weight: 500;
}
.operator-badge {
  font-size: 12px;
  font-weight: bold;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  padding: 2px 6px;
  border-radius: 4px;
}
.result-count {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.cards-grid-wrapper {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  box-sizing: border-box;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}
.empty-cards, .initial-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--el-text-color-secondary);
  gap: 12px;
}
.placeholder-icon {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dialog-tip {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

/* =====================================================
   RWD 手機版適應 (螢幕寬度小於 768px 時觸發)
   ===================================================== */
@media (max-width: 1200px) {
  .tag-center-container {
    height: calc(100dvh - 88px);
  }
}

@media (max-width: 768px) {
  .tag-center-container {
    flex-direction: column; /* 改為上下垂直堆疊 */
    height: auto;
    min-height: calc(100dvh - 88px);
    overflow-y: auto;
  }

  .tag-sidebar-panel {
    width: 100%;       /* 佔滿手機全寬 */
    height: auto;
    max-height: 320px; /* 限制高度並允許內部捲動，避免標籤區太長 */
    border-right: none;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .tag-content-panel {
    width: 100%;       /* 佔滿手機全寬 */
    height: auto;
    min-height: 400px;
  }
}
</style>
