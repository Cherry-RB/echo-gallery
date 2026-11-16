<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import CoverImageField from '../ui/CoverImageField.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import { imageUrlError } from '../../utils/imageUrl'
import type { TreasureBasicContent, TreasureContent, TreasureDetail, TreasureReference } from '../../types/treasure'
import { treasureApi } from '../../utils/api/treasureApi'
import { contentOf, errorMessage, today } from '../../utils/treasure'

const props = defineProps<{ modelValue: boolean; treasure?: TreasureDetail; prefill?: Partial<TreasureContent>; sourceExperiment?: TreasureReference; createHandler?: (data: TreasureContent) => Promise<TreasureDetail> }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [TreasureDetail] }>()
const form = reactive(contentOf())
const saving = ref(false)
const error = ref('')
const queryClient = useQueryClient()
watch(() => props.modelValue, visible => {
  if (!visible) return
  Object.assign(form, contentOf(props.treasure))
  if (!props.treasure && props.prefill) Object.assign(form, props.prefill)
  error.value = ''
}, { immediate: true })
const disabledDate = (date: Date) => date > new Date(`${today()}T23:59:59+08:00`)
const close = (value: boolean) => { if (!saving.value) emit('update:modelValue', value) }
const save = async () => {
  if (saving.value) return
  if (!form.title.trim() || !form.acquiredOn) { error.value = '請填寫展品名稱與完成日期。'; return }
  if (imageUrlError(form.coverImageUrl)) { error.value = imageUrlError(form.coverImageUrl); return }
  saving.value = true; error.value = ''
  try {
    const basic: TreasureBasicContent = { title: form.title.trim(), description: form.description, artifactUrl: form.artifactUrl, coverImageUrl: form.coverImageUrl, acquiredOn: form.acquiredOn }
    const result = props.treasure ? await treasureApi.update(props.treasure.id, basic) : await (props.createHandler ?? treasureApi.create)({ ...basic, sourceCardIds: [], sourceExperimentId: props.sourceExperiment?.id ?? null, sourceTreasureIds: [] })
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['treasures'] }), queryClient.invalidateQueries({ queryKey: ['overview'] })])
    ElMessage.success(props.treasure ? '展品資料已儲存。' : '已建立展品。')
    emit('saved', result); emit('update:modelValue', false)
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
</script>

<template>
  <AppDialog :busy="saving" :draft="[form]" :model-value="modelValue" :title="treasure ? '編輯展品' : '建立展品'" width="min(680px, calc(100vw - 32px))" scroll-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" @update:model-value="close">
    <p class="app-dialog-intro">{{ treasure ? '調整這件展品的名稱、內容與呈現方式；作品與創作脈絡分別在詳情頁管理。' : '先建立展品；建立後可在詳情頁收錄作品與補充創作脈絡。' }}</p>
    <el-form label-position="top" :disabled="saving" @submit.prevent="save">
      <el-form-item label="展品名稱" required><el-input v-model="form.title" maxlength="255" /></el-form-item>
      <el-form-item label="內容摘要"><el-input v-model="form.description" type="textarea" :rows="6" maxlength="50000" /><FieldCounter :value="form.description" :limit="50000" /></el-form-item>
      <el-form-item label="完成日期" required><el-date-picker v-model="form.acquiredOn" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledDate" /></el-form-item>
      <details class="display-settings"><summary>呈現設定</summary><div class="display-fields"><el-form-item label="作品連結"><el-input v-model="form.artifactUrl" maxlength="2048" placeholder="https://" /></el-form-item><el-form-item label="封面圖片"><CoverImageField v-model="form.coverImageUrl" :disabled="saving" /></el-form-item></div></details>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">儲存變更</el-button></template>
  </AppDialog>
</template>

<style scoped>
.display-settings { margin-top: var(--space-lg); }
.display-settings summary { color: var(--el-color-primary); cursor: pointer; }
.display-fields { margin-top: var(--space-md); }
</style>
