<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import AppDialog from '../AppDialog.vue'
import WorkInfoFields, { WORK_FORM_WIDTH } from './WorkInfoFields.vue'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workApi } from '../../utils/api/workApi'
import type { WorkDetail, WorkInfo } from '../../types/work'
const props = defineProps<{ modelValue: boolean; work?: WorkDetail; experimentId?: number; source?: { cardId?: number; recordId?: number } }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [WorkDetail] }>()
const form = reactive<WorkInfo>({ title: '', description: '', experimentId: null, editorMode: 'INTERNAL', externalUrl: '' })
const saving = ref(false), error = ref('')
const shell = ref<InstanceType<typeof AppDialog>>()
useWorkDialogGuard(shell, () => props.modelValue)
const client = useQueryClient()
const experiments = useQuery({ queryKey: ['work-experiments'], queryFn: workApi.experiments })
watch(() => props.modelValue, visible => {
  if (!visible) return
  const w = props.work
  Object.assign(form, w ? { title: w.work.title, description: w.work.description, experimentId: w.work.experimentId, editorMode: w.work.editorMode, externalUrl: w.externalUrl } : { title: '', description: '', experimentId: props.experimentId ?? null, editorMode: 'INTERNAL', externalUrl: '' }); error.value = ''
}, { immediate: true })
async function save() {
  if (saving.value) return
  if (!form.title.trim()) { error.value = '請輸入作品名稱'; return }
  if (form.externalUrl?.trim()) {
    try { const url = new URL(form.externalUrl); if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error() }
    catch { error.value = '請輸入有效的 HTTP(S) 網址'; return }
  }
  saving.value = true; error.value = ''
  try {
    const result = props.work ? await workApi.info(props.work.work.id, form) : await workApi.create({ ...form, ...(props.source ? { sources: [props.source] } : {}) })
    await client.invalidateQueries({ queryKey: ['works'] })
    emit('saved', result); emit('update:modelValue', false)
  } catch { error.value = '無法儲存作品，請重試。表單內容已保留。' } finally { saving.value = false }
}
</script>
<template>
  <AppDialog ref="shell" :model-value="modelValue" :title="work ? '編輯作品資訊' : '建立作品'" pattern="form" :width="WORK_FORM_WIDTH" :draft="form" :busy="saving" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <el-form label-position="top" :disabled="saving" @submit.prevent="save">
      <WorkInfoFields :model-value="form" :experiments="experiments.data.value" :original-mode="work?.work.editorMode" />
      <el-alert v-if="source" title="原始素材會加入參考來源，不會搬移或貼入正文。" :closable="false" />
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">{{ work ? '儲存變更' : '建立並開始' }}</el-button></template>
  </AppDialog>
</template>
