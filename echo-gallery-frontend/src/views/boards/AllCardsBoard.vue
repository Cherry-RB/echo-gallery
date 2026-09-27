<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BoardFlex from "../../components/Board-Flex.vue";
import type { BoardType } from '../../types/board'

const views = [
  { value: 'updated', label: '更新' },
  { value: 'hot', label: '熱門' },
  { value: 'random', label: '隨機起點' },
  { value: 'archived', label: '封存' },
  { value: 'snoozed', label: '稍後再看（> 10）' },
] as const

type CardLibraryView = typeof views[number]['value']

const route = useRoute()
const router = useRouter()
const randomSessionKey = ref(0)
const validViews = new Set<CardLibraryView>(views.map(view => view.value))

const selectedView = computed<CardLibraryView>(() => {
  const candidate = String(route.query.view ?? 'updated') as CardLibraryView
  return validViews.has(candidate) ? candidate : 'updated'
})

const boardType = computed<BoardType>(() => ({
  updated: 'all',
  hot: 'hot',
  random: 'random',
  archived: 'archived',
  snoozed: 'snoozed',
})[selectedView.value] as BoardType)

const description = computed(() => ({
  updated: '最近更新的未封存卡片',
  hot: '依收藏次數查看未封存卡片',
  random: '從隨機起點開始瀏覽未封存卡片',
  archived: '查看與管理已封存卡片',
  snoozed: '查看累計稍後再看超過 10 次的卡片',
})[selectedView.value])

const boardFilters = computed(() => selectedView.value === 'snoozed'
  ? { threshold: 10 }
  : selectedView.value === 'random'
    ? { randomSessionKey: randomSessionKey.value }
    : {})

const handleViewChange = (value: CardLibraryView) => {
  if (value === 'random') randomSessionKey.value += 1
  router.replace({
    path: '/board/all',
    query: value === 'updated' ? {} : { view: value },
  })
}
</script>

<template>
  <BoardFlex
    :board-type="boardType"
    :filters="boardFilters"
    title="全部卡片"
    :description="description"
  >
    <template #header-actions>
      <div class="view-selector">
        <span>檢視方式</span>
        <el-select
          :model-value="selectedView"
          aria-label="卡片檢視方式"
          @change="handleViewChange"
        >
          <el-option
            v-for="view in views"
            :key="view.value"
            :label="view.label"
            :value="view.value"
          />
        </el-select>
      </div>
    </template>
  </BoardFlex>
</template>

<style scoped>
.view-selector {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.view-selector > span {
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
  white-space: nowrap;
}

.view-selector :deep(.el-select) {
  width: 158px;
}

@media (max-width: 768px) {
  .view-selector {
    justify-content: space-between;
    width: 100%;
  }

  .view-selector :deep(.el-select) {
    width: min(210px, 62vw);
  }
}
</style>
