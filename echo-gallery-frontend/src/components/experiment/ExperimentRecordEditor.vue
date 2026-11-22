<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import ExpandableText from '../ExpandableText.vue'
import type { ExperimentRecordDto, ExperimentRecordHighlight, ExperimentRecordRequest } from '../../types/experiment'

const props = defineProps<{ modelValue: boolean; record?: ExperimentRecordDto | null; saving?: boolean; direction?: string | null; pinnedPractices?: string[]; recentPractices?: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; save: [value: ExperimentRecordRequest] }>()
const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const form = reactive<ExperimentRecordRequest>({
  title: '', content: '', practiceText: '', recordedOn: today(), understandingNode: false,
  methodCandidate: false, highlight: 'GENERAL',
})
const writingHelpVisible = ref(false)
const contentField = ref<{ focus: () => void }>()
const extraOpen = ref(false)

watch(() => [props.modelValue, props.record] as const, ([visible, record]) => {
  if (!visible) return
  writingHelpVisible.value = false
  extraOpen.value = Boolean(record && (record.understandingNode || record.methodCandidate || record.highlight !== 'GENERAL' || record.recordedOn !== today()))
  Object.assign(form, record
    ? { title: record.title ?? '', content: record.content ?? '', practiceText: record.practiceText ?? '', recordedOn: record.recordedOn, understandingNode: record.understandingNode, methodCandidate: record.methodCandidate, highlight: record.highlight, expectedVersion: record.version }
    : { title: '', content: '', practiceText: '', recordedOn: today(), understandingNode: false, methodCandidate: false, highlight: 'GENERAL', expectedVersion: undefined })
}, { immediate: true })

const hasText = computed(() => [form.title, form.content, form.practiceText].some(value => Boolean(value?.trim())))
const canSave = computed(() => hasText.value && Boolean(form.recordedOn) && !props.saving)
const highlights: Array<{ value: ExperimentRecordHighlight; label: string }> = [
  { value: 'GENERAL', label: '一般' }, { value: 'GAIN', label: '有所收穫' },
  { value: 'CHALLENGE_OVERCOME', label: '克服困難' }, { value: 'PEAK_EXPERIENCE', label: '高峰體驗' },
]
const submit = () => {
  if (!canSave.value) return
  emit('save', { ...form, title: form.title?.trim() || null, content: form.content?.trim() || null, practiceText: form.practiceText?.trim() || null })
}
const usePractice = (practice: string) => { form.practiceText = practice }
</script>

<template>
  <AppDialog class="record-dialog" :model-value="modelValue" :title="record ? '編輯探索紀錄' : '新增探索紀錄'" :busy="saving" :draft="form" width="min(620px, calc(100vw - 32px))" scroll-body @opened="contentField?.focus()" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="direction" class="direction-context">
      <span class="record-label"><strong>終點</strong>｜想達成的改變</span>
      <ExpandableText :content="direction" :lines="5" />
    </div>
    <el-form class="app-record-form record-form" label-position="top" :disabled="saving" @submit.prevent="submit">
      <el-form-item label="標題">
        <el-input v-model="form.title" maxlength="255" />
        <FieldCounter :value="form.title" :limit="255" />
      </el-form-item>
      <el-form-item label="內容">
        <el-input ref="contentField" v-model="form.content" type="textarea" :rows="4" maxlength="10000" placeholder="記下這次做了什麼、發現什麼，或讀到什麼值得再試。" />
        <FieldCounter :value="form.content" :limit="10000" />
        <button type="button" class="writing-help" :disabled="saving" :aria-expanded="writingHelpVisible" @click="writingHelpVisible = !writingHelpVisible">不知道怎麼寫？</button>
        <p v-if="writingHelpVisible" class="writing-examples">可從「我做了什麼」、「我發現什麼」或「下一步想試什麼」擇一開始。</p>
      </el-form-item>
      <el-form-item label="做法">
        <el-input v-model="form.practiceText" type="textarea" :autosize="{ minRows: 1, maxRows: 3 }" maxlength="10000" placeholder="這次怎麼做，或接下來想試什麼？" />
        <FieldCounter :value="form.practiceText" :limit="10000" />
        <div v-if="pinnedPractices?.length" class="pinned-practice-options" aria-label="常用做法">
          <span>常用做法</span>
          <el-button v-for="practice in pinnedPractices" :key="practice" text size="small" :disabled="saving" @click="usePractice(practice)">{{ practice }}</el-button>
        </div>
        <div v-if="recentPractices?.length" class="pinned-practice-options" aria-label="最近做法">
          <span>最近做法</span>
          <el-button v-for="practice in recentPractices" :key="practice" text size="small" :disabled="saving" @click="usePractice(practice)">{{ practice }}</el-button>
        </div>
      </el-form-item>
      <p v-if="!hasText" class="validation-hint">標題、內容或做法至少填一項。</p>
      <details class="record-supplementary" :open="extraOpen" @toggle="extraOpen = ($event.target as HTMLDetailsElement).open">
        <summary>補充資訊<span class="extra-summary">{{ form.recordedOn || '未設定日期' }}・{{ highlights.find(item => item.value === form.highlight)?.label }}{{ form.understandingNode ? '・理解節點' : '' }}{{ form.methodCandidate ? '・方法備選' : '' }}</span></summary>
        <div class="extra-grid">
          <el-form-item label="紀錄日期" required :error="!form.recordedOn ? '請選擇紀錄日期。' : undefined"><el-date-picker v-model="form.recordedOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="標記"><div class="mark-options"><el-checkbox v-model="form.understandingNode">理解節點</el-checkbox><el-checkbox v-model="form.methodCandidate">方法備選</el-checkbox></div></el-form-item>
        </div>
        <el-form-item label="本次亮點"><el-radio-group v-model="form.highlight"><el-radio v-for="item in highlights" :key="item.value" :value="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
      </details>
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="!canSave" @click="submit">儲存紀錄</el-button></template>
  </AppDialog>
</template>

<style scoped src="./experimentRecordDialog.css"></style>

<style scoped>
.direction-context { margin-bottom: var(--space-lg); padding: var(--space-xs) var(--space-sm); border-radius: var(--radius-sm); background: var(--surface-summary); }
.direction-context .record-label { display: block; margin-bottom: var(--space-2xs); color: var(--el-text-color-regular); }
.direction-context strong { color: var(--el-color-primary); font-weight: var(--weight-semibold); }
.direction-context :deep(.expandable-content) { color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.record-form :deep(.el-form-item__content) { display: flex; flex-direction: column; align-items: stretch; }
.validation-hint { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.writing-help { align-self: flex-start; margin-top: var(--space-xs); padding: 0; border: 0; background: transparent; color: var(--el-color-primary); font: inherit; font-size: var(--type-meta); line-height: var(--leading-ui); cursor: pointer; }
.writing-help:disabled { opacity: .6; cursor: default; }
.writing-examples { margin: var(--space-xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.pinned-practice-options { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2xs) var(--space-xs); margin-top: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.pinned-practice-options :deep(.el-button) { height: auto; padding: 0; font-size: var(--type-meta); }
.extra-summary { margin-left: var(--space-xs); color: var(--el-text-color-secondary); }
.extra-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); margin-top: var(--space-md); }
.extra-grid :deep(.el-date-editor) { width: 100%; }
.mark-options { display: flex; flex-wrap: wrap; gap: 0 var(--space-sm); }
.mark-options :deep(.el-checkbox) { margin-right: 0; }
.record-supplementary :deep(.el-form-item:last-child) { margin-bottom: 0; }
.record-supplementary :deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: 0 var(--space-md); }
.record-supplementary :deep(.el-radio) { margin-right: 0; }
@media (max-width: 480px) { .extra-grid { grid-template-columns: 1fr; gap: var(--space-md); } }
</style>
