<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageHeader from '../../components/ui/PageHeader.vue'
import AppEmptyState from '../../components/ui/AppEmptyState.vue'
import GoalScopeDialog from '../../components/goal/GoalScopeDialog.vue'
import GoalReminderNotice from '../../components/goal/GoalReminderNotice.vue'
import { MasonryWall } from '@yeger/vue-masonry-wall'
import { collectionLayoutTokens } from '../../utils/designTokens'
import CardItem from '../../components/CardItem.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalPreference } from '../../types/goal'
import { useGoalRound } from '../../composables/useGoalRound'
const router = useRouter(), route = useRoute(), owner = localStorage.getItem('id') || ''
const preference = ref<GoalPreference>({ allActive: true, goalIds: [], includePaused: true, includeArchived: false, version: 0 })
const scopeDraft = ref<GoalPreference>({ ...preference.value }), applied = ref<GoalPreference | null>(null)
const ready = ref(false), initialError = ref(''), applying = ref(false), scopeVisible = ref(false), savedButNotLoaded = ref(false)
const { current, round, busy, error, restore, load } = useGoalRound(owner)
const mode = computed(() => applied.value?.allActive ?? preference.value.allActive)
const emptyMessages: Record<string, string> = {
  NO_ACTIVE_GOALS: '目前沒有進行中的目標，可在範圍設定中自行選擇其他狀態的目標。',
  NO_SELECTION: '尚未選擇目標，可在範圍設定中選擇。',
  NO_AVAILABLE_GOALS: '選定目標已封存或不可用，請調整範圍。',
  NO_CARD_SOURCES: '選定目標尚未設定卡片來源，也可以先保留目標。',
  NO_MATCHING_CARDS: '目前來源與篩選條件沒有符合的卡片。',
}
function configure(allActive = preference.value.allActive) {
  scopeDraft.value = { ...preference.value, goalIds: [...preference.value.goalIds], allActive }; scopeVisible.value = true
}
async function initialize() {
  initialError.value = ''
  try {
    preference.value = await goalApi.preference()
    const id = Number(route.query.goal)
    if (route.query.goal && Number.isSafeInteger(id)) {
      const goal = await goalApi.detail(id)
      if (goal.definition.archived) { initialError.value = '這個目標已封存，請重新選擇範圍。'; return }
      preference.value = { ...preference.value, allActive: false, goalIds: [id] }
    }
    const hadResult = Boolean(current.value)
    if (!hadResult) { applied.value = JSON.parse(JSON.stringify(preference.value)); restore(preference.value) }
    ready.value = true
    await load(preference.value, hadResult ? 'start' : round.value ? 'resume' : 'start')
    if (!error.value) applied.value = JSON.parse(JSON.stringify(preference.value))
  } catch { initialError.value = '目標範圍讀取失敗，請重新載入。' }
}
async function apply(value: GoalPreference) {
  applying.value = true; initialError.value = ''
  try {
    preference.value = await goalApi.savePreference(value)
    savedButNotLoaded.value = true
    await load(preference.value, 'start')
    if (!error.value) {
      applied.value = JSON.parse(JSON.stringify(preference.value)); savedButNotLoaded.value = false; scopeVisible.value = false
    } else scopeVisible.value = false
  } catch { initialError.value = '設定未能保存，可能另一裝置已更新。請重新載入設定；原卡片仍保留。' }
  finally { applying.value = false }
}
async function retry() {
  if (savedButNotLoaded.value) {
    await load(preference.value, 'start')
    if (!error.value) { applied.value = JSON.parse(JSON.stringify(preference.value)); savedButNotLoaded.value = false }
  } else if (applied.value) await load(applied.value, 'resume')
}
onMounted(initialize)
</script>
<template>
  <section class="goal-board app-page app-page--collection">
    <PageHeader title="目標看板" description="記住你關心的方向，每次翻閱幾張相關材料。看過不會改變今日回流安排。">
      <template #actions><div class="scope-selector"><span>目標範圍</span><el-select :model-value="mode" aria-label="目標範圍" :disabled="busy || applying" @change="configure($event)"><el-option :value="true" label="所有進行中目標" /><el-option :value="false" label="自行選擇目標" /></el-select></div><el-button :disabled="busy || applying" @click="configure()">範圍設定</el-button><el-button @click="router.push('/goals')">管理目標</el-button></template>
    </PageHeader>
    <GoalReminderNotice />
    <el-alert v-if="initialError" :title="initialError" type="error" :closable="false"><el-button @click="initialize">重新載入設定</el-button></el-alert>
    <el-alert v-if="error" :title="savedButNotLoaded ? '新設定已保存，但材料尚未載入；下方仍是原範圍。' : error" type="error" :closable="false"><el-button :disabled="busy" @click="retry">重試</el-button></el-alert>
    <p v-if="current" class="goal-results" aria-live="polite"><template v-if="current.selectedGoalCount != null">已選 {{ current.selectedGoalCount }} 個目標 · </template>相關卡片 {{ current.total }} 張</p>
    <div class="goal-workspace app-workspace app-workspace--edge-to-edge" :aria-busy="busy">
      <div v-if="!current && (!ready || busy) && !initialError && !error" class="app-empty-state" v-loading="true">正在準備目標材料</div>
      <template v-if="current">
        <p v-if="busy" class="goal-notice" role="status">正在更新材料…</p>
        <AppEmptyState v-if="!current.total" :description="emptyMessages[current.emptyReason || ''] || '目前範圍沒有符合的卡片。'" />
        <AppEmptyState v-else-if="!current.content.length" description="這一批目前沒有可顯示的卡片，可以回看或重新洗牌。" />
        <MasonryWall v-else :items="current.content" :column-width="collectionLayoutTokens.masonryColumnWidth" :gap="collectionLayoutTokens.gridGap"><template #default="{ item }"><CardItem :data="item" view-mode="text" board-type="goal" @open-detail="router.push({ name: 'CardDetail', params: { id: item.id }, query: { from: 'goal' } })" /></template></MasonryWall>
      </template>
    </div>
    <div v-if="current?.total" class="goal-navigation"><el-button :disabled="busy || !round?.index" @click="applied && load(applied, 'previous')">上一批</el-button><el-button type="primary" plain :disabled="busy || !current.hasMore" @click="applied && load(applied, 'next')">下一批</el-button><el-button :disabled="busy" @click="applied && load(applied, 'start')">重新洗牌</el-button></div>
    <p v-if="current?.total && !current.hasMore" class="goal-end">這一輪已到末尾，可以回看或重新洗牌取得最新材料。</p>
    <GoalScopeDialog v-model="scopeVisible" :preference="scopeDraft" :busy="applying" @confirm="apply" />
  </section>
</template>
<style scoped>
.scope-selector { display: flex; align-items: center; gap: var(--space-xs); }
.scope-selector > span { color: var(--el-text-color-secondary); font-size: var(--type-metadata); white-space: nowrap; }
.scope-selector .el-select { width: 11rem; }
.goal-board :deep(.app-page-actions .el-button) { margin-left: 0; }
.goal-results, .goal-notice { margin-block: var(--space-sm); color: var(--el-text-color-regular); font-size: var(--type-secondary); }
.goal-navigation { display: flex; justify-content: center; gap: var(--space-sm); flex-wrap: wrap; margin-top: var(--space-lg); }
.goal-navigation .el-button { margin-left: 0; }
.goal-end { margin-top: var(--space-sm); text-align: center; color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.goal-board > .el-alert { margin-bottom: var(--space-sm); }
@media(max-width:760px) { .scope-selector { width: 100%; justify-content: space-between; } .scope-selector .el-select { width: min(210px, 62vw); } }
</style>
