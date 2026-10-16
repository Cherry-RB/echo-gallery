<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessageBox } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import ExpandableText from '../ExpandableText.vue'
import TreasureChallengeRoute from './TreasureChallengeRoute.vue'
import TreasureChallengeExamples from './TreasureChallengeExamples.vue'
import TreasureRankBadge from './TreasureRankBadge.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import { treasureApi } from '../../utils/api/treasureApi'
import { ranks, rankGuides, challengeRanks, nextRankOf, errorMessage } from '../../utils/treasure'
import type { TreasureRank, TreasureTrial, AttemptRequest } from '../../types/treasure'

type Mode = 'route' | 'help' | 'edit' | 'attempt'
const props = defineProps<{ modelValue: boolean; treasureId: number; currentRank?: TreasureRank; nextRank: TreasureRank | null; archived?: boolean; initialRank?: TreasureRank; initialMode?: Mode; trial?: TreasureTrial; editDefinition?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()
const client = useQueryClient()
const rank = computed<TreasureRank>(() => props.currentRank ?? (props.nextRank === 'BRONZE' ? 'STONE' : props.nextRank === 'SILVER' ? 'BRONZE' : props.nextRank === 'GOLD' ? 'SILVER' : 'GOLD'))
const selectedRank = ref<TreasureRank>(props.initialRank ?? props.trial?.targetRank ?? props.nextRank ?? 'GOLD')
const mode = ref<Mode>('route')
const examplesVisible = ref(false)
const exampleIndex = ref(0)
const saving = ref(false)
const error = ref('')
const content = ref<HTMLElement>()
const initialized = ref(false)
const formFromRoute = ref(false)
const originalResult = ref('')
const definition = reactive({ title: '', criteria: '' })
const originalDefinition = ref('')
const result = reactive<Omit<AttemptRequest, 'status'> & { status: AttemptRequest['status'] | '' }>({ status: '', resultNote: '', evidenceUrl: '', attemptedAt: new Date().toISOString() })
const query = useQuery({ queryKey: computed(() => ['treasures', props.treasureId, 'route']), queryFn: () => treasureApi.route(props.treasureId), enabled: computed(() => props.modelValue) })
const trial = computed(() => query.data.value?.find(t => t.targetRank === selectedRank.value))
const canAttempt = computed(() => !!trial.value && trial.value.status !== 'PASSED' && selectedRank.value === nextRankOf(rank.value) && !props.archived)
const canEdit = computed(() => !props.archived && Object.keys(ranks).indexOf(selectedRank.value) > Object.keys(ranks).indexOf(rank.value) && trial.value?.status !== 'PASSED')
const switchMode = async (value: Mode) => {
  mode.value = value; error.value = ''
  await nextTick()
  content.value?.focus({ preventScroll: true })
  const body = content.value?.closest<HTMLElement>('.el-dialog__body')
  if (body) body.scrollTop = 0
}
const edit = (target: TreasureRank) => {
  selectedRank.value = target
  const existing = query.data.value?.find(t => t.targetRank === target)
  definition.title = existing?.title ?? ''; definition.criteria = existing?.criteria ?? ''
  originalDefinition.value = JSON.stringify(definition)
  examplesVisible.value = false; exampleIndex.value = 0
  formFromRoute.value = mode.value === 'route'
  switchMode('edit')
}
const attempt = (value: TreasureTrial) => {
  selectedRank.value = value.targetRank!
  Object.assign(result, { status: '', resultNote: '', evidenceUrl: '', attemptedAt: new Date().toISOString() })
  formFromRoute.value = mode.value === 'route'
  originalResult.value = JSON.stringify(result)
  switchMode('attempt')
}
const help = () => switchMode('help')
const leave = async (close = false, done?: () => void) => {
  if (saving.value) return
  const editing = mode.value === 'edit'
  const dirty = editing && JSON.stringify(definition) !== originalDefinition.value || mode.value === 'attempt' && JSON.stringify(result) !== originalResult.value
  if (dirty) {
    try { await ElMessageBox.confirm('尚有未儲存內容，確定離開？', '保留目前編輯', { confirmButtonText: '捨棄並離開', cancelButtonText: '繼續編輯' }) } catch { return }
  }
  if (close) { if (done) done(); else emit('update:modelValue', false) }
  else await switchMode('route')
}
watch(() => props.modelValue, () => { initialized.value = false }, { immediate: true })
watch([() => props.modelValue, () => query.data.value], ([visible, data]) => {
  if (!visible || !data || initialized.value) return
  initialized.value = true
  selectedRank.value = props.initialRank ?? props.trial?.targetRank ?? props.nextRank ?? 'GOLD'
  mode.value = 'help'
  const start = props.initialMode ?? (props.editDefinition ? 'edit' : props.trial ? 'attempt' : 'route')
  if (start === 'help') help()
  else if (start === 'edit' && canEdit.value) edit(selectedRank.value)
  else if (start === 'attempt' && canAttempt.value) attempt(trial.value!)
  else switchMode('route')
}, { immediate: true })
const adoptExample = async () => {
  const example = rankGuides[selectedRank.value].examples[exampleIndex.value]
  if (!example || !canEdit.value) return
  if (definition.title.trim() || definition.criteria.trim()) {
    try { await ElMessageBox.confirm('採用範例會替換編輯中的名稱與條件，既有嘗試歷程不會改寫。', '採用參考範例', { confirmButtonText: '採用', cancelButtonText: '保留內容' }) } catch { return }
  }
  Object.assign(definition, example)
  examplesVisible.value = false
}
const refresh = async () => { await Promise.all([client.invalidateQueries({ queryKey: ['treasures'] }), client.invalidateQueries({ queryKey: ['overview'] })]); emit('saved') }
const save = async () => {
  if (saving.value) return
  error.value = ''
  saving.value = true
  try {
    if (mode.value === 'edit') {
      if (!canEdit.value) return
      if (!definition.title.trim() || !definition.criteria.trim()) { error.value = '請填寫檢定名稱與通過條件。'; return }
      const data = { ...definition, targetRank: selectedRank.value }
      if (trial.value) await treasureApi.updateTrial(props.treasureId, trial.value.id, data)
      else await treasureApi.createTrial(props.treasureId, data)
    } else if (mode.value === 'attempt') {
      if (!canAttempt.value) return
      if (!result.status || !result.resultNote.trim() || !result.attemptedAt) { error.value = '請填寫這次經過、結果判定與嘗試時間。'; return }
      if (result.status === 'PASSED') {
        try { await ElMessageBox.confirm('確認實際結果符合原條件？通過後晉級，這份檢定與嘗試紀錄將保留。', '確認通過', { confirmButtonText: '確認通過', cancelButtonText: '返回檢查' }) } catch { return }
      }
      await treasureApi.recordAttempt(props.treasureId, trial.value!.id, { ...result, status: result.status, attemptedAt: new Date(result.attemptedAt).toISOString() })
    } else return
    await refresh()
    emit('update:modelValue', false)
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
const title = computed(() => mode.value === 'help' ? '成果的四階成長' : mode.value === 'edit' ? (trial.value ? '編輯' : '設定') + ranks[selectedRank.value] + '檢定' : mode.value === 'attempt' ? '記錄一次嘗試' : '成長挑戰')
</script>

<template>
  <AppDialog :model-value="modelValue" :title="title" width="min(680px, calc(100vw - 32px))" scroll-body :busy="saving" :before-close="done => leave(true, done)" :close-on-click-modal="mode === 'help' || mode === 'route'" @update:model-value="emit('update:modelValue', $event)">
    <div ref="content" class="challenge-content" tabindex="-1">
      <p v-if="query.isLoading.value">正在讀取檢定…</p>
      <el-button v-else-if="query.isError.value" @click="query.refetch()">讀取失敗，重試</el-button>
      <template v-else-if="mode === 'help'">
        <p class="help-intro">等級記錄成果經歷的現實考驗。四階提供參考方向，具體檢定由你設定。</p>
        <section v-for="stage in (['STONE', ...challengeRanks] as TreasureRank[])" :key="stage" class="growth-guide" :data-rank="stage">
          <div class="guide-heading"><TreasureRankBadge :rank="stage" /><h3>{{ rankGuides[stage].stage }}</h3><span v-if="rank === stage" class="current-label">目前等級</span></div>
          <p>{{ rankGuides[stage].summary }}</p>
        </section>
        <div class="growth-footnote"><p>銅、銀、金各可設定一份檢定，也可以先規劃未來階級。</p><p>每份檢定可多次嘗試；未通過不降級，也可以保持原級珍藏。</p></div>
      </template>
      <TreasureChallengeRoute v-else-if="mode === 'route'" v-model="selectedRank" :treasure-id="treasureId" :rank="rank" :trials="query.data.value ?? []" :archived="archived" @edit="edit" @attempt="attempt" @help="help" />
      <el-form v-else :class="{ 'app-record-form': mode === 'attempt' }" label-position="top" @submit.prevent="save">
        <template v-if="mode === 'edit'">
          <div class="edit-context"><TreasureRankBadge :rank="selectedRank" /><p class="form-help">設定一個可實際完成、能確認結果的挑戰。</p></div>
          <p v-if="selectedRank !== nextRankOf(rank)" class="form-help">可以先保存規劃；通過{{ ranks[selectedRank === 'GOLD' ? 'SILVER' : 'BRONZE'] }}後，才能記錄{{ ranks[selectedRank] }}嘗試。</p>
          <button type="button" class="example-toggle" :aria-expanded="examplesVisible" @click="examplesVisible = !examplesVisible">{{ examplesVisible ? '收合範例' : '從範例開始' }}</button>
          <section v-if="examplesVisible" class="inline-examples" :data-rank="selectedRank">
            <TreasureChallengeExamples v-model="exampleIndex" :rank="selectedRank" hide-badge />
            <el-button :disabled="saving" @click="adoptExample">帶入名稱與條件</el-button>
          </section>
          <el-form-item label="這一階想完成什麼？" required><el-input v-model="definition.title" maxlength="255" placeholder="例如：完成一次正式投稿" /></el-form-item>
          <el-form-item label="做到哪些事，就算通過？" required><el-input v-model="definition.criteria" type="textarea" :rows="4" maxlength="50000" placeholder="例如：依投稿規則提交作品，保留提交日期與確認信" /><FieldCounter :value="definition.criteria" :limit="50000" /></el-form-item>
          <p class="form-help">儲存後才會更新檢定；過去嘗試的條件與結果會保留。</p>
        </template>
        <template v-else-if="trial">
          <section class="trial-context" :data-rank="selectedRank" aria-label="這次對照的檢定">
            <header class="trial-context-heading"><TreasureRankBadge :rank="selectedRank" /><h3>{{ trial.title }}</h3></header><span class="context-label">通過條件</span><ExpandableText :content="trial.criteria" :lines="4" />
          </section>
          <el-form-item label="這次做了什麼？" required><el-input v-model="result.resultNote" type="textarea" :rows="4" maxlength="50000" placeholder="記下行動、實際經過，以及哪些條件已達成。" /><FieldCounter :value="result.resultNote" :limit="50000" /></el-form-item>
          <el-form-item label="是否達到通過條件？" required><el-select v-model="result.status" placeholder="請選擇這次的結果"><el-option label="尚待確認" value="ATTEMPTED" /><el-option label="尚未達成" value="NOT_PASSED" /><el-option label="已達成" value="PASSED" /></el-select></el-form-item>
          <el-form-item label="嘗試時間" required><el-date-picker v-model="result.attemptedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" /></el-form-item>
          <el-form-item label="佐證連結（選填）"><el-input v-model="result.evidenceUrl" placeholder="https://" maxlength="2048" /></el-form-item>
          <p class="form-help">新增一筆獨立紀錄，保留當時的條件與結果，不會覆蓋前次嘗試。</p>
        </template>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </div>
    <template #footer>
      <template v-if="mode === 'help'"><el-button @click="emit('update:modelValue', false)">關閉</el-button></template>
      <template v-else-if="mode === 'edit' || mode === 'attempt'"><el-button :disabled="saving" @click="leave(!formFromRoute)">{{ formFromRoute ? '返回檢定' : '取消' }}</el-button><el-button type="primary" :loading="saving" @click="save">{{ mode === 'edit' ? '儲存檢定' : '儲存嘗試紀錄' }}</el-button></template>
      <el-button v-else @click="emit('update:modelValue', false)">關閉</el-button>
    </template>
  </AppDialog>
</template>
<style scoped>
.challenge-content { min-width: 0; overflow-wrap: anywhere; }
.challenge-content:focus { outline: none; }
.help-intro, .form-help { color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.growth-guide { padding: var(--space-md) 0; border-bottom: 1px solid var(--el-border-color-light); }
.guide-heading { display: flex; flex-wrap: wrap; gap: var(--space-sm); align-items: center; }
.guide-heading h3 { margin: 0; font-size: var(--type-card-title); color: var(--rank-text); }
.growth-guide p { margin: var(--space-sm) 0 0; line-height: var(--leading-body); }
.current-label { color: var(--el-color-primary); font-size: var(--type-meta); }
.example-toggle { padding: 0; margin-bottom: var(--space-md); border: 0; background: transparent; color: var(--el-color-primary); font: inherit; cursor: pointer; }
.example-toggle:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.inline-examples { margin-bottom: var(--space-lg); padding: var(--space-md); background: var(--rank-surface); border-radius: var(--radius-md); }
.inline-examples > .el-button { margin-top: var(--space-sm); }
.trial-context { margin-bottom: var(--space-lg); padding: var(--space-md); background: var(--surface-summary); border: 1px solid var(--rank-border); border-left: var(--space-2xs) solid var(--rank-accent); border-radius: var(--radius-md); }
.trial-context-heading, .edit-context { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); margin-bottom: var(--space-md); }
.trial-context h3 { min-width: 0; margin: 0; font-size: var(--type-card-title); }
.context-label { display: block; margin-bottom: var(--space-xs); color: var(--el-color-primary); font-size: var(--type-ui); }
.edit-context p { margin: 0; }
.growth-footnote { display: grid; gap: var(--space-xs); margin: var(--space-lg) 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.growth-footnote p { margin: 0; }
.challenge-content :deep(.el-select), .challenge-content :deep(.el-date-editor) { width: 100%; }
</style>
