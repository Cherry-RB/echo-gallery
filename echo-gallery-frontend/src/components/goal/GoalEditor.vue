<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import CoverImageField from '../ui/CoverImageField.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import GoalSourcePicker from './GoalSourcePicker.vue'
import GoalMetricSettings from './GoalMetricSettings.vue'
import { goalApi } from '../../utils/api/goalApi'
import { goalKinds, goalStatuses } from '../../types/goal'
import { goalDates } from '../../utils/goalPresentation'
import type { GoalDefinition, GoalOption, GoalSource, GoalSourceView, GoalView } from '../../types/goal'
const props = defineProps<{ modelValue: boolean; goal?: GoalView | null; initialSection?: 'goal' | 'sources' }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [GoalView] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const dialog = ref<InstanceType<typeof AppDialog>>(), picker = ref<InstanceType<typeof GoalSourcePicker>>()
const content = ref<HTMLElement>(), sourceButton = ref<HTMLElement>()
const form = ref<GoalDefinition>(empty()), metadata = ref<Record<string, GoalOption | GoalSourceView>>({})
const saving = ref(false), error = ref(''), reason = ref(''), sourceVisible = ref(false), metadataBusy = ref(false)
const editingId = ref<number | null>(null), baseVersion = ref(0), baseline = ref('')
const submitted = ref(false)
const wasClosed = ref(false)
const reasonRequired = computed(() => wasClosed.value && ['ACTIVE', 'PLANNED'].includes(form.value.status))
const includedSources = computed(() => form.value.sources.filter(source => !source.excluded))
let requestId = crypto.randomUUID(), generation = 0, scrollTop = 0
function empty(): GoalDefinition {
  return { name: '', ...goalDates(), expectation: '', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: true, pinOrder: 0, reviewDays: 7, remindersEnabled: true, allTags: false, sources: [], metrics: [] }
}
watch(() => props.modelValue, async value => {
  const token = ++generation
  if (!value) return
  form.value = props.goal ? JSON.parse(JSON.stringify(props.goal.definition)) : empty()
  editingId.value = props.goal?.id ?? null; baseVersion.value = props.goal?.version ?? 0
  wasClosed.value = props.goal?.closedAt != null; submitted.value = false
  reason.value = ''; error.value = ''; sourceVisible.value = false; metadata.value = {}; requestId = crypto.randomUUID()
  baseline.value = JSON.stringify({ form: form.value, reason: '' }); metadataBusy.value = Boolean(editingId.value)
  if (editingId.value) {
    try { const values = await goalApi.sources(editingId.value); if (token === generation) for (const source of values) metadata.value[`${source.kind}:${source.id}`] = source }
    catch { if (token === generation) error.value = '來源名稱讀取失敗。原設定仍保留，請重新開啟表單重試。' }
    finally { if (token === generation) metadataBusy.value = false }
  }
  if (token === generation && props.initialSection === 'sources' && !error.value) await openSources()
})
const sourceSummary = computed(() => Object.entries(goalKinds).map(([kind, label]) => {
  const count = form.value.sources.filter(s => s.kind === kind && !s.excluded).length
  return count ? `${label} ${count} 項` : ''
}).filter(Boolean).join('、'))
const sourceSummaryText = computed(() => includedSources.value.length
  ? `已選 ${includedSources.value.length} 項：${sourceSummary.value}`
  : '尚未設定追蹤內容，也可以只記下目標。')
async function openSources() {
  scrollTop = content.value?.closest('.el-dialog__body')?.scrollTop ?? 0
  sourceVisible.value = true; await nextTick()
  const body = content.value?.closest('.el-dialog__body'); if (body) body.scrollTop = 0
  content.value?.focus()
}
async function returnToForm() {
  sourceVisible.value = false; await nextTick()
  const body = content.value?.closest('.el-dialog__body'); if (body) body.scrollTop = scrollTop
  sourceButton.value?.focus()
}
function confirmSources(value: { sources: GoalSource[]; allTags: boolean; metadata: Record<string, GoalOption | GoalSourceView> }) {
  form.value.sources = value.sources; form.value.allTags = value.allTags; metadata.value = value.metadata
  void returnToForm()
}
function beforeClose(done: () => void) {
  if (saving.value) return
  if (sourceVisible.value) void returnToForm()
  else done()
}
onBeforeRouteLeave(async () => {
  if (!visible.value) return true
  if (saving.value) return false
  if (sourceVisible.value) {
    if (picker.value?.hasChanges || JSON.stringify({ form: form.value, reason: reason.value }) !== baseline.value) {
      try { await ElMessageBox.confirm('尚有未儲存的目標或來源，離開將放棄本次草稿。', '放棄變更', { type: 'warning' }) } catch { return false }
    }
    visible.value = false; return true
  }
  return dialog.value?.confirmNavigation() ?? false
})
async function save() {
  if (saving.value) return
  submitted.value = true
  if (!form.value.name.trim() || !form.value.startsOn || !form.value.endsOn || form.value.endsOn < form.value.startsOn || (reasonRequired.value && !reason.value.trim())) return
  saving.value = true; error.value = ''
  try {
    const result = await goalApi.save(editingId.value, form.value, baseVersion.value, reason.value, requestId)
    emit('saved', result); visible.value = false; ElMessage.success('目標已儲存')
  } catch { error.value = '儲存失敗，草稿仍保留。若其他裝置已修改此目標，請保留需要的內容再重新開啟。' }
  finally { saving.value = false }
}
</script>
<template>
  <AppDialog ref="dialog" v-model="visible" :title="sourceVisible ? '選擇相關資料' : goal ? '編輯目標' : '建立目標'" :width="sourceVisible ? 'min(900px, calc(100vw - 32px))' : 'min(680px, calc(100vw - 32px))'" scroll-body :busy="saving" :before-close="beforeClose" :draft="sourceVisible ? undefined : { form, reason }" :draft-baseline="baseline">
    <div ref="content" class="goal-editor-content" tabindex="-1">
      <GoalSourcePicker v-if="sourceVisible" ref="picker" :sources="form.sources" :all-tags="form.allTags" :metadata="metadata" @confirm="confirmSources" @cancel="returnToForm" />
      <el-form v-else label-position="top" :disabled="saving" @submit.prevent="save">
        <el-alert v-if="error" :title="error" type="error" :closable="false" />
        <el-form-item label="目標名稱" required :error="submitted && !form.name.trim() ? '請填寫目標名稱' : ''"><el-input v-model="form.name" maxlength="255" placeholder="例如：完成三次人物衝突練習" /><FieldCounter :value="form.name" :limit="255" /></el-form-item>
        <el-form-item label="期望結果（選填）"><el-input v-model="form.expectation" type="textarea" :rows="3" maxlength="5000" placeholder="希望完成什麼，或發生什麼改變？" /><FieldCounter :value="form.expectation" :limit="5000" /></el-form-item>
        <div class="goal-date-fields"><el-form-item label="開始日期" required :error="submitted && !form.startsOn ? '請選擇開始日期' : ''"><el-date-picker v-model="form.startsOn" type="date" value-format="YYYY-MM-DD" /></el-form-item><el-form-item label="結束日期" required :error="!form.endsOn && submitted ? '請選擇結束日期' : form.endsOn && form.startsOn && form.endsOn < form.startsOn ? '結束日期不可早於開始日期' : ''"><el-date-picker v-model="form.endsOn" type="date" value-format="YYYY-MM-DD" /></el-form-item></div>
        <div class="goal-status-fields"><el-form-item label="目標狀態"><el-select v-model="form.status"><el-option v-for="(label, value) in goalStatuses" :key="value" :value="value" :label="label" /></el-select></el-form-item><el-checkbox v-model="form.pinned">固定於總覽</el-checkbox></div>
        <section class="goal-section source-settings" aria-label="追蹤內容">
          <div class="goal-section-heading"><h3>追蹤內容（選填）</h3><button ref="sourceButton" type="button" class="goal-source-button" :disabled="saving || metadataBusy" @click="openSources">{{ metadataBusy ? '載入中…' : includedSources.length ? '管理' : '選擇資料' }}</button></div>
          <p class="goal-help">{{ sourceSummaryText }}</p>
          <p v-if="form.sources.some(s => s.excluded)" class="goal-help">另有 {{ form.sources.filter(s => s.excluded).length }} 項排除範圍，可在管理中調整。</p>
        </section>
        <GoalMetricSettings class="goal-section" v-model="form.metrics" :sources="form.sources" />
        <details class="goal-section background-settings">
          <summary><span>背景與封面（選填）<small v-if="form.motivation || form.imageUrl">已填寫</small></span><span class="display-toggle" aria-hidden="true"><span class="when-closed">▸ 展開</span><span class="when-open">▾ 收合</span></span></summary>
          <div class="goal-section-content"><el-form-item label="為什麼想做"><el-input v-model="form.motivation" type="textarea" :rows="3" maxlength="5000" /><FieldCounter :value="form.motivation" :limit="5000" /></el-form-item><el-form-item label="封面圖片網址"><CoverImageField v-model="form.imageUrl" /></el-form-item></div>
        </details>
        <el-form-item v-if="goal" class="goal-section" :label="reasonRequired ? '這次調整的原因' : '這次調整的原因（選填）'" :required="reasonRequired" :error="submitted && reasonRequired && !reason.trim() ? '重新開始已完成或已結束的目標，請填寫原因' : ''"><el-input v-model="reason" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" maxlength="2000" placeholder="例如：調整期間，繼續下一階段" /><FieldCounter :value="reason" :limit="2000" /></el-form-item>
      </el-form>
    </div>
    <template #footer="{ requestClose }"><div v-if="sourceVisible" class="source-footer"><p>儲存目標後才會生效。</p><div><el-button @click="returnToForm">放棄並返回</el-button><el-button type="primary" @click="picker?.confirm()">套用並返回</el-button></div></div><template v-else><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ goal ? '儲存變更' : '建立目標' }}</el-button></template></template>
  </AppDialog>
</template>
<style scoped>
.goal-editor-content { min-width: 0; }
.goal-editor-content:focus { outline: none; }
.goal-editor-content :deep(.el-form-item) { margin-bottom: var(--space-md); }
.goal-date-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }
.goal-date-fields :deep(.el-date-editor) { width: 100%; }
.goal-status-fields { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; gap: var(--space-lg); }
.goal-status-fields :deep(.el-form-item) { margin-bottom: 0; }
.goal-status-fields > .el-checkbox { align-self: end; margin: 0; }
.goal-section { margin-top: var(--space-lg); padding-top: var(--space-md); border-top: 1px solid var(--el-border-color-lighter); }
.goal-section-content { margin-top: var(--space-md); }
.goal-section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); }
h3 { font-size: var(--type-secondary); font-weight: var(--weight-medium); line-height: var(--leading-ui); }
.goal-source-button { padding: var(--space-2xs) 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-secondary); }
.goal-source-button:disabled { cursor: wait; color: var(--el-text-color-secondary); }
.goal-source-button:focus-visible { outline: 2px solid var(--el-color-primary); }
.goal-help { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-ui); margin-top: var(--space-xs); }
.background-settings { margin-bottom: var(--space-md); }
.background-settings .goal-section-content { margin-top: var(--space-md); }
.background-settings .goal-section-content :deep(.el-form-item:last-child) { margin-bottom: 0; }
.background-settings summary { display: flex; justify-content: space-between; align-items: start; gap: var(--space-sm); padding: 0; list-style: none; font-size: var(--type-secondary); line-height: var(--leading-ui); cursor: pointer; }
.background-settings summary::-webkit-details-marker { display: none; }
.background-settings summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.background-settings small { color: var(--el-text-color-secondary); font-size: var(--type-metadata); margin-left: var(--space-xs); }
.display-toggle { flex-shrink: 0; color: var(--el-color-primary); }
.when-open, .background-settings[open] .when-closed { display: none; }
.background-settings[open] .when-open { display: inline; }
.source-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: var(--space-sm); text-align: left; }
.source-footer p { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }
.source-footer > div { display: flex; margin-left: auto; }
.el-alert { margin-bottom: var(--space-md); }
@media (max-width: 760px) { .goal-date-fields { grid-template-columns: 1fr; gap: 0; } .goal-status-fields { grid-template-columns: 1fr; gap: var(--space-xs); } }
</style>
