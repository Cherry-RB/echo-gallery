<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import type { WorkProgressUpdate, WorkProgressUpdateRequest } from '../../types/work'
import { workApi } from '../../utils/api/workApi'
import AppDialog from '../AppDialog.vue'
import {
  hasWorkProgressUpdateContent,
  normalizeWorkProgressUpdate,
} from '../../utils/workProgressUpdate'

const props = withDefaults(defineProps<{
  modelValue: boolean
  workId: string | number
  update?: WorkProgressUpdate | null
}>(), {
  update: null,
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [update: WorkProgressUpdate]
  closed: []
}>()

const queryClient = useQueryClient()
const formError = ref('')
const form = reactive<WorkProgressUpdateRequest>({
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
  mutationFn: (payload: WorkProgressUpdateRequest) => props.update
    ? workApi.updateWorkUpdate(props.workId, props.update.id, payload)
    : workApi.createWorkUpdate(props.workId, payload),
  onSuccess: async (savedUpdate) => {
    emit('update:modelValue', false)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['work-progress-updates', String(props.workId)] }),
      queryClient.invalidateQueries({ queryKey: ['recent-work-progress-updates'] }),
      queryClient.invalidateQueries({ queryKey: ['works'] }),
    ])
    emit('saved', savedUpdate)
    ElMessage.success(props.update ? '議題近況已修改' : '近況已提出')
  },
  onError: () => {
    ElMessage.error('儲存議題近況失敗，請稍後再試')
  },
})

const submitUpdate = () => {
  const payload = normalizeWorkProgressUpdate(form)
  if (!hasWorkProgressUpdateContent(payload)) {
    formError.value = '至少寫下一項，才算一次議題近況。'
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
    :title="update ? '修改議題近況' : '提出近況'"
    width="min(640px, calc(100vw - 32px))"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <p class="dialog-intro">不必寫成完整報告。只要記下相較上一次，現在有什麼不同。</p>

    <label class="update-field">
      <span>最近有什麼改變？</span>
      <small>新事件、現實回饋、結果，或資源與限制的變化。</small>
      <el-input v-model="form.changeSummary" type="textarea" :rows="3" maxlength="50000" />
    </label>

    <label class="update-field">
      <span>現在怎麼看？</span>
      <small>這些變化意味著什麼？原本的判斷是否需要修正？</small>
      <el-input v-model="form.assessment" type="textarea" :rows="3" maxlength="50000" />
    </label>

    <label class="update-field">
      <span>所以接下來呢？</span>
      <small>只留下目前最值得推進的一個方向，不需要拆成工作清單。</small>
      <el-input v-model="form.nextStep" type="textarea" :rows="2" maxlength="50000" />
    </label>

    <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>

    <template #footer>
      <el-button :disabled="saveMutation.isPending.value" @click="emit('update:modelValue', false)">
        取消
      </el-button>
      <el-button type="primary" :loading="saveMutation.isPending.value" @click="submitUpdate">
        {{ update ? '儲存修改' : '提出近況' }}
      </el-button>
    </template>
  </AppDialog>
</template>

<style scoped>
.dialog-intro,
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
