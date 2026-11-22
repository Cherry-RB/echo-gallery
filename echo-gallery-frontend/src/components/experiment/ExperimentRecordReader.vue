<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import CardPickerDialog from '../CardPickerDialog.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import type { ExperimentRecordDto, ExperimentRecordReferenceDto, ExperimentTopicDto } from '../../types/experiment'
import type { CardDto } from '../../types/card'
import { formatDate } from '../../utils/formatDate'

const props = defineProps<{ modelValue: boolean; record: ExperimentRecordDto | null; experimentId?: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; edit: [] }>()
const topicOptions = ref<ExperimentTopicDto[]>([])
const selectedTopicIds = ref<number[]>([])
const topicsVisible = ref(false)
const savingTopics = ref(false)
const references = ref<ExperimentRecordReferenceDto[]>([])
const referenceUrl = ref('')
const referenceLabel = ref('')
const savingReference = ref(false)
const cardPickerVisible = ref(false)
const savingCardReference = ref(false)
watch(() => [props.modelValue, props.record?.id] as const, async ([visible]) => {
  if (!visible || !props.record || !props.experimentId) return
  try { const [all, selected] = await Promise.all([experimentApi.getTopics(props.experimentId), experimentApi.getRecordTopics(props.experimentId, props.record.id)]); topicOptions.value = all; selectedTopicIds.value = selected.map(topic => topic.id) }
  catch { topicOptions.value = []; selectedTopicIds.value = [] }
}, { immediate: true })
watch(() => [props.modelValue, props.record?.id, props.experimentId] as const, async ([visible]) => {
  if (!visible || !props.record || !props.experimentId) { references.value = []; return }
  try { references.value = await experimentApi.getRecordReferences(props.experimentId, props.record.id) }
  catch { references.value = [] }
}, { immediate: true })
const saveTopics = async () => { if (!props.record || !props.experimentId) return; savingTopics.value = true; try { await experimentApi.setRecordTopics(props.experimentId, props.record.id, selectedTopicIds.value); topicsVisible.value = false; ElMessage.success('已更新主題') } catch { ElMessage.error('無法更新主題') } finally { savingTopics.value = false } }

const addUrlReference = async () => {
  if (!props.record || !props.experimentId || !referenceUrl.value.trim()) return
  savingReference.value = true
  try {
    const linked = await experimentApi.createRecordReference(props.experimentId, props.record.id, {
      kind: 'URL', url: referenceUrl.value.trim(), label: referenceLabel.value.trim() || null,
    })
    references.value = [...references.value, linked]
    referenceUrl.value = ''
    referenceLabel.value = ''
  } catch { ElMessage.error('無法新增參考連結') } finally { savingReference.value = false }
}
const removeReference = async (reference: ExperimentRecordReferenceDto) => {
  if (!props.record || !props.experimentId) return
  try {
    await experimentApi.deleteRecordReference(props.experimentId, props.record.id, reference.id)
    references.value = references.value.filter(item => item.id !== reference.id)
  } catch { ElMessage.error('無法移除參考連結') }
}
const addCardReference = async (card: CardDto) => {
  if (!props.record || !props.experimentId) return
  savingCardReference.value = true
  try {
    const linked = await experimentApi.createRecordReference(props.experimentId, props.record.id, {
      kind: 'CARD', cardId: Number(card.id),
    })
    references.value = [...references.value, linked]
    cardPickerVisible.value = false
  } catch { ElMessage.error('無法加入收藏卡片參考') } finally { savingCardReference.value = false }
}
const highlightLabel = (record: ExperimentRecordDto) => ({
  GENERAL: '一般', GAIN: '有所收穫', CHALLENGE_OVERCOME: '克服困難', PEAK_EXPERIENCE: '高峰體驗',
}[record.highlight])
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    :title="record?.title || '探索紀錄'"
    class="record-dialog"
    width="min(620px, calc(100vw - 32px))"
    scroll-body
    @update:model-value="emit('update:modelValue', $event)"
  >
    <article v-if="record" class="record-reading">
      <div class="record-meta">
        <time :datetime="record.recordedOn" aria-label="紀錄日期">{{ record.recordedOn }}</time>
        <el-tag v-if="record.understandingNode" size="small">理解節點</el-tag>
        <el-tag v-if="record.methodCandidate" size="small">方法備選</el-tag>
        <el-tag v-if="record.highlight !== 'GENERAL'" size="small" type="success">{{ highlightLabel(record) }}</el-tag>
      </div>
      <section v-if="record.content" class="record-section"><h3 class="record-label">內容</h3><p>{{ record.content }}</p></section>
      <section v-if="record.practiceText" class="record-section"><h3 class="record-label">做法</h3><p>{{ record.practiceText }}</p></section>
      <section class="record-section record-references">
        <h3 class="record-label">參考資料</h3>
        <ul v-if="references.length"><li v-for="reference in references" :key="reference.id"><a v-if="reference.url" :href="reference.url" target="_blank" rel="noopener noreferrer">{{ reference.label || reference.url }}</a><span v-else>{{ reference.title || reference.label }}</span><el-button text type="danger" size="small" @click="removeReference(reference)">移除</el-button></li></ul>
        <p v-else class="no-references">尚未加入參考資料。</p>
        <div class="reference-form"><el-input v-model="referenceUrl" placeholder="https://…" /><el-input v-model="referenceLabel" placeholder="連結名稱（選填）" /><el-button :loading="savingReference" :disabled="!referenceUrl.trim()" @click="addUrlReference">新增連結</el-button></div>
        <el-button class="reference-card-picker" :loading="savingCardReference" @click="cardPickerVisible = true">選擇收藏卡片</el-button>
      </section>
      <details class="record-supplementary">
        <summary>紀錄資訊</summary>
        <dl class="record-timestamps">
          <div><dt>建立時間</dt><dd>{{ formatDate(record.createdAt) }}</dd></div>
          <div><dt>更新時間</dt><dd>{{ formatDate(record.updatedAt) }}</dd></div>
        </dl>
      </details>
    </article>
    <template #footer="{ requestClose }"><el-button @click="requestClose">關閉</el-button><el-button v-if="record" @click="topicsVisible = true">設定主題</el-button><el-button v-if="record" type="primary" @click="emit('edit')">編輯紀錄</el-button></template>
  </AppDialog>
  <el-dialog v-model="topicsVisible" title="設定主題" width="min(440px, calc(100vw - 32px))" append-to-body>
    <el-checkbox-group v-model="selectedTopicIds"><el-checkbox v-for="topic in topicOptions" :key="topic.id" :value="topic.id">{{ topic.name }}</el-checkbox></el-checkbox-group>
    <template #footer><el-button @click="topicsVisible = false">取消</el-button><el-button type="primary" :loading="savingTopics" @click="saveTopics">儲存</el-button></template>
  </el-dialog>
  <CardPickerDialog
    v-model="cardPickerVisible"
    title="加入收藏卡片"
    description="選擇一張與這筆探索紀錄相關的收藏卡片；原卡片不會被修改。"
    confirm-label="加入參考"
    settings-style="plain"
    :submitting="savingCardReference"
    @confirm="addCardReference"
  />
</template>

<style scoped src="./experimentRecordDialog.css"></style>

<style scoped>
.record-meta { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.record-meta time { margin-right: var(--space-xs); }
.record-section { margin-top: var(--space-lg); }
.record-section p { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-body); line-height: var(--leading-body); white-space: pre-wrap; overflow-wrap: anywhere; }
.record-timestamps { display: grid; gap: var(--space-xs); margin: var(--space-sm) 0 0; font-size: var(--type-meta); line-height: var(--leading-metadata); }
.record-timestamps > div { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.record-timestamps dt { color: var(--el-text-color-secondary); }
.record-timestamps dd { margin: 0; color: var(--el-text-color-regular); }
</style>

<style scoped>
.record-references ul { display: grid; gap: var(--space-xs); margin: 0; padding: 0; list-style: none; }
.record-references li { display: flex; align-items: center; gap: var(--space-xs); }
.record-references a { min-width: 0; overflow: hidden; color: var(--el-color-primary); text-overflow: ellipsis; white-space: nowrap; }
.no-references { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); }
.reference-form { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; gap: var(--space-xs); margin-top: var(--space-sm); }
.reference-card-picker { margin-top: var(--space-sm); }
@media (max-width: 560px) { .reference-form { grid-template-columns: 1fr; } }
</style>
