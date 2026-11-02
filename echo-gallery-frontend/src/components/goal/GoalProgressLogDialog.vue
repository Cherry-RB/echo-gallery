<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalLogKind, GoalProgressLog } from '../../types/goal'

const props = defineProps<{ modelValue: boolean; goalId: number; log?: GoalProgressLog | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const kind = ref<GoalLogKind>('NOTE'), occurredOn = ref(''), content = ref(''), amount = ref<number | null>(null), unit = ref('')
const saving = ref(false), error = ref('')
const today = () => new Date().toLocaleDateString('en-CA', { timeZone: 'Asia/Taipei' })

watch(visible, open => {
  if (!open) return
  kind.value = props.log?.kind || 'NOTE'; occurredOn.value = props.log?.occurredOn || today()
  content.value = props.log?.content || ''; amount.value = props.log?.amount || null; unit.value = props.log?.unit || ''; error.value = ''
})

async function save() {
  if (saving.value) return
  if (!occurredOn.value || (!content.value.trim() && amount.value == null) || (kind.value === 'MEASUREMENT' && (amount.value == null || !unit.value.trim()))) {
    error.value = kind.value === 'MEASUREMENT' ? '請填寫日期、數值與單位；說明可選填。' : '請至少填寫日期與一則補記內容。'
    return
  }
  saving.value = true; error.value = ''
  try {
    await goalApi.saveLog(props.goalId, { id: props.log?.id, kind: kind.value, occurredOn: occurredOn.value, content: content.value.trim() || null, amount: amount.value, unit: unit.value.trim() || null, version: props.log?.version || 0 })
    ElMessage.success(props.log ? '已更新補記' : '已加入補記'); emit('saved'); visible.value = false
  } catch { error.value = '儲存補記失敗，請重新整理後再試。' }
  finally { saving.value = false }
}
</script>

<template>
  <AppDialog v-model="visible" :title="log ? '編輯補記' : '新增補記'" width="min(520px, calc(100vw - 32px))" :busy="saving">
    <el-form label-position="top" @submit.prevent="save">
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <div class="log-fields">
        <el-form-item label="類型"><el-select v-model="kind"><el-option label="文字補記" value="NOTE" /><el-option label="數值紀錄" value="MEASUREMENT" /></el-select></el-form-item>
        <el-form-item label="日期" required><el-date-picker v-model="occurredOn" type="date" value-format="YYYY-MM-DD" /></el-form-item>
      </div>
      <div v-if="kind === 'MEASUREMENT'" class="log-fields">
        <el-form-item label="數值" required><el-input-number v-model="amount" :min="0.01" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="單位" required><el-input v-model="unit" maxlength="80" placeholder="例如：次、分鐘、篇" /></el-form-item>
      </div>
      <el-form-item :label="kind === 'MEASUREMENT' ? '說明（選填）' : '補記內容'" :required="kind === 'NOTE'"><el-input v-model="content" type="textarea" :rows="4" maxlength="5000" show-word-limit placeholder="記下站外活動、進度或觀察" /></el-form-item>
    </el-form>
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="save">儲存補記</el-button></template>
  </AppDialog>
</template>

<style scoped>
.log-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: var(--space-md); }
.log-fields :deep(.el-form-item) { margin-bottom: var(--space-md); }
.log-fields :deep(.el-date-editor), .log-fields :deep(.el-input-number) { width: 100%; }
.el-alert { margin-bottom: var(--space-md); }
@media (max-width: 600px) { .log-fields { grid-template-columns: 1fr; gap: 0; } }
</style>
