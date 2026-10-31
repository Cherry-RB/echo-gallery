<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import ExplorationThreadSelect from './ExplorationThreadSelect.vue'
import type { MethodOption } from '../../types/explorationActivity'
import { explorationActivityApi as api } from '../../utils/api/explorationActivityApi'
import { apiErrorMessage } from '../../utils/api/errorMessage'
import { useExplorationInvalidation } from '../../composables/useExplorationInvalidation'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'

const props = defineProps<{ modelValue: boolean; experimentId: number; threadId: number; methodId: number }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const invalidate = useExplorationInvalidation()
const method = ref<MethodOption>(), target = ref<number | null>(null), isCurrent = ref(false)
const loading = ref(false), saving = ref(false), uncertain = ref(false), conflict = ref(false), error = ref('')
const field = ref<InstanceType<typeof ExplorationThreadSelect>>()
let generation = 0
const canSave = computed(() => method.value && target.value != null && target.value !== method.value.threadId && !loading.value && !saving.value && !uncertain.value && !conflict.value)
async function load() {
  const current = ++generation
  loading.value = true
  try {
    const [latest, state] = await Promise.all([api.method(props.experimentId, props.methodId), api.state(props.experimentId)])
    if (current !== generation) return
    if (latest.threadId !== props.threadId) { method.value = undefined; error.value = '行動歸屬已改變，請關閉後重新開啟。'; return }
    method.value = latest; isCurrent.value = state.currentMethod?.id === latest.id
    target.value = null; uncertain.value = false; conflict.value = false; error.value = ''
    loading.value = false
    await nextTick()
    if (current === generation) field.value?.focus()
  } catch (e) { if (current === generation) error.value = apiErrorMessage(e, '無法載入行動，請重試。') }
  finally { if (current === generation) loading.value = false }
}
watch(() => [props.modelValue, props.experimentId, props.threadId, props.methodId] as const, ([visible]) => {
  if (visible) void load(); else generation++
}, { immediate: true })
onBeforeUnmount(() => generation++)
async function finish() {
  target.value = null
  try { await invalidate(props.experimentId) }
  catch { ElMessage.warning('行動已移動，畫面更新失敗，請重新載入。') }
  ElMessage.success('行動已移至所選探索線')
  emit('saved'); emit('update:modelValue', false)
}
async function verify() {
  if (saving.value || !method.value) return
  saving.value = true
  try {
    const latest = await api.method(props.experimentId, props.methodId)
    if (latest.threadId === target.value) await finish()
    else {
      uncertain.value = false; conflict.value = latest.version !== method.value.version
      error.value = conflict.value ? '行動已更新，請重新載入後確認歸屬。' : '尚未確認到這次移動，可以再次確認移動。'
    }
  } catch { error.value = '仍無法確認移動結果，請稍後再確認。' }
  finally { saving.value = false }
}
async function save() {
  if (!canSave.value || !method.value || target.value == null) return
  saving.value = true; error.value = ''
  try { await api.assignMethod(props.experimentId, method.value.id, target.value, method.value.version) }
  catch (e) {
    const status = (e as { response?: { status?: number } }).response?.status
    uncertain.value = status == null || status >= 500
    conflict.value = status === 409 || status === 404
    error.value = apiErrorMessage(e, uncertain.value ? '移動結果尚未確認，請先確認結果。' : '無法移動行動，選擇已保留。')
    saving.value = false; return
  }
  await finish()
  saving.value = false
}
</script>

<template>
  <AppDialog ref="shell" :model-value="modelValue" title="調整所屬探索線" pattern="compact-form" :busy="saving" :draft="target" :draft-baseline="JSON.stringify(null)" @opened="field?.focus()" @update:model-value="emit('update:modelValue', $event)">
    <el-skeleton v-if="loading" :rows="3" animated />
    <template v-else-if="method">
      <p class="method-content">{{ method.content }}</p>
      <p class="app-dialog-intro">目前探索線：{{ method.threadName }}</p>
      <el-form label-position="top"><el-form-item label="目標探索線" required>
        <ExplorationThreadSelect ref="field" v-model="target" :experiment-id="experimentId" :clearable="false" :disabled="saving || uncertain" placeholder="選擇目標探索線" />
      </el-form-item></el-form>
      <p class="app-dialog-intro">將連同 {{ method.discoveryCount }} 筆發現{{ method.hasHistory ? '與歷次整理' : '' }}一起移動，原始時間與常用標記保留。</p>
      <p v-if="isCurrent" class="app-dialog-intro">目前下一步的探索線也會同步調整。</p>
      <p v-if="target === method.threadId" class="app-dialog-intro">請選擇其他探索線。</p>
    </template>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }">
      <el-button :disabled="saving" @click="requestClose">取消</el-button>
      <el-button v-if="error" :disabled="saving || loading" @click="uncertain ? verify() : load()">{{ uncertain ? '確認移動結果' : '重新載入' }}</el-button>
      <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">確認移動</el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.method-content { white-space: pre-wrap; overflow-wrap: anywhere; }
</style>
