<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = defineProps<{ modelValue: boolean; experimentId: number; threadId: number; methodId: number | null; label: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const invalidate = useExplorationInvalidation()
const text = ref(''), original = ref(''), version = ref(0), loaded = ref(false)
const loading = ref(false), saving = ref(false), uncertain = ref(false), conflict = ref(false), error = ref('')
const baseline = ref<string>()
const field = ref<{ focus: () => void }>()
let generation = 0
const canSave = computed(() => loaded.value && !loading.value && !saving.value && !uncertain.value && !conflict.value && text.value !== original.value)
const normalized = (value: string) => value.trim() ? value : ''

async function load() {
  const current = ++generation
  loading.value = true
  try {
    const history = await api.history(props.experimentId, props.threadId, props.methodId)
    if (current !== generation) return
    text.value = original.value = history.text
    version.value = history.version
    baseline.value = JSON.stringify(history.text)
    loaded.value = true; uncertain.value = false; conflict.value = false; error.value = ''
    loading.value = false
    await nextTick()
    if (current === generation) field.value?.focus()
  } catch (e) {
    if (current === generation) error.value = apiErrorMessage(e, '無法載入歷次整理，請重試。')
  } finally { if (current === generation) loading.value = false }
}
watch(() => [props.modelValue, props.experimentId, props.threadId, props.methodId] as const, ([visible]) => {
  if (visible) void load(); else generation++
}, { immediate: true })
onBeforeUnmount(() => generation++)
async function reload() {
  if (saving.value || loading.value) return
  if (loaded.value && text.value !== original.value) {
    try { await ElMessageBox.confirm('重新載入會取代目前草稿，請先複製需要保留的文字。', '重新載入歷次整理', { confirmButtonText: '捨棄並重新載入', cancelButtonText: '返回編輯', type: 'warning' }) }
    catch { return }
  }
  await load()
}
async function finish() {
  baseline.value = JSON.stringify(text.value)
  try { await invalidate(props.experimentId) }
  catch { ElMessage.warning('內容已儲存，畫面更新失敗，請重新載入。') }
  ElMessage.success('歷次整理已儲存')
  emit('saved'); emit('update:modelValue', false)
}
async function verify() {
  if (saving.value) return
  saving.value = true
  try {
    const history = await api.history(props.experimentId, props.threadId, props.methodId)
    if (history.text === normalized(text.value) && history.version >= version.value) await finish()
    else {
      uncertain.value = false
      conflict.value = history.version !== version.value
      error.value = conflict.value ? '資料已更新，草稿已保留；請核對後重新載入。' : '尚未確認到這次儲存，可以再次儲存；草稿已保留。'
    }
  } catch { error.value = '仍無法確認儲存結果，草稿已保留，請稍後再確認。' }
  finally { saving.value = false }
}
async function save() {
  if (!canSave.value) return
  saving.value = true; error.value = ''
  if (original.value.trim() && !text.value.trim()) {
    try { await ElMessageBox.confirm('將清空這個分組的歷次整理；行動、發現與卡片保留。', '清空歷次整理', { confirmButtonText: '確認清空', cancelButtonText: '返回編輯', type: 'warning' }) }
    catch { saving.value = false; return }
  }
  try {
    await api.updateHistory(props.experimentId, { threadId: props.threadId, methodId: props.methodId, text: text.value, expectedVersion: version.value })
  } catch (e) {
    const status = (e as { response?: { status?: number } }).response?.status
    uncertain.value = status == null || status >= 500
    conflict.value = status === 409 || status === 404 || status === 400
    error.value = apiErrorMessage(e, uncertain.value ? '儲存結果尚未確認，草稿已保留，請先確認結果。' : '儲存失敗，草稿已保留。')
    saving.value = false
    return
  }
  await finish()
  saving.value = false
}
</script>

<template>
  <AppDialog ref="shell" :model-value="modelValue" title="編輯歷次整理" pattern="large-form" scroll-body :busy="saving" :draft="text" :draft-ready="loaded" :draft-baseline="baseline" @opened="field?.focus()" @update:model-value="emit('update:modelValue', $event)">
    <p class="app-dialog-intro history-context">{{ label }}</p>
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-input v-else-if="loaded" ref="field" v-model="text" type="textarea" :rows="14" :disabled="saving || uncertain" aria-label="歷次整理全文" placeholder="輸入或貼上歷次整理文字" />
    <el-alert v-if="error" class="history-error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button :disabled="saving" @click="requestClose">取消</el-button>
      <el-button v-if="error" :disabled="saving || loading" @click="uncertain ? verify() : reload()">{{ uncertain ? '確認儲存結果' : '重新載入' }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">儲存</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.history-context { overflow-wrap: anywhere; white-space: pre-wrap; }
.history-error { margin-top: var(--space-sm); }
</style>
