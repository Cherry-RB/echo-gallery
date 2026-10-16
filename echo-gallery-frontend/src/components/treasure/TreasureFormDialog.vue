<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import CoverImageField from '../ui/CoverImageField.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import TreasureSourceSummary from './TreasureSourceSummary.vue'
import { imageUrlError } from '../../utils/imageUrl'
import TreasureSourcePicker from './TreasureSourcePicker.vue'
import type { TreasureDetail, TreasureReference } from '../../types/treasure'
import { treasureApi } from '../../utils/api/treasureApi'
import { contentOf, errorMessage, today } from '../../utils/treasure'

const props = defineProps<{ modelValue: boolean; treasure?: TreasureDetail }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [TreasureDetail] }>()
const form = reactive(contentOf())
const saving = ref(false)
const error = ref('')
const queryClient = useQueryClient()
const sourceVisible = ref(false)
const content = ref<HTMLElement>()
let formScrollTop = 0
watch(sourceVisible, async visible => {
  const body = content.value?.closest<HTMLElement>('.el-dialog__body')
  if (visible) formScrollTop = body?.scrollTop ?? 0
  await nextTick()
  if (body) body.scrollTop = visible ? 0 : formScrollTop
  if (visible) content.value?.focus({ preventScroll: true })
  else content.value?.querySelector<HTMLElement>('[data-source-manager]')?.focus({ preventScroll: true })
})
const sourcePicker = ref<InstanceType<typeof TreasureSourcePicker>>()
const close = (value: boolean) => {
  if (saving.value) return
  if (!value && sourceVisible.value) sourceVisible.value = false
  else emit('update:modelValue', value)
}
const beforeClose = (done: () => void) => {
  if (saving.value) return
  if (sourceVisible.value) sourceVisible.value = false
  else done()
}
const selectedSources = ref<TreasureReference[]>([])
const cardSource = ref<TreasureReference | null>(null)
const experimentSource = ref<TreasureReference | null>(null)
const hasSources = computed(() => !!cardSource.value || !!experimentSource.value || selectedSources.value.length > 0)

watch(() => props.modelValue, visible => {
  if (visible) {
    Object.assign(form, contentOf(props.treasure))
    selectedSources.value = [...(props.treasure?.sourceTreasures ?? [])]
    cardSource.value = props.treasure?.sourceCard ?? null
    experimentSource.value = props.treasure?.sourceExperiment ?? null
    sourceVisible.value = false; error.value = ''
  }
}, { immediate: true })
const disabledDate = (date: Date) => date > new Date(`${today()}T23:59:59+08:00`)
const save = async () => {
  if (saving.value) return
  if (!form.title.trim() || !form.acquiredOn) { error.value = '請填寫成果名稱與完成日期'; return }
  if (imageUrlError(form.coverImageUrl)) { error.value = imageUrlError(form.coverImageUrl); return }
  saving.value = true; error.value = ''
  try {
    const data = { ...form, title: form.title.trim(), sourceCardId: cardSource.value?.id ?? null, sourceExperimentId: experimentSource.value?.id ?? null, sourceTreasureIds: selectedSources.value.map(s => s.id) }
    const result = props.treasure ? await treasureApi.update(props.treasure.id, data) : await treasureApi.create(data)
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['treasures'] }), queryClient.invalidateQueries({ queryKey: ['overview'] }), queryClient.invalidateQueries({ queryKey: ['treasure-source-picker'] })])
    ElMessage.success(props.treasure ? '奇珍變更已儲存。' : '一件新的奇珍已收入館藏。它的故事，從此有了值得回望的地方。')
    emit('saved', result); emit('update:modelValue', false)
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
</script>

<template>
  <AppDialog :busy="saving" :draft="sourceVisible ? undefined : [form, cardSource?.id, experimentSource?.id, selectedSources.map(s => s.id)]" :model-value="modelValue" :title="sourceVisible ? '管理成果來源' : treasure ? '編輯奇珍' : '收入奇珍'" :width="sourceVisible ? 'min(980px, calc(100vw - 32px))' : 'min(760px, calc(100vw - 32px))'" scroll-body :before-close="beforeClose" :close-on-click-modal="!saving" :close-on-press-escape="!saving" @update:model-value="close">
    <div ref="content" class="form-content" tabindex="-1">
    <el-form v-show="!sourceVisible" label-position="top" @submit.prevent="save">
      <el-form-item label="成果名稱" required><el-input v-model="form.title" maxlength="255" /></el-form-item>
      <el-form-item label="成果說明（選填）"><el-input v-model="form.description" type="textarea" :rows="6" maxlength="50000" /><FieldCounter :value="form.description" :limit="50000" /></el-form-item>
      <el-form-item label="完成日期" required><el-date-picker v-model="form.acquiredOn" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledDate" /></el-form-item>
      <details class="display-settings">
        <summary><span><strong>作品與封面（選填）</strong><small>補充作品網址或封面，方便之後查看。</small></span><span class="display-toggle" aria-hidden="true"><span class="when-closed">展開</span><span class="when-open">收合</span></span></summary>
        <div class="display-fields">
        <el-form-item label="作品網址"><el-input v-model="form.artifactUrl" maxlength="2048" placeholder="https://" /></el-form-item>
        <el-form-item label="封面圖片來源連結"><CoverImageField v-model="form.coverImageUrl" :disabled="saving" /></el-form-item>
        </div>
      </details>
      <section class="source-section" aria-label="成果來源">
        <header class="source-heading"><h3>成果來源（選填）</h3><el-button data-source-manager :disabled="saving" @click="sourceVisible = true">{{ hasSources ? '管理來源' : '選擇來源' }}</el-button></header>
        <p>可關聯卡片、實驗或其他奇珍，原內容會保留。</p>
        <TreasureSourceSummary v-if="hasSources" :card="cardSource" :experiment="experimentSource" :treasures="selectedSources" :disabled="saving" @remove="(kind, id) => { if (kind === 'card') cardSource = null; else if (kind === 'experiment') experimentSource = null; else selectedSources = selectedSources.filter(s => s.id !== id) }" />
      </section>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </el-form>
    <TreasureSourcePicker v-if="sourceVisible" ref="sourcePicker" :treasure-id="treasure?.id" :source-treasures="selectedSources" :source-card="cardSource" :source-experiment="experimentSource" @cancel="sourceVisible = false" @confirm="selectedSources = $event.treasures; cardSource = $event.card; experimentSource = $event.experiment; sourceVisible = false" />
    </div>
    <template #footer="{ requestClose }">
      <template v-if="sourceVisible"><el-button @click="sourceVisible = false">取消變更</el-button><el-button type="primary" @click="sourcePicker?.confirm()">確認來源</el-button></template>
      <template v-else><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ treasure ? '儲存變更' : '收入館藏' }}</el-button></template>
    </template>
  </AppDialog>

</template>

<style scoped>
.form-content { min-width: 0; }
.form-content:focus { outline: none; }
.source-section { display: flex; flex-direction: column; gap: var(--space-sm); margin-top: var(--space-lg); padding-top: var(--space-lg); border-top: 1px solid var(--el-border-color-lighter); }
.source-heading { display: flex; justify-content: space-between; align-items: center; gap: var(--space-sm); }
.source-heading h3 { margin: 0; font-size: var(--type-ui); font-weight: var(--weight-medium); }
.source-section p { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.display-settings { margin-top: var(--space-lg); }
.display-settings summary { display: flex; justify-content: space-between; align-items: flex-start; gap: var(--space-sm); font-size: var(--type-ui); cursor: pointer; list-style: none; }
.display-settings summary::-webkit-details-marker { display: none; }
.display-settings summary:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.display-settings strong { font-weight: var(--weight-medium); }
.display-settings small { display: block; margin-top: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.display-toggle { flex-shrink: 0; color: var(--el-color-primary); }
.when-open, .display-settings[open] .when-closed { display: none; }
.display-settings[open] .when-open { display: inline; }
.display-fields { margin-top: var(--space-md); }
</style>
