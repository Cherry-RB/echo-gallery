<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Edit, MoreFilled } from '@element-plus/icons-vue'
import DetailShell from '../../components/ui/DetailShell.vue'
import DetailHeading from '../../components/ui/DetailHeading.vue'
import SectionHeader from '../../components/ui/SectionHeader.vue'
import RecordCount from '../../components/ui/RecordCount.vue'
import HistoryTimeline from '../../components/ui/HistoryTimeline.vue'
import CoverImage from '../../components/ui/CoverImage.vue'
import GoalEditor from '../../components/goal/GoalEditor.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalKinds, goalMetrics, goalStatuses, isGoalPeriodDue } from '../../types/goal'
import { hasGoalCards } from '../../utils/goalPresentation'
import { formatDate } from '../../utils/formatDate'
import type { GoalDefinition } from '../../types/goal'
type GoalRevision = { revision: number; definition: GoalDefinition; reason: string; effectiveAt: string }
const route = useRoute(), router = useRouter(), client = useQueryClient(), visible = ref(false), busy = ref(false), error = ref('')
const owner = localStorage.getItem('id') || '', id = computed(() => Number(route.params.id))
const editorSection = ref<'goal' | 'sources'>('goal')
function openEditor(section: 'goal' | 'sources' = 'goal') { editorSection.value = section; visible.value = true }
const removed = ref(false)
const query = useQuery({ queryKey: computed(() => ['goals', owner, 'detail', id.value]), queryFn: () => goalApi.detail(id.value), enabled: computed(() => !removed.value), retry: (count, e) => (e as { response?: { status?: number } }).response?.status !== 404 && count < 2 })
const goal = computed(() => query.data.value)
const relatedEnabled = computed(() => !removed.value && !!goal.value && !query.isError.value)
const history = useQuery({ queryKey: computed(() => ['goals', owner, 'revisions', id.value]), queryFn: () => goalApi.revisions(id.value), enabled: relatedEnabled })
const sources = useQuery({ queryKey: computed(() => ['goals', owner, 'sources', id.value]), queryFn: () => goalApi.sources(id.value), enabled: relatedEnabled })
const missing = computed(() => (query.error.value as { response?: { status?: number } } | null)?.response?.status === 404)
const included = computed(() => sources.data.value?.filter(s => !s.excluded) ?? [])
const excluded = computed(() => sources.data.value?.filter(s => s.excluded) ?? [])
const canRead = computed(() => goal.value && goal.value.cardCount > 0 && hasGoalCards(goal.value.definition))
const sourceSignature = (definition: GoalDefinition) => definition.sources.map(source => `${source.kind}:${source.id}:${source.excluded}`).sort().join('|')
const metricSignature = (definition: GoalDefinition) => [...definition.metrics].sort().join('|')
function revisionTitle(item: GoalRevision, previous?: GoalRevision) {
  if (item.revision === 1) return '建立目標'
  const reason = item.reason.trim()
  if (reason && !['儲存目標設定', '更新目標'].includes(reason)) return reason
  if (!previous) return '更新目標'
  const changes: string[] = []
  if (item.definition.name !== previous.definition.name) changes.push('修改名稱')
  if (item.definition.startsOn !== previous.definition.startsOn || item.definition.endsOn !== previous.definition.endsOn) changes.push('調整期間')
  if (item.definition.status !== previous.definition.status) changes.push(`狀態改為${goalStatuses[item.definition.status]}`)
  if (item.definition.pinned !== previous.definition.pinned) changes.push(item.definition.pinned ? '固定於總覽' : '取消固定於總覽')
  if (item.definition.archived !== previous.definition.archived) changes.push(item.definition.archived ? '封存目標' : '恢復目標')
  if (sourceSignature(item.definition) !== sourceSignature(previous.definition) || item.definition.allTags !== previous.definition.allTags) changes.push('更新追蹤內容')
  if (metricSignature(item.definition) !== metricSignature(previous.definition)) changes.push('調整顯示數據')
  if (item.definition.expectation !== previous.definition.expectation || item.definition.motivation !== previous.definition.motivation || item.definition.imageUrl !== previous.definition.imageUrl) changes.push('更新目標內容')
  return changes.length ? `更新目標：${changes.join('、')}` : '更新目標'
}
const nameChanged = (item: GoalRevision) => item.definition.name !== goal.value?.definition.name
async function saved() { await client.invalidateQueries({ queryKey: ['goals'] }) }
async function leaveDeleted(alreadyMissing = false) {
  removed.value = true; visible.value = false
  for (const section of ['detail', 'sources', 'revisions']) {
    const queryKey = ['goals', owner, section, id.value]
    await client.cancelQueries({ queryKey, exact: true })
    client.removeQueries({ queryKey, exact: true })
  }
  await router.replace('/goals')
  ElMessage.success(alreadyMissing ? '目標已不存在，已返回管理目標' : '目標已刪除')
  await saved()
}
async function command(value: string) {
  if (!goal.value || busy.value) return
  if (value === 'delete') {
    try { await ElMessageBox.confirm('會刪除這個目標及專屬追蹤歷史，原卡片、實驗與作品保留。', '刪除目標', { type: 'warning', confirmButtonText: '刪除目標', cancelButtonText: '取消' }) } catch { return }
  }
  busy.value = true; error.value = ''
  try {
    if (value === 'delete') { await goalApi.delete(id.value, goal.value.version); await leaveDeleted() }
    else if (value === 'archive') { await goalApi.save(id.value, { ...goal.value.definition, archived: !goal.value.definition.archived }, goal.value.version, goal.value.definition.archived ? '還原目標' : '封存目標', crypto.randomUUID()); await saved() }
  } catch (e) {
    if (value === 'delete' && (e as { response?: { status?: number } }).response?.status === 404) await leaveDeleted(true)
    else error.value = '操作失敗，可能資料已更新。請重新讀取後再試。'
  }
  finally { busy.value = false }
}
</script>
<template>
  <DetailShell class="goal-detail">
    <template #navigation><nav class="app-detail-navigation" aria-label="目標導覽"><el-button text :icon="ArrowLeft" @click="router.push('/goals')">返回管理目標</el-button><div v-if="goal && !removed" class="detail-actions"><el-button type="primary" plain :icon="Edit" :disabled="busy" @click="openEditor()">編輯目標</el-button><el-dropdown trigger="click" :disabled="busy" @command="command"><el-button :icon="MoreFilled" :loading="busy" aria-label="更多目標操作">更多操作</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="archive">{{ goal.definition.archived ? '還原目標' : '封存目標' }}</el-dropdown-item><el-dropdown-item command="delete" divided>刪除目標</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div></nav></template>
    <p v-if="removed" class="goal-help" role="status">目標已刪除，正在返回列表…</p>
    <el-skeleton v-else-if="query.isPending.value" :rows="6" animated />
    <el-result v-else-if="query.isError.value" :icon="missing ? 'info' : 'error'" :title="missing ? '這個目標已不存在' : '讀取目標失敗'" :sub-title="missing ? '目標可能已在其他裝置刪除。' : '請確認連線後再試一次。'"><template #extra><el-button @click="router.replace('/goals')">返回管理目標</el-button><el-button v-if="!missing" type="primary" @click="query.refetch()">重新載入</el-button></template></el-result>
    <template v-else-if="goal">
      <DetailHeading :title="goal.definition.name" eyebrow="目標"><template #metadata><el-tag type="info" effect="plain" size="small">{{ goalStatuses[goal.effectiveStatus] }}</el-tag><span>{{ goal.definition.startsOn }} — {{ goal.definition.endsOn }}</span><el-tag v-if="goal.definition.archived" type="info" size="small">已封存</el-tag><span v-if="goal.definition.pinned">固定於總覽</span><el-button v-if="goal.definition.metrics.includes('M01') && canRead" class="goal-related-card-link" type="primary" link size="small" @click="router.push({ path: '/board/goals', query: { goal: id } })">目前相關卡片 <RecordCount :count="goal.cardCount" /></el-button></template></DetailHeading>
      <p v-if="isGoalPeriodDue(goal)" class="goal-due">期間已到，可回看這段時間的方向與累積。</p>
      <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button @click="query.refetch()">重新讀取</el-button></el-alert>
      <div class="goal-content app-stack">
        <section v-if="goal.definition.expectation || goal.definition.motivation || goal.definition.imageUrl" class="app-panel app-panel--padded app-section">
          <SectionHeader :title="goal.definition.expectation ? '期望結果' : '目標背景'" />
          <CoverImage v-if="goal.definition.imageUrl" :src="goal.definition.imageUrl" alt="目標圖片" />
          <p v-if="goal.definition.expectation" class="goal-text">{{ goal.definition.expectation }}</p>
          <div v-if="goal.definition.motivation" class="goal-background"><h3>為什麼想做</h3><p class="goal-text">{{ goal.definition.motivation }}</p></div>
        </section>
        <p v-else class="goal-help">期望結果可以之後再補充。<el-button type="primary" link size="small" @click="openEditor()">編輯目標</el-button></p>
        <section v-if="goal.definition.sources.length" class="app-panel app-panel--padded app-section">
          <SectionHeader title="相關資料"><template #actions><el-button type="primary" link size="small" @click="openEditor('sources')">編輯來源</el-button><el-button v-if="canRead && !goal.definition.metrics.includes('M01')" type="primary" link size="small" @click="router.push({ path: '/board/goals', query: { goal: id } })">翻閱相關卡片</el-button></template></SectionHeader>
          <p v-if="sources.isPending.value" class="goal-help">載入來源中…</p>
          <el-alert v-else-if="sources.isError.value" title="來源載入失敗" type="error" :closable="false"><el-button @click="sources.refetch()">重試</el-button></el-alert>
          <template v-else><ul class="goal-sources"><li v-for="source in included" :key="`${source.kind}:${source.id}`"><span>{{ goalKinds[source.kind] }}</span><router-link v-if="source.available && source.path" :to="source.path">{{ source.title }}</router-link><strong v-else>{{ source.title }}</strong><small v-if="source.parentTitle">{{ source.parentTitle }}</small></li></ul><details v-if="excluded.length" class="goal-exceptions"><summary>已調整的追蹤範圍 · {{ excluded.length }} 項</summary><p v-for="source in excluded" :key="`${source.kind}:${source.id}`" class="goal-help">{{ source.kind === 'CARD' ? '不納入卡片' : '未透過實驗納入探索線' }}：{{ source.title }}<template v-if="source.parentTitle"> · {{ source.parentTitle }}</template></p></details></template>
        </section>
        <p v-else class="goal-help">未設定站內追蹤，也可以只記下目標。<el-button type="primary" link size="small" @click="openEditor('sources')">選擇相關資料</el-button></p>
        <p v-if="history.isPending.value" class="goal-help">正在讀取調整紀錄…</p>
        <el-alert v-else-if="history.isError.value" title="調整紀錄載入失敗" type="error" :closable="false"><el-button @click="history.refetch()">重試</el-button></el-alert>
        <section v-else-if="history.data.value?.length" class="goal-history app-panel app-panel--padded" aria-labelledby="goal-history-title">
          <header class="goal-history-heading"><h2 id="goal-history-title">最近調整紀錄 <RecordCount :count="history.data.value.length" /></h2></header>
          <p v-if="history.data.value.length >= 100" class="goal-help">目前顯示最近 100 筆。</p>
          <HistoryTimeline class="goal-history-list">
            <li v-for="(item, index) in history.data.value" :key="item.revision" class="goal-history-item">
              <span class="timeline-marker" aria-hidden="true"></span>
              <details class="revision-settings">
                <summary>
                  <span class="revision-event-copy"><time :datetime="item.effectiveAt">{{ formatDate(item.effectiveAt, 'YYYY-MM-DD HH:mm') }}</time><strong>{{ revisionTitle(item, history.data.value[index + 1]) }}</strong></span>
                  <span class="revision-toggle" aria-hidden="true"><el-icon class="revision-chevron"><ArrowRight /></el-icon><span class="revision-closed">當時設定</span><span class="revision-open">收起設定</span></span>
                </summary>
                <dl class="goal-revision">
                  <div v-if="nameChanged(item)" class="revision-wide"><dt>當時名稱</dt><dd>{{ item.definition.name }}</dd></div>
                  <div><dt>狀態</dt><dd>{{ goalStatuses[item.definition.status] }}</dd></div>
                  <div><dt>期間</dt><dd>{{ item.definition.startsOn }} — {{ item.definition.endsOn }}</dd></div>
                  <div v-if="item.definition.expectation" class="revision-wide"><dt>期望結果</dt><dd class="goal-text">{{ item.definition.expectation }}</dd></div>
                  <div v-if="item.definition.sources.filter(s => !s.excluded).length"><dt>納入追蹤</dt><dd>{{ item.definition.sources.filter(s => !s.excluded).length }} 項</dd></div>
                  <div v-if="item.definition.sources.filter(s => s.excluded).length"><dt>排除設定</dt><dd>{{ item.definition.sources.filter(s => s.excluded).length }} 項</dd></div>
                  <div v-if="item.definition.metrics.length" class="revision-wide"><dt>顯示數據</dt><dd>{{ item.definition.metrics.map(key => goalMetrics[key]).join('、') }}</dd></div>
                </dl>
              </details>
            </li>
          </HistoryTimeline>
        </section>
      </div>
      <GoalEditor v-model="visible" :goal="goal" :initial-section="editorSection" @saved="saved" />
    </template>
  </DetailShell>
</template>
<style scoped>
.goal-content { margin-top: var(--space-lg); }
.goal-text { max-width: var(--reading-max-width); white-space: pre-wrap; overflow-wrap: anywhere; color: var(--el-text-color-regular); }
.goal-background { display: grid; gap: var(--space-xs); }
.goal-background h3 { color: var(--el-text-color-secondary); font-size: var(--type-secondary); font-weight: var(--weight-medium); }
.goal-help { color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.goal-due { margin-top: var(--space-sm); color: var(--el-color-warning); font-size: var(--type-secondary); }
.goal-related-card-link { display: inline-flex; align-items: center; gap: var(--space-2xs); padding: 0; }
.goal-related-card-link :deep(.record-count) { margin: 0; }
.goal-sources { display: grid; gap: var(--space-sm); list-style: none; padding: 0; margin: 0; }
.goal-sources li { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-xs); overflow-wrap: anywhere; }
.goal-sources li > span, .goal-sources small { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-sources a { color: var(--el-color-primary); text-decoration: none; }
.goal-sources a:hover { text-decoration: underline; }
.goal-sources strong { font-size: var(--type-secondary); font-weight: var(--weight-medium); }
.goal-exceptions summary, .revision-settings summary { cursor: pointer; font-size: var(--type-secondary); line-height: var(--leading-ui); }
.goal-exceptions p { margin-top: var(--space-xs); }
.goal-history-heading { margin-bottom: var(--space-sm); }
.goal-history-heading h2 { display: flex; align-items: center; gap: var(--space-xs); margin: 0; color: var(--el-text-color-primary); font-size: var(--type-section-title); font-weight: var(--weight-semibold); line-height: var(--leading-section-title); }
.goal-history-list { padding-left: 0; }
.goal-history-item { display: grid; grid-template-columns: 20px minmax(0, 1fr); gap: var(--space-sm); padding-bottom: var(--space-xs); }
.goal-history-item:last-child { padding-bottom: 0; }
.timeline-marker { position: relative; display: block; min-height: 100%; }
.timeline-marker::before { position: absolute; top: 10px; bottom: -8px; left: 9px; width: 1px; background: var(--el-border-color); content: ''; }
.timeline-marker::after { position: absolute; top: 6px; left: 5px; width: 7px; height: 7px; border: 2px solid var(--el-bg-color); border-radius: 50%; background: var(--el-color-primary); box-shadow: 0 0 0 1px var(--el-color-primary-light-5); content: ''; }
.goal-history-item:last-child .timeline-marker::before { bottom: auto; height: 8px; }
.revision-settings { min-width: 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.goal-history-item:last-child .revision-settings { border-bottom: 0; }
.revision-settings > summary { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); min-height: 40px; padding: var(--space-xs) 0; cursor: pointer; list-style: none; }
.revision-settings > summary::-webkit-details-marker { display: none; }
.revision-settings > summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.revision-settings > summary:hover .revision-event-copy strong { color: var(--el-color-primary); }
.revision-event-copy { display: flex; min-width: 0; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs); }
.revision-event-copy time { flex: 0 0 auto; color: var(--el-text-color-placeholder); font-size: var(--type-metadata); }
.revision-event-copy strong { min-width: 0; color: var(--el-text-color-primary); font-size: var(--type-secondary); font-weight: var(--weight-medium); overflow-wrap: anywhere; }
.revision-toggle { display: inline-flex; align-items: center; gap: var(--space-2xs); flex-shrink: 0; color: var(--el-color-primary); font-size: var(--type-metadata); }
.revision-open, .revision-settings[open] > summary .revision-closed { display: none; }
.revision-settings[open] > summary .revision-open { display: inline; }
.revision-settings[open] > summary .revision-chevron { transform: rotate(90deg); }
.goal-revision { display: flex; flex-wrap: wrap; gap: var(--space-2xs) var(--space-md); margin: 0; padding: 0 0 var(--space-sm); color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.goal-revision > div { display: inline-flex; min-width: 0; gap: var(--space-2xs); }
.goal-revision dt { color: var(--el-text-color-secondary); font-size: inherit; }
.goal-revision dt::after { content: '：'; }
.goal-revision dd { margin: 0; color: inherit; }
.revision-wide { flex-basis: 100%; }
</style>
