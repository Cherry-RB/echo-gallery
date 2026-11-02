<script setup lang="ts">
import { computed, ref, watch } from 'vue'
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
import GoalProgressLogDialog from '../../components/goal/GoalProgressLogDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalKinds, goalMetrics, goalStatuses, isGoalPeriodDue } from '../../types/goal'
import { hasGoalCards } from '../../utils/goalPresentation'
import { formatDate } from '../../utils/formatDate'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import type { GoalDefinition, GoalMetric, GoalProgressLog, GoalReview, GoalSourceView, GoalStatus } from '../../types/goal'
type GoalRevision = { revision: number; definition: GoalDefinition; reason: string; effectiveAt: string }
const route = useRoute(), router = useRouter(), client = useQueryClient(), visible = ref(false), busy = ref(false), error = ref(''), evidenceMetric = ref<string | null>(null), evidenceBusy = ref<string | null>(null)
const owner = localStorage.getItem('id') || '', id = computed(() => Number(route.params.id))
const editorSection = ref<'goal' | 'sources'>('goal')
const editorInitialStatus = ref<GoalStatus | undefined>()
const logVisible = ref(false), editingLog = ref<GoalProgressLog | null>(null), reviewStarting = ref(false)
function openEditor(section: 'goal' | 'sources' = 'goal', initialStatus?: GoalStatus) { editorSection.value = section; editorInitialStatus.value = initialStatus; visible.value = true }
const removed = ref(false)
const query = useQuery({ queryKey: computed(() => ['goals', owner, 'detail', id.value]), queryFn: () => goalApi.detail(id.value), enabled: computed(() => !removed.value), retry: (count, e) => (e as { response?: { status?: number } }).response?.status !== 404 && count < 2 })
const goal = computed(() => query.data.value)
watch([() => route.query.action, goal], ([action, current]) => {
  if (!current || typeof action !== 'string' || !['adjust', 'end', 'log'].includes(action)) return
  if (action === 'log') openLog()
  else openEditor('goal', action === 'end' ? 'ENDED' : undefined)
  const query = { ...route.query }
  delete query.action
  void router.replace({ query })
}, { immediate: true })
const relatedEnabled = computed(() => !removed.value && !!goal.value && !query.isError.value)
const history = useQuery({ queryKey: computed(() => ['goals', owner, 'revisions', id.value]), queryFn: () => goalApi.revisions(id.value), enabled: relatedEnabled })
const sources = useQuery({ queryKey: computed(() => ['goals', owner, 'sources', id.value]), queryFn: () => goalApi.sources(id.value), enabled: relatedEnabled })
const metrics = useQuery({ queryKey: computed(() => ['goals', owner, 'metrics', id.value]), queryFn: () => goalApi.metrics(id.value), enabled: relatedEnabled })
const evidence = useQuery({ queryKey: computed(() => ['goals', owner, 'evidence', id.value, evidenceMetric.value]), queryFn: () => goalApi.evidence(id.value, evidenceMetric.value || undefined), enabled: computed(() => relatedEnabled.value && !!evidenceMetric.value) })
const logs = useQuery({ queryKey: computed(() => ['goals', owner, 'logs', id.value]), queryFn: () => goalApi.logs(id.value), enabled: relatedEnabled })
const reviews = useQuery({ queryKey: computed(() => ['goals', owner, 'reviews', id.value]), queryFn: () => goalApi.reviews(id.value), enabled: relatedEnabled })
function todayInTaipei() { return new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Taipei' }) }
const reviewEndsOn = computed(() => !goal.value ? '' : [goal.value.definition.endsOn, todayInTaipei()].sort()[0])
const latestSubmittedReview = computed<GoalReview | undefined>(() => (reviews.data.value ?? []).filter(review => review.status === 'SUBMITTED').sort((a, b) => b.endsOn.localeCompare(a.endsOn))[0])
const reviewItems = computed(() => reviews.data.value ?? [])
const reviewStartsOn = computed(() => {
  const last = latestSubmittedReview.value?.endsOn
  return last ? new Date(new Date(last + 'T00:00:00Z').getTime() + 86400000).toISOString().slice(0, 10) : goal.value?.definition.startsOn || ''
})
const pendingReview = computed(() => reviewItems.value.find(review => review.status === 'DRAFT' && review.startsOn === reviewStartsOn.value && review.endsOn <= reviewEndsOn.value))
const reviewActionLabel = computed(() => pendingReview.value ? '繼續復盤' : '開始復盤')
const nextReviewPeriod = computed(() => {
  if (reviews.isFetching.value) return ''
  if (pendingReview.value) return pendingReview.value.startsOn + ' — ' + pendingReview.value.endsOn
  if (!goal.value || reviewUnavailableReason.value) return ''
  return reviewStartsOn.value + ' — ' + reviewEndsOn.value
})
const reviewUnavailableReason = computed(() => {
  if (!goal.value || reviews.isPending.value) return ''
  if (reviews.isError.value) return '無法確認復盤紀錄，請先重試讀取。'
  if (pendingReview.value) return ''
  if (todayInTaipei() < goal.value.definition.startsOn) return '尚未進入可復盤期間。'
  const latest = latestSubmittedReview.value
  if (latest && latest.endsOn >= reviewEndsOn.value) return '本期已完成復盤；如需補充，請開啟紀錄編輯。'
  return ''
})
const missing = computed(() => (query.error.value as { response?: { status?: number } } | null)?.response?.status === 404)
const included = computed(() => sources.data.value?.filter(s => !s.excluded) ?? [])
const excluded = computed(() => sources.data.value?.filter(s => s.excluded) ?? [])
const sourceGroups = computed(() => Object.entries(goalKinds).map(([kind, label]) => ({ kind, label, sources: included.value.filter(source => source.kind === kind) })).filter(group => group.sources.length))
function sourceTitle(source: GoalSourceView) { return source.parentTitle ? `${source.parentTitle}／${source.title}` : source.title }
const canRead = computed(() => goal.value && goal.value.cardCount > 0 && hasGoalCards(goal.value.definition))
const availableMetrics = computed(() => metrics.data.value?.filter(metric => metric.coverageStatus !== 'NOT_AVAILABLE') ?? [])
const metricLabel = (metric: GoalMetric) => goalMetrics[metric.metricKey] || metric.metricKey
function openLog(log: GoalProgressLog | null = null) { editingLog.value = log; logVisible.value = true }
async function logSaved() { await client.invalidateQueries({ queryKey: ['goals', owner, 'logs', id.value] }); await client.invalidateQueries({ queryKey: ['goals', owner, 'metrics', id.value] }) }
async function removeLog(log: GoalProgressLog) {
  try {
    await ElMessageBox.confirm('刪除後無法復原這則補記。', '刪除補記', { type: 'warning', confirmButtonText: '刪除', cancelButtonText: '取消' })
    await goalApi.deleteLog(id.value, log.id, log.version); await logSaved(); ElMessage.success('已刪除補記')
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error('刪除補記失敗，請重新整理後再試。') }
}
async function startReview() {
  if (reviewStarting.value || reviews.isPending.value || reviews.isFetching.value || reviewUnavailableReason.value) return
  reviewStarting.value = true
  try { await router.push(pendingReview.value ? `/goals/${id.value}/reviews/${pendingReview.value.id}/edit` : `/goals/${id.value}/reviews/new`) }
  catch (e) { ElMessage.error(apiErrorMessage(e, '無法開始復盤，請重新整理後再試。')) }
  finally { reviewStarting.value = false }
}
async function toggleEvidence(item: { id: string; excluded: boolean; version: number }) {
  if (!goal.value || evidenceBusy.value) return
  try {
    const prompt = await ElMessageBox.prompt(item.excluded ? '請填寫恢復納入的原因。' : '請填寫排除這筆活動的原因。', item.excluded ? '恢復證據' : '排除證據', { inputPattern: /\S+/, inputErrorMessage: '請填寫原因', confirmButtonText: item.excluded ? '恢復納入' : '排除', cancelButtonText: '取消' })
    evidenceBusy.value = item.id; await goalApi.saveEvidence(id.value, item.id, !item.excluded, prompt.value, item.version); await client.invalidateQueries({ queryKey: ['goals', owner, 'metrics', id.value] }); await evidence.refetch()
  } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error('儲存證據設定失敗，請重新讀取後再試。') } finally { evidenceBusy.value = null }
}
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
          <SectionHeader :title="`追蹤內容 ${included.length} 項`"><template #actions><el-button type="primary" link size="small" @click="openEditor('sources')">編輯</el-button><el-button v-if="canRead && !goal.definition.metrics.includes('M01')" type="primary" link size="small" @click="router.push({ path: '/board/goals', query: { goal: id } })">翻閱相關卡片</el-button></template></SectionHeader>
          <p v-if="sources.isPending.value" class="goal-help">載入來源中…</p>
          <el-alert v-else-if="sources.isError.value" title="來源載入失敗" type="error" :closable="false"><el-button @click="sources.refetch()">重試</el-button></el-alert>
          <template v-else><dl class="goal-sources"><div v-for="group in sourceGroups" :key="group.kind"><dt>{{ group.label }}</dt><dd><template v-for="source in group.sources" :key="`${source.kind}:${source.id}`"><el-tag v-if="source.kind === 'TAG'" size="small" effect="plain">#{{ source.title }}</el-tag><router-link v-else-if="source.available && source.path" :to="source.path">{{ sourceTitle(source) }}</router-link><strong v-else>{{ sourceTitle(source) }}</strong></template></dd></div></dl><details v-if="excluded.length" class="goal-exceptions"><summary>已調整的追蹤範圍 · {{ excluded.length }} 項</summary><p v-for="source in excluded" :key="`${source.kind}:${source.id}`" class="goal-help">{{ source.kind === 'CARD' ? '不納入卡片' : '未透過實驗納入探索線' }}：{{ sourceTitle(source) }}</p></details></template>
        </section>
        <p v-else class="goal-help">未設定站內追蹤，也可以只記下目標。<el-button type="primary" link size="small" @click="openEditor('sources')">選擇相關資料</el-button></p>
        <section v-if="goal.definition.metrics.length" class="app-panel app-panel--padded app-section" aria-labelledby="goal-metrics-title">
          <SectionHeader title="期間觀測"><template #actions><span class="goal-help">{{ goal.definition.startsOn }} — {{ goal.definition.endsOn }}</span></template></SectionHeader>
          <p v-if="metrics.isPending.value" class="goal-help">正在整理期間資料…</p>
          <el-alert v-else-if="metrics.isError.value" title="期間資料載入失敗" type="error" :closable="false"><el-button @click="metrics.refetch()">重試</el-button></el-alert>
          <template v-else-if="availableMetrics.length"><dl class="goal-metrics"><div v-for="metric in availableMetrics" :key="metric.metricKey"><dt>{{ metricLabel(metric) }}</dt><dd>{{ metric.value }} {{ metric.unit }}</dd><small>{{ metric.mode === 'CURRENT' ? '目前保留資料' : `自 ${formatDate(metric.coverageStart, 'YYYY-MM-DD')} 起記錄` }}</small><el-button v-if="metric.mode === 'PERIOD'" type="primary" link size="small" @click="evidenceMetric = evidenceMetric === metric.metricKey ? null : metric.metricKey">{{ evidenceMetric === metric.metricKey ? '收起證據' : '查看證據' }}</el-button></div></dl><div v-if="evidenceMetric" class="goal-evidence"><p v-if="evidence.isPending.value" class="goal-help">正在讀取證據…</p><el-alert v-else-if="evidence.isError.value" title="證據載入失敗" type="error" :closable="false"><el-button @click="evidence.refetch()">重試</el-button></el-alert><ul v-else class="goal-evidence-list"><li v-for="item in evidence.data.value?.content" :key="item.id"><span><strong>{{ item.type === 'CONTENT_UPDATED' ? '內容修改' : item.type }}</strong><small>{{ formatDate(item.occurredAt, 'YYYY-MM-DD HH:mm') }}<template v-if="item.excluded"> · 已排除</template></small></span><el-button type="primary" link size="small" :loading="evidenceBusy === item.id" @click="toggleEvidence(item)">{{ item.excluded ? '恢復納入' : '排除' }}</el-button></li></ul><p v-if="!evidence.data.value?.content.length" class="goal-help">這段期間沒有可追查的活動。</p></div></template>
          <p v-else class="goal-help">已保存顯示偏好，但尚沒有可用的期間資料。</p>
        </section>
        <section class="app-panel app-panel--padded app-section" aria-labelledby="goal-progress-title">
          <SectionHeader title="補記與復盤">
            <template #actions><el-button type="primary" link size="small" @click="openLog()">新增補記</el-button><el-tooltip :disabled="!reviewUnavailableReason" :content="reviewUnavailableReason"><span><el-button type="primary" link size="small" :loading="reviewStarting" :disabled="reviews.isPending.value || reviews.isFetching.value || !!reviewUnavailableReason" @click="startReview">{{ reviewActionLabel }}</el-button></span></el-tooltip></template>
          </SectionHeader>
          <p class="goal-help">補記站外進度；復盤會保留本期設定與數據快照。</p>
          <el-alert v-if="logs.isError.value" title="補記讀取失敗" type="error" :closable="false"><el-button @click="logs.refetch()">重試</el-button></el-alert>
          <ul v-else-if="logs.data.value?.length" class="goal-log-list">
            <li v-for="log in logs.data.value" :key="log.id"><div><strong>{{ log.occurredOn }}</strong><span v-if="log.kind === 'MEASUREMENT'">{{ log.amount }} {{ log.unit }}</span><p v-if="log.content">{{ log.content }}</p></div><div class="goal-log-actions"><el-button type="primary" link size="small" @click="openLog(log)">編輯</el-button><el-button type="danger" link size="small" @click="removeLog(log)">刪除</el-button></div></li>
          </ul>
          <p v-else-if="!logs.isPending.value" class="goal-help">尚無補記。可在回顧前補上站外完成的活動或數值。</p>
          <div class="goal-review-list">
            <h3>復盤紀錄</h3>
            <p v-if="nextReviewPeriod" class="goal-help">{{ pendingReview ? '未完成期間' : '下次復盤涵蓋' }}：{{ nextReviewPeriod }}</p>
            <el-alert v-if="reviews.isError.value" title="復盤紀錄讀取失敗" type="error" :closable="false"><el-button @click="reviews.refetch()">重試</el-button></el-alert>
            <ul v-else-if="reviewItems.length">
              <li v-for="review in reviewItems" :key="review.id" class="goal-review-period">
                <div class="goal-review-period-heading">
                  <router-link :to="'/goals/' + id + '/reviews/' + review.id + (review.status === 'DRAFT' ? '/edit' : '')">{{ review.startsOn }} — {{ review.endsOn }}</router-link>
                  <span class="goal-review-period-actions"><el-tag size="small" :type="review.status === 'SUBMITTED' ? 'success' : 'info'">{{ review.status === 'SUBMITTED' ? '已完成' : '未完成' }}</el-tag></span>
                </div>
              </li>
            </ul>
            <p v-else-if="!reviews.isPending.value" class="goal-help">尚未建立復盤。</p>
            <p v-if="reviewUnavailableReason && !reviews.isPending.value" class="goal-help">{{ reviewUnavailableReason }}</p>
          </div>
        </section>
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
      <GoalEditor v-model="visible" :goal="goal" :initial-section="editorSection" :initial-status="editorInitialStatus" @saved="saved" />
      <GoalProgressLogDialog v-model="logVisible" :goal-id="id" :log="editingLog" @saved="logSaved" />
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
.goal-sources { display: grid; gap: var(--space-sm); margin: 0; }
.goal-sources > div { display: grid; grid-template-columns: 76px minmax(0, 1fr); gap: var(--space-sm); align-items: baseline; }
.goal-sources dt { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-sources dd { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-xs) var(--space-md); margin: 0; min-width: 0; }
.goal-sources a { color: var(--el-color-primary); text-decoration: none; overflow-wrap: anywhere; }
.goal-sources a:hover { text-decoration: underline; }
.goal-sources strong { font-size: var(--type-secondary); font-weight: var(--weight-medium); overflow-wrap: anywhere; }
.goal-exceptions summary, .revision-settings summary { cursor: pointer; font-size: var(--type-secondary); line-height: var(--leading-ui); }
.goal-metrics { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: var(--space-sm); margin: 0; }
.goal-metrics > div { display: grid; gap: var(--space-2xs); min-width: 0; padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: 8px; background: var(--surface-summary); }
.goal-metrics dt, .goal-metrics small { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-metrics dd { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-medium); }
.goal-evidence { margin-top: var(--space-md); padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-lighter); }
.goal-evidence-list { display: grid; gap: var(--space-xs); list-style: none; margin: 0; padding: 0; }
.goal-evidence-list li { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); padding: var(--space-sm) 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.goal-evidence-list span { display: grid; gap: var(--space-2xs); min-width: 0; }.goal-evidence-list strong { font-size: var(--type-secondary); font-weight: var(--weight-medium); }.goal-evidence-list small { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.goal-log-list, .goal-review-list ul { display: grid; gap: var(--space-xs); margin: var(--space-sm) 0 0; padding: 0; list-style: none; }
.goal-log-list li { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); padding: var(--space-sm) 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.goal-log-list li > div:first-child { display: grid; gap: var(--space-2xs); min-width: 0; }
.goal-log-list strong { color: var(--el-text-color-secondary); font-size: var(--type-metadata); font-weight: var(--weight-normal); }
.goal-log-list span { color: var(--el-text-color-primary); font-size: var(--type-secondary); font-weight: var(--weight-medium); }
.goal-log-list p { margin: 0; white-space: pre-wrap; color: var(--el-text-color-regular); font-size: var(--type-secondary); overflow-wrap: anywhere; }
.goal-log-actions { display: flex; flex: 0 0 auto; gap: var(--space-2xs); }
.goal-review-list { margin-top: var(--space-md); padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-lighter); }
.goal-review-list h3 { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-secondary); font-weight: var(--weight-medium); }
.goal-review-list li { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); font-size: var(--type-secondary); }
.goal-review-list a { color: var(--el-color-primary); text-decoration: none; }.goal-review-list a:hover { text-decoration: underline; }
.goal-review-list .goal-review-period { display: block; padding: var(--space-xs) 0; }
.goal-review-period-heading, .goal-review-period-actions { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); flex-wrap: wrap; }
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
