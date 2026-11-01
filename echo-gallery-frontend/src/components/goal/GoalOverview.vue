<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import SectionHeader from '../ui/SectionHeader.vue'
import RecordCount from '../ui/RecordCount.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalStatuses, isGoalPeriodDue } from '../../types/goal'
import { hasGoalCards } from '../../utils/goalPresentation'
const owner = localStorage.getItem('id') || ''
const router = useRouter()
const query = useQuery({ queryKey: ['goals', owner, 'overview'], queryFn: goalApi.overview })
const storageKey = `goal-overview-expanded:${owner}`
const expanded = ref(true)
try { expanded.value = localStorage.getItem(storageKey) !== 'false' } catch { /* 偏好不可讀時仍可使用。 */ }
watch(expanded, value => { try { localStorage.setItem(storageKey, String(value)) } catch { /* 不阻止閱讀。 */ } })
const names = computed(() => query.data.value?.map(goal => goal.definition.name).join('、') || '')
const openGoal = (id: number) => router.push(`/goals/${id}`)
</script>
<template>
  <section class="goal-overview app-panel app-panel--padded" aria-label="固定目標">
    <SectionHeader title="固定目標">
      <template v-if="!expanded && names" #title-extra><span class="goal-collapsed-names">{{ names }}</span></template>
      <template #actions>
        <router-link class="goal-manage-link" to="/goals">管理目標</router-link>
        <el-button v-if="query.data.value?.length" text size="small" :icon="expanded ? ArrowUp : ArrowDown" :aria-expanded="expanded" aria-controls="goal-overview-content" @click="expanded = !expanded">{{ expanded ? '收合' : '展開' }}</el-button>
      </template>
    </SectionHeader>
    <el-alert v-if="query.isError.value" title="目標載入失敗" type="error" :closable="false"><el-button @click="query.refetch()">重試</el-button></el-alert>
    <div v-show="expanded || !query.data.value?.length" id="goal-overview-content" class="goal-overview-content">
    <p v-if="query.isPending.value">載入中…</p>
    <p v-if="query.isSuccess.value && !query.data.value?.length" class="goal-empty">尚未將目標放到總覽，可在「管理目標」設定。</p>
    <div v-if="query.data.value?.length" class="goal-overview-list">
      <article
        v-for="goal in query.data.value"
        :key="goal.id"
        class="goal-overview-item"
        role="link"
        tabindex="0"
        :aria-label="`查看目標：${goal.definition.name}`"
        @click="openGoal(goal.id)"
        @keydown.enter.self="openGoal(goal.id)"
        @keydown.space.self.prevent="openGoal(goal.id)"
      >
        <div class="goal-overview-copy">
          <div class="goal-name-row"><h3 class="goal-title" :title="goal.definition.name">{{ goal.definition.name }}</h3><span class="goal-status">{{ goalStatuses[goal.effectiveStatus] }}</span></div>
          <p v-if="goal.definition.expectation?.trim() && goal.definition.expectation.trim() !== goal.definition.name.trim()" class="goal-expectation">{{ goal.definition.expectation }}</p>
          <p class="goal-metadata">{{ goal.definition.startsOn }} — {{ goal.definition.endsOn }}<span v-if="isGoalPeriodDue(goal)"> · 期間已到</span></p>
          <div v-if="goal.definition.metrics.includes('M01')" class="goal-count"><span>目前相關卡片</span><RecordCount :count="goal.cardCount" /></div>
        </div>
        <router-link v-if="goal.cardCount > 0 && hasGoalCards(goal.definition)" class="goal-material-link" :to="`/board/goals?goal=${goal.id}`" @click.stop>翻閱卡片</router-link>
      </article>
    </div>
    </div>
  </section>
</template>
<style scoped>
.goal-overview { min-width: 0; }
.goal-overview-content { margin-top: var(--space-md); }
.goal-empty { color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.goal-manage-link, .goal-material-link { color: var(--el-color-primary); font-size: var(--type-metadata); text-decoration: none; }
.goal-manage-link:hover, .goal-material-link:hover { text-decoration: underline; }
.goal-overview-list { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--space-sm); }
.goal-overview-item { display: flex; flex-direction: column; justify-content: space-between; gap: var(--space-xs); min-width: 0; padding: 13px 14px; overflow: hidden; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--surface-summary); cursor: pointer; }
.goal-overview-item:hover, .goal-overview-item:focus-within { border-color: var(--el-color-primary-light-5); }
.goal-name-row { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-xs); }
.goal-status { flex-shrink: 0; color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-material-link { align-self: flex-end; }
.goal-material-link:focus-visible, .goal-manage-link:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.goal-collapsed-names { color: var(--el-text-color-secondary); font-size: var(--type-secondary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 28rem; }
.goal-expectation { display: -webkit-box; max-width: var(--reading-max-width); margin-top: var(--space-xs); color: var(--el-text-color-regular); font-size: var(--type-secondary); overflow: hidden; white-space: pre-line; overflow-wrap: anywhere; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.goal-overview-copy { min-width: 0; width: 100%; }
.goal-title { min-width: 0; margin: 0; color: var(--el-color-primary); font-size: var(--type-meta); font-weight: var(--weight-regular); line-height: var(--leading-ui); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.goal-metadata { margin-top: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-metadata); overflow-wrap: anywhere; }
.goal-count { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-xs); font-size: var(--type-secondary); color: var(--el-text-color-secondary); }
@media (max-width: 900px) { .goal-overview-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .goal-overview-list { grid-template-columns: minmax(0, 1fr); gap: var(--space-xs); } }
</style>
