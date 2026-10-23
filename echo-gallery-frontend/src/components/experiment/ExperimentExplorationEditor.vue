<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ExperimentExplorationDto, ExperimentExplorationRecordDto, ExplorationThreadMethodDto } from '../../types/experiment'
import { experimentApi } from '../../utils/api/experimentApi'
import { getTextLength, trimToTextLength } from '../../utils/textLength'
import AppDialog from '../AppDialog.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

type EditorMode = 'TRY' | 'DISCOVERY'
type TryPane = 'COMPOSE' | 'FAVORITES'

const props = defineProps<{
  modelValue: boolean
  experimentId: number
  mode: EditorMode
  record?: ExperimentExplorationRecordDto | null
  threadId?: number
  method?: ExplorationThreadMethodDto | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  closed: []
  saved: []
}>()

const queryClient = useQueryClient()
const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const emptyExploration: ExperimentExplorationDto = { currentTry: '', currentTrySourceMethodId: null, favoriteTries: [], records: [] }
const tryPane = ref<TryPane>('COMPOSE')
const tryDraft = ref('')
const selectedTrySuggestion = ref('')
const newFavoriteTryDraft = ref('')
const editingFavoriteTry = ref<string | null>(null)
const editingFavoriteTryDraft = ref('')
const recordTryDraft = ref('')
const discoveryDraft = ref('')
const includeTryInDiscovery = ref(true)
const saving = ref(false)
const initializedMode = ref<EditorMode | null>(null)
const selectedSourceMethodId = ref<number | null>(null)
const sourceThreads = useQuery({
  queryKey: computed(() => ['experiment-threads', props.experimentId]),
  queryFn: () => experimentApi.getThreads(props.experimentId),
  enabled: computed(() => visible.value && props.mode === 'TRY'),
})
const sourceMethods = computed(() => (sourceThreads.data.value ?? []).flatMap(thread =>
  thread.methods.map(method => ({ ...method, threadName: thread.name }))))
const applySourceMethod = (id: number | null) => {
  selectedSourceMethodId.value = id
  const method = sourceMethods.value.find(item => item.id === id)
  if (method) tryDraft.value = method.content
}

const explorationQuery = useQuery({
  queryKey: computed(() => ['experiment-exploration', props.experimentId]),
  queryFn: () => experimentApi.getExploration(props.experimentId),
  enabled: visible,
})
const exploration = computed(() => explorationQuery.data.value ?? emptyExploration)
const recentTries = computed(() => [...new Set(
  exploration.value.records.map(record => record.tryText).filter((text): text is string => Boolean(text)),
)].slice(0, 3))

watch([visible, () => props.mode, explorationQuery.isSuccess], ([isVisible, mode, isLoaded]) => {
  if (!isVisible) {
    initializedMode.value = null
    return
  }
  if (!isLoaded || initializedMode.value === mode) return
  if (mode === 'TRY') {
    tryPane.value = 'COMPOSE'
    tryDraft.value = props.method?.content ?? exploration.value.currentTry
    selectedSourceMethodId.value = props.method?.id ?? exploration.value.currentTrySourceMethodId
    selectedTrySuggestion.value = ''
  }
  if (mode === 'DISCOVERY') {
    recordTryDraft.value = props.record?.tryText ?? props.method?.content ?? ''
    discoveryDraft.value = props.record?.discovery ?? ''
    includeTryInDiscovery.value = true
  }
  initializedMode.value = mode
}, { immediate: true })

const close = () => {
  tryPane.value = 'COMPOSE'
  visible.value = false
}

const handleClosed = () => {
  tryPane.value = 'COMPOSE'
  emit('closed')
}

const setExploration = async (value: ExperimentExplorationDto) => {
  queryClient.setQueryData(['experiment-exploration', props.experimentId], value)
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['experiment', props.experimentId] }),
    queryClient.invalidateQueries({ queryKey: ['experiments'] }),
    queryClient.invalidateQueries({ queryKey: ['overview'] }),
  ])
}

const saveTry = async () => {
  if (getTextLength(tryDraft.value) > 500) return
  const currentTry = trimToTextLength(tryDraft.value, 500).trim()
  if (saving.value) return
  if (currentTry === exploration.value.currentTry && selectedSourceMethodId.value === exploration.value.currentTrySourceMethodId) {
    close()
    return
  }

  saving.value = true
  try {
    await setExploration(await experimentApi.updateCurrentTry(props.experimentId, currentTry, currentTry ? selectedSourceMethodId.value : null))
    ElMessage.success('試法已更新')
    close()
  } catch {
    ElMessage.error('儲存試法失敗，請稍後再試。')
  } finally {
    saving.value = false
  }
}

const saveDiscovery = async () => {
  if (getTextLength(recordTryDraft.value) > 500 || getTextLength(discoveryDraft.value) > 1000) return
  const discovery = trimToTextLength(discoveryDraft.value, 1000).trim()
  const tryText = trimToTextLength(recordTryDraft.value, 500).trim()
  if (saving.value) return
  if (props.record && !tryText && !discovery) {
    ElMessage.warning('試法與發現不可同時清空，請保留至少一項內容。')
    return
  }
  if (!props.record && !discovery) return

  saving.value = true
  try {
    if (props.threadId && !props.record) {
      await experimentApi.createThreadDiscovery(props.experimentId, props.threadId, {
        methodId: props.method?.id ?? null,
        tryText: includeTryInDiscovery.value ? tryText : '',
        discovery,
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['experiment-exploration', props.experimentId] }),
        queryClient.invalidateQueries({ queryKey: ['experiment-threads', props.experimentId] }),
        queryClient.invalidateQueries({ queryKey: ['experiment', props.experimentId] }),
        queryClient.invalidateQueries({ queryKey: ['experiments'] }),
        queryClient.invalidateQueries({ queryKey: ['overview'] }),
      ])
      ElMessage.success('發現已保存至探索紀錄與探索線')
      emit('saved')
      close()
      return
    }
    await setExploration(props.record
      ? await experimentApi.updateExplorationRecord(props.experimentId, props.record.id, tryText, discovery)
      : await experimentApi.createExplorationRecord(props.experimentId, discovery, includeTryInDiscovery.value))
    ElMessage.success(props.record ? '探索紀錄已更新' : '已留下這次發現')
    emit('saved')
    close()
  } catch {
    ElMessage.error('儲存發現失敗，請稍後再試。')
  } finally {
    saving.value = false
  }
}

const openFavoriteManagement = () => {
  newFavoriteTryDraft.value = tryDraft.value
  editingFavoriteTry.value = null
  tryPane.value = 'FAVORITES'
}

const saveFavoriteTries = async (favoriteTries: string[], successMessage: string) => {
  if (saving.value) return false
  saving.value = true
  try {
    await setExploration(await experimentApi.updateFavoriteTries(props.experimentId, favoriteTries))
    ElMessage.success(successMessage)
    return true
  } catch {
    ElMessage.error('儲存常用試法失敗，請稍後再試。')
    return false
  } finally {
    saving.value = false
  }
}

const addFavoriteTry = async () => {
  const text = trimToTextLength(newFavoriteTryDraft.value, 500).trim()
  if (!text) return
  if (exploration.value.favoriteTries.includes(text)) {
    ElMessage.info('這個試法已在常用清單中。')
    return
  }
  if (exploration.value.favoriteTries.length >= 3) {
    ElMessage.info('最多保存 3 種常用試法；可先修改或移除其中一種。')
    return
  }
  if (await saveFavoriteTries([...exploration.value.favoriteTries, text], '已新增常用試法。')) {
    newFavoriteTryDraft.value = ''
  }
}

const startEditFavoriteTry = (text: string) => {
  editingFavoriteTry.value = text
  editingFavoriteTryDraft.value = text
}

const updateFavoriteTry = async () => {
  const original = editingFavoriteTry.value
  const text = trimToTextLength(editingFavoriteTryDraft.value, 500).trim()
  if (!original || !text) return
  if (text !== original && exploration.value.favoriteTries.includes(text)) {
    ElMessage.info('這個試法已在常用清單中。')
    return
  }
  if (await saveFavoriteTries(
    exploration.value.favoriteTries.map(saved => saved === original ? text : saved),
    '常用試法已更新。',
  )) {
    editingFavoriteTry.value = null
  }
}

const removeFavoriteTry = async (text: string) => {
  try {
    await ElMessageBox.confirm('移除後不會影響已留下的探索紀錄。', '移除常用試法', {
      confirmButtonText: '移除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await saveFavoriteTries(
    exploration.value.favoriteTries.filter(saved => saved !== text),
    '已移除常用試法。',
  )
}
</script>

<template>
  <AppDialog ref="shell" :busy="saving" :draft="[tryDraft, selectedSourceMethodId, recordTryDraft, discoveryDraft, includeTryInDiscovery, newFavoriteTryDraft && newFavoriteTryDraft !== tryDraft ? newFavoriteTryDraft : '', editingFavoriteTry && editingFavoriteTryDraft !== editingFavoriteTry ? editingFavoriteTryDraft : '']" :draft-ready="initializedMode !== null"
    class="app-record-form"
    v-model="visible"
    :title="mode === 'TRY' ? (tryPane === 'COMPOSE' ? '設定試法' : '常用試法') : (record ? '編輯探索紀錄' : '記下發現')"
    width="min(560px, calc(100vw - 32px))"
    scroll-body
    @closed="handleClosed"
  >
    <el-skeleton v-if="explorationQuery.isLoading.value" :rows="4" animated />
    <el-result v-else-if="explorationQuery.isError.value" icon="error" title="無法載入探索資料">
      <template #extra><el-button type="primary" @click="explorationQuery.refetch()">重新載入</el-button></template>
    </el-result>
    <template v-else-if="mode === 'TRY' && tryPane === 'COMPOSE'">
      <p class="dialog-intro">先寫下一件容易開始的小事。它會留在「目前想試」，等你記下發現時再決定要不要一起收進一筆探索紀錄。</p>
      <label class="try-input-label" for="source-method">探索線試法（選填）</label>
      <el-select id="source-method" :model-value="selectedSourceMethodId" clearable filterable class="try-suggestion-select" :loading="sourceThreads.isPending.value" placeholder="自由輸入，或選擇探索線試法" @update:model-value="applySourceMethod($event ?? null)">
        <el-option v-for="item in sourceMethods" :key="item.id" :value="item.id" :label="item.threadName + ' · ' + item.content" />
      </el-select>
      <p v-if="sourceThreads.isError.value" class="dialog-hint">無法載入探索線試法。<el-button text @click="sourceThreads.refetch()">重試</el-button></p>
      <label class="try-suggestion-label" for="try-suggestion-select">從既有試法開始（可選）</label>
      <el-select id="try-suggestion-select" v-model="selectedTrySuggestion" class="try-suggestion-select" placeholder="最近用過或常用試法" :disabled="!recentTries.length && !exploration.favoriteTries.length" @change="tryDraft = $event; selectedSourceMethodId = null">
        <el-option-group v-if="recentTries.length" label="最近用過（最多 3 種）">
          <el-option v-for="text in recentTries" :key="`recent-${text}`" :label="text" :value="text" />
        </el-option-group>
        <el-option-group v-if="exploration.favoriteTries.length" label="我的常用試法（最多 3 種）">
          <el-option v-for="text in exploration.favoriteTries" :key="`saved-${text}`" :label="text" :value="text" />
        </el-option-group>
      </el-select>
      <label class="try-input-label" for="try-draft">這次想試什麼？</label>
      <el-input id="try-draft" v-model="tryDraft" type="textarea" :rows="4" maxlength="500" placeholder="例如：晚餐後拿起紙筆，隨意畫兩分鐘。" aria-label="這次想試的小事" @input="selectedTrySuggestion = ''" />
      <FieldCounter :value="tryDraft" :limit="500" />
      <p v-if="getTextLength(tryDraft) > 500" class="dialog-hint">此試法超過目前試法的 500 字限制，請先精簡採用文字；探索線原文會保留。</p>
      <div class="try-secondary-actions">
        <el-button text size="small" @click="openFavoriteManagement">管理常用試法</el-button>
      </div>
    </template>
    <template v-else-if="mode === 'TRY'">
      <p class="dialog-intro">將想反覆使用的起點放在這裡。最多保存 3 種；修改或移除不會影響已留下的探索紀錄。</p>
      <section class="favorite-try-create">
        <label for="new-favorite-try">新增常用試法</label>
        <el-input id="new-favorite-try" v-model="newFavoriteTryDraft" type="textarea" :rows="2" maxlength="500" placeholder="輸入想保存的試法" />
        <div><span>{{ getTextLength(newFavoriteTryDraft) }} / 500</span><el-button size="small" type="primary" :disabled="!newFavoriteTryDraft.trim() || exploration.favoriteTries.length >= 3" :loading="saving" @click="addFavoriteTry">新增</el-button></div>
      </section>
      <p v-if="exploration.favoriteTries.length >= 3" class="dialog-hint">已保存 3 種常用試法；可先修改或移除其中一種。</p>
      <ul v-if="exploration.favoriteTries.length" class="favorite-try-list">
        <li v-for="text in exploration.favoriteTries" :key="text">
          <template v-if="editingFavoriteTry === text">
            <el-input v-model="editingFavoriteTryDraft" type="textarea" :rows="2" maxlength="500" aria-label="編輯常用試法" />
            <div class="favorite-try-row-actions"><el-button size="small" @click="editingFavoriteTry = null">取消</el-button><el-button size="small" type="primary" :loading="saving" :disabled="!editingFavoriteTryDraft.trim()" @click="updateFavoriteTry">儲存</el-button></div>
          </template>
          <template v-else>
            <p>{{ text }}</p>
            <div class="favorite-try-row-actions"><el-button text size="small" @click="startEditFavoriteTry(text)">編輯</el-button><el-button text type="danger" size="small" @click="removeFavoriteTry(text)">移除</el-button></div>
          </template>
        </li>
      </ul>
      <p v-else class="empty-copy">尚未保存常用試法。</p>
    </template>
    <template v-else>
      <p v-if="threadId && !record" class="dialog-intro">確認後同時保存原始探索紀錄，並加入目前探索線。</p>
      <p v-if="!threadId" class="dialog-intro">{{ record ? '可修改這筆紀錄的試法與發現；清空其中一欄後儲存即可。' : '試過、沒能開始、或想法變了，都可以寫一句。每次儲存只會產生一筆探索紀錄。' }}</p>
      <template v-if="record || threadId">
        <label class="record-input-label" for="record-try-draft">試法</label>
        <el-input id="record-try-draft" v-model="recordTryDraft" type="textarea" :rows="3" maxlength="500" placeholder="尚未記下試法" aria-label="這筆紀錄的試法" />
        <FieldCounter :value="recordTryDraft" :limit="500" />
        <p v-if="getTextLength(recordTryDraft) > 500" class="dialog-hint">請將本次保留的試法精簡至 500 字內；探索線原文不受影響。</p>
        <label class="record-input-label record-discovery-label" for="record-discovery-draft">發現</label>
      </template>
      <el-input :id="record ? 'record-discovery-draft' : undefined" v-model="discoveryDraft" type="textarea" :rows="5" maxlength="1000" placeholder="記下實際經過、遇到的阻礙，或尚待確認的線索。" aria-label="這次發生了什麼" />
      <FieldCounter :value="discoveryDraft" :limit="1000" />
      <div v-if="exploration.currentTry && !record && !threadId" class="discovery-try-choice">
        <p><span>目前試法</span>{{ exploration.currentTry }}</p>
        <el-checkbox v-model="includeTryInDiscovery">把它一起留在這筆探索紀錄</el-checkbox>
        <small v-if="includeTryInDiscovery">儲存後會清空目前試法，並形成「試法 → 發現」的一筆紀錄。</small>
        <small v-else>這次只記發現；目前試法會保留，之後仍可繼續使用。</small>
      </div>
    </template>
    <template #footer="{ requestClose }">
      <template v-if="mode === 'TRY' && tryPane === 'FAVORITES'">
        <el-button :disabled="saving" @click="tryPane = 'COMPOSE'">返回試法</el-button>
      </template>
      <template v-else>
        <el-button :disabled="saving" @click="requestClose">取消</el-button>
        <el-button v-if="mode === 'TRY'" type="primary" :loading="saving" :disabled="getTextLength(tryDraft) > 500" @click="saveTry">儲存試法</el-button>
        <el-button v-else type="primary" :loading="saving" :disabled="(!record && !discoveryDraft.trim()) || getTextLength(recordTryDraft) > 500 || getTextLength(discoveryDraft) > 1000" @click="saveDiscovery">{{ record ? '儲存探索紀錄' : '留下這次發現' }}</el-button>
      </template>
    </template>
  </AppDialog>
</template>

<style scoped>
.dialog-intro { margin: 0 0 var(--space-md); color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.field-counter { width: 100%; margin: var(--space-xs) 0 0; color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.try-suggestion-label, .try-input-label, .favorite-try-create > label { display: block; margin-bottom: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.try-suggestion-select { width: 100%; margin-bottom: var(--space-md); }
.try-secondary-actions { display: flex; justify-content: flex-end; margin-top: var(--space-xs); }
.record-input-label { display: block; margin: 0 0 var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.record-discovery-label { margin-top: var(--space-md); }
.discovery-try-choice { margin-top: var(--space-md); padding: var(--space-sm) var(--space-md); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); background: var(--surface-subtle); }
.discovery-try-choice p { margin: 0 0 var(--space-sm); color: var(--el-text-color-regular); line-height: var(--leading-ui); white-space: pre-line; overflow-wrap: anywhere; }
.discovery-try-choice p span { display: block; margin-bottom: var(--space-2xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.discovery-try-choice small { display: block; margin-top: var(--space-xs); color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-ui); }
.favorite-try-create { padding-bottom: var(--space-md); border-bottom: 1px solid var(--el-border-color-lighter); }
.favorite-try-create > div { display: flex; align-items: center; justify-content: space-between; gap: var(--space-sm); margin-top: var(--space-xs); color: var(--el-text-color-placeholder); font-size: var(--type-meta); }
.dialog-hint, .empty-copy { margin: var(--space-md) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-caption); line-height: var(--leading-ui); }
.favorite-try-list { display: flex; flex-direction: column; gap: var(--space-sm); padding: 0; margin: var(--space-md) 0 0; list-style: none; }
.favorite-try-list li { padding: var(--space-sm); border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-sm); }
.favorite-try-list p { margin: 0; color: var(--el-text-color-primary); white-space: pre-line; overflow-wrap: anywhere; }
.favorite-try-row-actions { display: flex; justify-content: flex-end; gap: var(--space-xs); margin-top: var(--space-xs); }
</style>
