<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import ExpandableText from '../ExpandableText.vue'
import type { ExperimentRecordDto, ExperimentRecordHighlight, ExperimentRecordRequest, ExperimentTopicDto } from '../../types/experiment'

const props = defineProps<{
  modelValue: boolean
  record?: ExperimentRecordDto | null
  saving?: boolean
  direction?: string | null
  pinnedPractices?: string[]
  recentPractices?: string[]
  topicOptions?: ExperimentTopicDto[]
  topicsLoading?: boolean
  topicsError?: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  save: [value: ExperimentRecordRequest]
  retryTopics: []
}>()
const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const form = reactive<ExperimentRecordRequest>({
  title: '', content: '', practiceText: '', recordedOn: today(), understandingNode: false,
  methodCandidate: false, highlight: 'GENERAL', topicIds: [],
})
const writingHelpVisible = ref(false)
const contentField = ref<{ focus: () => void }>()
const extraOpen = ref(false)
const practicePickerVisible = ref(false)

watch(() => [props.modelValue, props.record] as const, ([visible, record]) => {
  if (!visible) return
  writingHelpVisible.value = false
  practicePickerVisible.value = false
  extraOpen.value = !record || Boolean(record && (record.understandingNode || record.methodCandidate || record.highlight !== 'GENERAL' || record.recordedOn !== today()))
  Object.assign(form, record
    ? { title: record.title ?? '', content: record.content ?? '', practiceText: record.practiceText ?? '', recordedOn: record.recordedOn, understandingNode: record.understandingNode, methodCandidate: record.methodCandidate, highlight: record.highlight, topicIds: record.topics?.map(topic => topic.id) ?? [], expectedVersion: record.version }
    : { title: '', content: '', practiceText: '', recordedOn: today(), understandingNode: false, methodCandidate: false, highlight: 'GENERAL', topicIds: [], expectedVersion: undefined })
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
const usePractice = (practice: string) => { form.practiceText = practice; practicePickerVisible.value = false }
</script>

<template>
  <AppDialog class="record-dialog" :model-value="modelValue" :title="record ? '編輯探索紀錄' : '新增探索紀錄'" :busy="saving" :draft="form" width="min(620px, calc(100vw - 32px))" scroll-body @opened="contentField?.focus()" @update:model-value="emit('update:modelValue', $event)">
    <div v-if="direction" class="direction-context">
      <span class="record-label"><strong>終點</strong>｜想達成的改變</span>
      <ExpandableText :content="direction" :lines="5" />
    </div>
    <el-form class="app-record-form record-form" label-position="top" :disabled="saving" @submit.prevent="submit">
      <el-form-item label="標題">
        <el-input v-model="form.title" maxlength="255" aria-label="紀錄標題" />
        <FieldCounter v-if="form.title" :value="form.title" :limit="255" />
      </el-form-item>
      <el-form-item label="內容" class="record-content-field">
        <el-input ref="contentField" v-model="form.content" type="textarea" :rows="5" maxlength="10000" aria-label="紀錄內容" placeholder="記下這次做了什麼、發現什麼，或讀到什麼值得再試。" />
        <div class="content-assistance"><FieldCounter v-if="form.content" :value="form.content" :limit="10000" /><button type="button" class="writing-help" :disabled="saving" :aria-expanded="writingHelpVisible" @click="writingHelpVisible = !writingHelpVisible">不知道怎麼寫？</button></div>
        <p v-if="writingHelpVisible" class="writing-examples">可從「我做了什麼」、「我發現什麼」或「下一步想試什麼」擇一開始。</p>
      </el-form-item>
      <el-form-item label="做法">
        <el-input v-model="form.practiceText" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" maxlength="10000" aria-label="紀錄做法" placeholder="這次怎麼做，或接下來想試什麼？" />
        <div class="practice-assistance">
          <FieldCounter v-if="form.practiceText" :value="form.practiceText" :limit="10000" />
          <el-popover v-if="pinnedPractices?.length || recentPractices?.length" v-model:visible="practicePickerVisible" placement="bottom-end" :width="280" trigger="click">
            <template #reference><el-button text type="primary" size="small" :disabled="saving">選用做法</el-button></template>
            <div class="practice-options">
              <section v-if="pinnedPractices?.length"><h4>常用做法</h4><button v-for="practice in pinnedPractices" :key="practice" type="button" :disabled="saving" @click="usePractice(practice)">{{ practice }}</button></section>
              <section v-if="recentPractices?.length"><h4>最近做法</h4><button v-for="practice in recentPractices" :key="practice" type="button" :disabled="saving" @click="usePractice(practice)">{{ practice }}</button></section>
            </div>
          </el-popover>
        </div>
      </el-form-item>
      <el-form-item v-if="topicOptions" label="主題">
        <el-select v-model="form.topicIds" multiple filterable clearable collapse-tags :max-collapse-tags="3" :loading="topicsLoading" :disabled="saving || topicsError" placeholder="選擇主題" aria-label="紀錄主題">
          <el-option v-for="topic in topicOptions" :key="topic.id" :label="topic.name" :value="topic.id" />
        </el-select>
        <p v-if="topicsError" class="field-status" role="alert">無法載入主題。<el-button link type="primary" size="small" @click="emit('retryTopics')">重試</el-button></p>
        <p v-else-if="!topicsLoading && !topicOptions?.length" class="field-status">尚未建立主題，可由左側主題目錄進入管理。</p>
      </el-form-item>
      <p v-if="!hasText" class="validation-hint">標題、內容或做法至少填一項。</p>
      <details class="record-supplementary editor-supplementary" :open="extraOpen" @toggle="extraOpen = ($event.target as HTMLDetailsElement).open">
        <summary>補充資訊<span v-if="!extraOpen" class="extra-summary">{{ form.recordedOn || '未設定日期' }}・{{ highlights.find(item => item.value === form.highlight)?.label }}{{ form.understandingNode ? '・理解節點' : '' }}{{ form.methodCandidate ? '・方法備選' : '' }}</span></summary>
        <div class="extra-fields">
          <el-form-item label="紀錄日期" required :error="!form.recordedOn ? '請選擇紀錄日期。' : undefined"><el-date-picker v-model="form.recordedOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
          <el-form-item label="標記"><div class="mark-options"><el-checkbox v-model="form.understandingNode">理解節點</el-checkbox><el-checkbox v-model="form.methodCandidate">方法備選</el-checkbox></div></el-form-item>
          <el-form-item label="本次亮點" class="highlight-field"><el-radio-group v-model="form.highlight"><el-radio v-for="item in highlights" :key="item.value" :value="item.value">{{ item.label }}</el-radio></el-radio-group></el-form-item>
        </div>
      </details>
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" :disabled="!canSave" @click="submit">儲存紀錄</el-button></template>
  </AppDialog>
</template>

<style scoped src="./experimentRecordDialog.css"></style>

<style scoped>
.direction-context { margin-bottom: var(--space-md); padding-bottom: var(--space-sm); border-bottom: 1px solid var(--el-border-color-lighter); }
.direction-context .record-label { display: block; color: var(--el-color-primary); }
.direction-context strong { font-weight: var(--weight-semibold); }
.direction-context :deep(.expandable-content) { color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.record-form :deep(.el-form-item__label) { color: var(--el-text-color-secondary); }
.record-form :deep(.el-form-item__content) { display: flex; flex-direction: column; align-items: stretch; min-width: 0; }
.record-form :deep(.el-input), .record-form :deep(.el-textarea), .record-form :deep(.el-date-editor) { width: 100%; min-width: 0; }
.record-form :deep(.el-textarea__inner) { font-size: var(--type-secondary); line-height: var(--leading-ui); }
.content-assistance, .practice-assistance { display: flex; align-items: baseline; justify-content: space-between; flex-wrap: wrap; gap: var(--space-xs); width: 100%; }
.content-assistance :deep(.app-field-counter), .practice-assistance :deep(.app-field-counter) { width: auto; }
.validation-hint, .writing-examples, .field-status { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.writing-examples { margin-top: var(--space-xs); }
.writing-help { padding: 0; border: 0; background: transparent; color: var(--el-color-primary); font: inherit; font-size: var(--type-meta); line-height: var(--leading-ui); cursor: pointer; }
.writing-help:disabled { opacity: .6; cursor: default; }
.practice-options { max-height: 300px; overflow-y: auto; }
.practice-options section + section { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-lighter); }
.practice-options h4 { margin: 0 0 var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: var(--weight-medium); }
.practice-options button { display: block; width: 100%; border: 0; border-radius: var(--radius-sm); padding: var(--space-xs); background: transparent; color: var(--el-text-color-regular); font: inherit; font-size: var(--type-secondary); line-height: var(--leading-ui); text-align: left; white-space: normal; overflow-wrap: anywhere; cursor: pointer; }
.practice-options button:hover, .practice-options button:focus-visible { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.practice-options button:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: -2px; }
.extra-summary { margin-left: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-meta); font-weight: var(--weight-regular); }
.extra-fields { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 0 var(--space-md); margin-top: var(--space-sm); }
.highlight-field { grid-column: 1 / -1; }
.mark-options { display: flex; flex-wrap: wrap; gap: 0 var(--space-sm); }
.mark-options :deep(.el-checkbox), .record-supplementary :deep(.el-radio) { margin-right: 0; }
.record-supplementary :deep(.el-form-item:last-child) { margin-bottom: 0; }
.record-supplementary :deep(.el-radio-group) { display: flex; flex-wrap: wrap; gap: 0 var(--space-md); }
.editor-supplementary { padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-summary); }
.editor-supplementary > summary { margin: calc(var(--space-sm) * -1); padding: var(--space-sm); }
.editor-supplementary[open] > summary { margin-bottom: 0; }
.editor-supplementary .extra-fields { margin-top: var(--space-sm); }
@media (max-width: 760px) { .extra-fields { grid-template-columns: minmax(0, 1fr); } .extra-summary { display: block; margin: var(--space-2xs) 0 0; } }
</style>
