<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { IssueUpdate, IssueUpdateRequest } from '../../types/issue'
import { issueApi } from '../../utils/api/issueApi'
import AppDialog from '../AppDialog.vue'
import {
  hasIssueUpdateContent,
  normalizeIssueUpdate,
} from '../../utils/issueUpdate'

const props = withDefaults(defineProps<{
  modelValue: boolean
  issueId: string | number
  update?: IssueUpdate | null
}>(), {
  update: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [update: IssueUpdate]
  closed: []
}>()

const queryClient = useQueryClient()
const formError = ref('')
const form = reactive<IssueUpdateRequest>({
  changeSummary: '',
  assessment: '',
  nextStep: '',
})

const resetForm = () => {
  form.changeSummary = props.update?.changeSummary ?? ''
  form.assessment = props.update?.assessment ?? ''
  form.nextStep = props.update?.nextStep ?? ''
  formError.value = ''
}

watch(
  () => [props.modelValue, props.update] as const,
  ([visible]) => {
    if (visible) resetForm()
  },
  { immediate: true },
)

const saveMutation = useMutation({
  mutationFn: (payload: IssueUpdateRequest) => props.update
    ? issueApi.updateIssueUpdate(props.issueId, props.update.id, payload)
    : issueApi.createIssueUpdate(props.issueId, payload),
  onSuccess: async (savedUpdate) => {
    emit('update:modelValue', false)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['issue-progress-updates', String(props.issueId)] }),
      queryClient.invalidateQueries({ queryKey: ['recent-issue-progress-updates'] }),
      queryClient.invalidateQueries({ queryKey: ['issues'] }),
    ])
    emit('saved', savedUpdate)
    ElMessage.success(props.update ? '系統訊號已修改' : '系統訊號已記錄')
  },
  onError: () => {
    ElMessage.error('儲存系統訊號失敗，請稍後再試')
  },
})

const submitUpdate = () => {
  const payload = normalizeIssueUpdate(form)
  if (!hasIssueUpdateContent(payload)) {
    formError.value = '至少寫下一項，才算一次系統訊號。'
    return
  }
  formError.value = ''
  saveMutation.mutate(payload)
}

const handleClosed = () => {
  resetForm()
  emit('closed')
}
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    :title="update ? '修改系統訊號' : '記錄系統訊號'"
    width="min(640px, calc(100vw - 32px))"
    scroll-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <p class="dialog-intro app-dialog-intro">事件不等於結論。只記錄新回饋、模型是否改變，以及現在要介入或等待。</p>

    <label class="update-field">
      <span>出現了什麼新訊號？</span>
      <small>新事件、結果、現實回饋，或關鍵資源／限制的變化。只記和上一次相比不同的地方。</small>
      <el-input v-model="form.changeSummary" type="textarea" :rows="3" maxlength="50000" />
    </label>

    <label class="update-field">
      <span>這讓系統模型怎麼變？</span>
      <small>哪個判斷被支持或挑戰？若模型沒有改變，也可以明確寫「目前不改」。</small>
      <el-input v-model="form.assessment" type="textarea" :rows="3" maxlength="50000" />
    </label>

    <label class="update-field">
      <span>現在要介入，還是等待？</span>
      <small>只留下最重要的介入方向；若樣本不足或存在延遲，也可以選擇等待某個訊號。</small>
      <el-input v-model="form.nextStep" type="textarea" :rows="2" maxlength="50000" />
    </label>

    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

    <template #footer>
      <el-button :disabled="saveMutation.isPending.value" @click="emit('update:modelValue', false)">
        取消
      </el-button>
      <el-button type="primary" :loading="saveMutation.isPending.value" @click="submitUpdate">
        {{ update ? '儲存修改' : '記錄訊號' }}
      </el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.update-field small {
  margin: 0;
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.update-field {
  display: block;
  margin-top: 22px;
}

.update-field > span {
  display: block;
  margin-bottom: 3px;
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.update-field small {
  display: block;
  margin-bottom: 8px;
  font-size: var(--type-meta);
}

.form-error {
  margin: 14px 0 0;
  color: var(--el-color-danger);
  font-size: var(--type-caption);
}
</style>
