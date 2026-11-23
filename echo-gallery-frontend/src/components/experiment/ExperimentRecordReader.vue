<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MoreFilled, Plus } from '@element-plus/icons-vue'
import AppDialog from '../AppDialog.vue'
import CardPickerDialog from '../CardPickerDialog.vue'
import ExpandableText from '../ExpandableText.vue'
import { experimentApi } from '../../utils/api/experimentApi'
import type { ExperimentRecordDto, ExperimentRecordReferenceDto, ExperimentTopicDto } from '../../types/experiment'
import type { CardDto } from '../../types/card'
import { formatDate } from '../../utils/formatDate'

const props = defineProps<{ modelValue: boolean; record: ExperimentRecordDto | null; experimentId?: number; direction?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; edit: []; changed: [] }>()
const topicOptions = ref<ExperimentTopicDto[]>([])
const assignedTopics = ref<ExperimentTopicDto[]>([])
const selectedTopicIds = ref<number[]>([])
const topicsVisible = ref(false), savingTopics = ref(false), topicsLoading = ref(false), topicsError = ref(false)
const topicSearch = ref('')
const filteredTopicOptions = computed(() => topicOptions.value.filter(topic => topic.name.toLocaleLowerCase().includes(topicSearch.value.trim().toLocaleLowerCase())))
const references = ref<ExperimentRecordReferenceDto[]>([])
const referencesLoading = ref(false), referencesError = ref(false)
const referenceUrl = ref(''), referenceLabel = ref(''), referenceError = ref('')
const referenceDialogVisible = ref(false), savingReference = ref(false)
const cardPickerVisible = ref(false), savingCardReference = ref(false), removingReferenceId = ref<number>()
const busy = computed(() => savingReference.value || savingCardReference.value || savingTopics.value || removingReferenceId.value != null)
let generation = 0
onBeforeUnmount(() => { generation++ })
const load = async () => {
  const current = ++generation
  topicOptions.value = []
  assignedTopics.value = props.record?.topics ?? []
  references.value = []
  if (!props.modelValue || !props.record || !props.experimentId) return
  topicsLoading.value = true; referencesLoading.value = true
  topicsError.value = false; referencesError.value = false
  const experimentId = props.experimentId, recordId = props.record.id
  await Promise.all([
    Promise.all([experimentApi.getTopics(experimentId), experimentApi.getRecordTopics(experimentId, recordId)])
      .then(([all, selected]) => { if (current === generation) { topicOptions.value = all; assignedTopics.value = selected } })
      .catch(() => { if (current === generation) topicsError.value = true })
      .finally(() => { if (current === generation) topicsLoading.value = false }),
    experimentApi.getRecordReferences(experimentId, recordId)
      .then(value => { if (current === generation) references.value = value })
      .catch(() => { if (current === generation) referencesError.value = true })
      .finally(() => { if (current === generation) referencesLoading.value = false }),
  ])
}
watch(() => [props.modelValue, props.record?.id, props.experimentId] as const, () => {
  topicsVisible.value = false; referenceDialogVisible.value = false; cardPickerVisible.value = false
  referenceUrl.value = ''; referenceLabel.value = ''; referenceError.value = ''
  void load()
}, { immediate: true })
const openTopics = () => {
  selectedTopicIds.value = assignedTopics.value.map(topic => topic.id)
  topicSearch.value = ''
  topicsVisible.value = true
}
const saveTopics = async () => {
  if (!props.record || !props.experimentId || savingTopics.value) return
  savingTopics.value = true
  try {
    assignedTopics.value = await experimentApi.setRecordTopics(props.experimentId, props.record.id, selectedTopicIds.value)
    topicsVisible.value = false
    emit('changed')
    ElMessage.success('已更新主題')
  } catch { ElMessage.error('無法更新主題') } finally { savingTopics.value = false }
}
const referenceCommand = (command: string) => {
  if (command === 'card') cardPickerVisible.value = true
  else { referenceUrl.value = ''; referenceLabel.value = ''; referenceError.value = ''; referenceDialogVisible.value = true }
}
const addUrlReference = async () => {
  if (!props.record || !props.experimentId || !referenceUrl.value.trim() || savingReference.value) return
  savingReference.value = true; referenceError.value = ''
  try {
    const linked = await experimentApi.createRecordReference(props.experimentId, props.record.id, {
      kind: 'URL', url: referenceUrl.value.trim(), label: referenceLabel.value.trim() || null,
    })
    references.value = [...references.value, linked]
    referenceDialogVisible.value = false
    referenceUrl.value = ''; referenceLabel.value = ''
  } catch { referenceError.value = '無法新增參考連結，請確認網址後重試。' } finally { savingReference.value = false }
}
const removeReference = async (reference: ExperimentRecordReferenceDto) => {
  if (!props.record || !props.experimentId || removingReferenceId.value != null) return
  removingReferenceId.value = reference.id
  try {
    await experimentApi.deleteRecordReference(props.experimentId, props.record.id, reference.id)
    references.value = references.value.filter(item => item.id !== reference.id)
  } catch { ElMessage.error('無法移除參考連結') } finally { removingReferenceId.value = undefined }
}
const addCardReference = async (card: CardDto) => {
  if (!props.record || !props.experimentId || savingCardReference.value) return
  savingCardReference.value = true
  try {
    const linked = await experimentApi.createRecordReference(props.experimentId, props.record.id, { kind: 'CARD', cardId: Number(card.id) })
    references.value = [...references.value, linked]
    cardPickerVisible.value = false
  } catch { ElMessage.error('無法加入收藏卡片參考') } finally { savingCardReference.value = false }
}
const highlightLabel = (record: ExperimentRecordDto) => ({
  GENERAL: '一般', GAIN: '有所收穫', CHALLENGE_OVERCOME: '克服困難', PEAK_EXPERIENCE: '高峰體驗',
}[record.highlight])
</script>

<template>
  <AppDialog :model-value="modelValue" :title="record?.title || '探索紀錄'" class="record-dialog" width="min(580px, calc(100vw - 32px))" :busy="busy" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <article v-if="record" class="record-reading">
      <div v-if="direction" class="direction-context">
        <span class="record-label"><strong>終點</strong>｜想達成的改變</span>
        <ExpandableText :content="direction" :lines="5" />
      </div>
      <section v-if="record.content" class="record-section record-body" aria-label="紀錄內容"><h3 class="record-label">內容</h3><p>{{ record.content }}</p></section>
      <section v-if="record.practiceText" class="record-section record-practice"><h3 class="record-label">做法</h3><p>{{ record.practiceText }}</p></section>
      <section class="record-section record-topics">
        <div class="reader-section-heading"><h3 class="record-label">主題</h3><el-button v-if="experimentId" link type="primary" size="small" :disabled="topicsLoading || topicsError || busy" @click="openTopics">編輯主題</el-button></div>
        <span v-if="topicsLoading" class="reader-hint" role="status">正在載入主題…</span>
        <span v-else-if="topicsError" class="reader-hint" role="alert">無法載入主題。<el-button link type="primary" @click="load">重試</el-button></span>
        <div v-else-if="assignedTopics.length" class="reader-topic-tags"><span v-for="topic in assignedTopics" :key="topic.id" class="record-badge record-badge--topic">{{ topic.name }}</span></div>
        <span v-else class="reader-hint">未分類</span>
      </section>
      <details open class="record-supplementary reader-supplementary">
        <summary>補充資訊</summary>
        <dl class="record-details">
          <div><dt>紀錄日期</dt><dd><time :datetime="record.recordedOn">{{ record.recordedOn }}</time></dd></div>
          <div><dt>標記</dt><dd class="record-badges"><span v-if="record.understandingNode" class="record-badge">理解節點</span><span v-if="record.methodCandidate" class="record-badge">方法備選</span><span v-if="record.highlight !== 'GENERAL'" class="record-badge record-badge--highlight">{{ highlightLabel(record) }}</span><span v-if="!record.understandingNode && !record.methodCandidate && record.highlight === 'GENERAL'" class="reader-hint">一般</span></dd></div>
        </dl>
        <dl class="record-timestamps"><div><dt>建立時間</dt><dd>{{ formatDate(record.createdAt) }}</dd></div><div><dt>更新時間</dt><dd>{{ formatDate(record.updatedAt) }}</dd></div></dl>
      </details>
      <section class="record-section record-references">
        <div v-if="referencesLoading || referencesError || references.length" class="reader-section-heading">
          <h3 class="record-label">參考資料</h3>
          <el-dropdown v-if="experimentId" trigger="click" :disabled="busy" @command="referenceCommand"><el-button link type="primary" size="small" :icon="Plus" :disabled="busy">新增</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="url">加入連結</el-dropdown-item><el-dropdown-item command="card">加入收藏卡片</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
        <p v-if="referencesLoading" class="reader-hint" role="status">正在載入參考資料…</p>
        <p v-else-if="referencesError" class="reader-hint" role="alert">無法載入參考資料。<el-button link type="primary" @click="load">重試</el-button></p>
        <ul v-else-if="references.length" class="reference-list">
          <li v-for="reference in references" :key="reference.id">
            <div class="reference-name"><span>{{ reference.kind === 'CARD' ? '收藏卡片' : '外部連結' }}</span><a v-if="reference.url" :href="reference.url" target="_blank" rel="noopener noreferrer">{{ reference.label || reference.url }}</a><router-link v-else-if="reference.cardId" :to="'/cards/' + reference.cardId">{{ reference.title || reference.label || '收藏卡片' }}</router-link><span v-else>{{ reference.title || reference.label }}</span></div>
            <el-dropdown trigger="click" :disabled="busy"><el-button text circle size="small" :icon="MoreFilled" :disabled="busy" aria-label="參考資料操作" /><template #dropdown><el-dropdown-menu><el-dropdown-item @click="removeReference(reference)">移除參考</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
          </li>
        </ul>
        <el-dropdown v-else-if="experimentId" trigger="click" :disabled="busy" @command="referenceCommand"><el-button link type="primary" size="small" :icon="Plus" :disabled="busy">加入參考資料</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="url">加入連結</el-dropdown-item><el-dropdown-item command="card">加入收藏卡片</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        <p v-else class="reader-hint">尚未加入參考資料。</p>
      </section>
    </article>
    <template #footer="{ requestClose }"><el-button :disabled="busy" @click="requestClose">關閉</el-button><el-button v-if="record" type="primary" :disabled="busy" @click="emit('edit')">編輯紀錄</el-button></template>
  </AppDialog>
  <AppDialog v-model="topicsVisible" title="設定主題" pattern="compact-form" width="min(520px, calc(100vw - 32px))" :draft="selectedTopicIds" :busy="savingTopics" scroll-body>
    <el-input v-model="topicSearch" clearable placeholder="搜尋主題" aria-label="搜尋可選主題" />
    <el-checkbox-group v-model="selectedTopicIds" class="reader-topic-options"><el-checkbox v-for="topic in filteredTopicOptions" :key="topic.id" :value="topic.id">{{ topic.name }}</el-checkbox></el-checkbox-group>
    <p v-if="!filteredTopicOptions.length" class="reader-hint">沒有符合的主題。</p>
    <template #footer="{ requestClose }"><el-button :disabled="savingTopics" @click="requestClose">取消</el-button><el-button type="primary" :loading="savingTopics" @click="saveTopics">儲存</el-button></template>
  </AppDialog>
  <AppDialog v-model="referenceDialogVisible" title="加入參考連結" pattern="compact-form" width="min(520px, calc(100vw - 32px))" :draft="[referenceUrl, referenceLabel]" :busy="savingReference">
    <el-form label-position="top" :disabled="savingReference" @submit.prevent="addUrlReference">
      <el-form-item label="網址" required><el-input v-model="referenceUrl" placeholder="https://…" maxlength="2048" aria-label="參考網址" /></el-form-item>
      <el-form-item label="連結名稱"><el-input v-model="referenceLabel" maxlength="255" aria-label="連結名稱" /></el-form-item>
      <el-alert v-if="referenceError" :title="referenceError" type="error" :closable="false" />
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="savingReference" @click="requestClose">取消</el-button><el-button type="primary" :loading="savingReference" :disabled="!referenceUrl.trim()" @click="addUrlReference">加入參考</el-button></template>
  </AppDialog>
  <CardPickerDialog v-model="cardPickerVisible" title="加入收藏卡片" description="選擇一張與這筆探索紀錄相關的收藏卡片；原卡片不會被修改。" confirm-label="加入參考" settings-style="plain" :submitting="savingCardReference" @confirm="addCardReference" />
</template>

<style scoped src="./experimentRecordDialog.css"></style>

<style scoped>
.direction-context { margin-bottom: var(--space-md); padding-bottom: var(--space-sm); border-bottom: 1px solid var(--el-border-color-lighter); }
.direction-context .record-label { display: block; color: var(--el-color-primary); }
.direction-context strong { font-weight: var(--weight-semibold); }
.direction-context :deep(.expandable-content) { color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.record-section p { margin: 0; color: var(--el-text-color-primary); font-size: var(--type-body); line-height: var(--leading-body); white-space: pre-wrap; overflow-wrap: anywhere; }
.record-section + .record-section { margin-top: 0; padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-lighter); }
.record-practice p { color: var(--el-text-color-regular); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.reader-section-heading { display: flex; align-items: center; justify-content: space-between; gap: var(--space-xs); margin-bottom: var(--space-xs); }
.reader-section-heading .record-label { margin: 0; }
.reader-topic-tags { display: flex; flex-wrap: wrap; gap: var(--space-2xs); }
.record-badges { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-2xs); }
.record-badge { display: inline-flex; align-items: center; min-height: 22px; max-width: 100%; box-sizing: border-box; padding: 1px var(--space-xs); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-sm); background: var(--el-fill-color-light); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); overflow-wrap: anywhere; }
.record-badge--topic { color: var(--el-text-color-regular); }
.record-badge--highlight { border-color: var(--el-color-success-light-7); background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.reader-hint, .reading-empty { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
.reader-supplementary { margin-top: var(--space-sm); padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-summary); }
.reader-supplementary > summary { margin: calc(var(--space-sm) * -1); padding: var(--space-sm); }
.reader-supplementary[open] > summary { margin-bottom: 0; }
.record-details { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-xs); margin: var(--space-sm) 0 0; font-size: var(--type-meta); line-height: var(--leading-ui); }
.record-details dt, .record-timestamps dt { color: var(--el-text-color-secondary); }
.record-details dd { margin: var(--space-2xs) 0 0; color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.record-references { margin-top: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-lighter); }
.reference-list { display: grid; gap: var(--space-xs); margin: 0; padding: 0; list-style: none; }
.reference-list li { display: flex; align-items: flex-start; gap: var(--space-xs); }
.reference-name { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-2xs) var(--space-xs); flex: 1; min-width: 0; }
.reference-name > span:first-child { color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.reference-name a { color: var(--el-color-primary); font-size: var(--type-secondary); line-height: var(--leading-ui); overflow-wrap: anywhere; text-decoration: none; }
.reference-name a:hover { text-decoration: underline; }
.reference-name a:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
.record-timestamps { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-xs); margin: var(--space-xs) 0 0; font-size: var(--type-meta); line-height: var(--leading-ui); }
.record-timestamps dd { margin: 0; color: var(--el-text-color-regular); overflow-wrap: anywhere; }
.reader-topic-options { display: grid; gap: var(--space-xs); margin-top: var(--space-md); }
.reader-topic-options :deep(.el-checkbox) { margin: 0; height: auto; min-height: 32px; white-space: normal; }
.reader-topic-options :deep(.el-checkbox__label) { white-space: normal; overflow-wrap: anywhere; }
@media (max-width: 480px) { .record-details, .record-timestamps { grid-template-columns: minmax(0, 1fr); } }
</style>
