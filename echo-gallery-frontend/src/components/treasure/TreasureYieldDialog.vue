<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import CardPickerDialog from '../CardPickerDialog.vue'
import SelectionSummary from '../ui/SelectionSummary.vue'
import FieldCounter from '../ui/FieldCounter.vue'
import type { CardDto } from '../../types/card'
import { treasureApi } from '../../utils/api/treasureApi'
import { errorMessage, today } from '../../utils/treasure'
import type { TreasureYield, YieldRequest, TreasureReference } from '../../types/treasure'

const props = defineProps<{ modelValue: boolean; treasureId: number; yield?: TreasureYield }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()
const form = reactive<YieldRequest>({ title: '', description: '', occurredOn: today(), relatedCardId: null })
const selectedCard = ref<TreasureReference | null>(null)
const pickerVisible = ref(false)
const selectCard = (card: CardDto) => { selectedCard.value = { id: Number(card.id), title: card.title }; pickerVisible.value = false }
const saving = ref(false)
const error = ref('')
const queryClient = useQueryClient()

watch(() => props.modelValue, visible => {
  if (!visible) return
  Object.assign(form, {
    title: props.yield?.title ?? '',
    description: props.yield?.description ?? '',
    occurredOn: props.yield?.occurredOn ?? today(),
    relatedCardId: props.yield?.relatedCard?.id ?? null,
  })
  selectedCard.value = props.yield?.relatedCard ?? null
  pickerVisible.value = false
  error.value = ''
}, { immediate: true })

const disabledDate = (date: Date) => date > new Date(`${today()}T23:59:59+08:00`)
const save = async () => {
  if (saving.value) return
  if (!form.title.trim() || !form.occurredOn) { error.value = '請填寫收益名稱與發生日期。'; return }
  saving.value = true
  error.value = ''
  try {
    const data = { ...form, relatedCardId: selectedCard.value?.id ?? null }
    if (props.yield) await treasureApi.updateYield(props.treasureId, props.yield.id, data)
    else await treasureApi.createYield(props.treasureId, data)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['treasures'] }),
      queryClient.invalidateQueries({ queryKey: ['overview'] }),
    ])
    emit('saved')
    ElMessage.success(props.yield ? '收益紀錄已更新。' : '昔日的成果並未沉寂。這一次，它又帶回了新的收穫。')
    emit('update:modelValue', false)
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
</script>

<template>
  <AppDialog :busy="saving" :draft="[form, selectedCard?.id]" :model-value="modelValue" :title="yield ? '編輯收益' : '記錄收益'" width="min(620px, calc(100vw - 32px))" scroll-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" @update:model-value="emit('update:modelValue', $event)">
    <el-form class="app-record-form" label-position="top" @submit.prevent="save">
      <p class="app-dialog-intro">記下這件奇珍已帶來的新作品、回應或機會，不限於金錢收入。整理、發表與分享的連續過程，可以記成同一筆。</p>
      <el-form-item label="收益名稱" required>
        <el-input v-model="form.title" maxlength="255" placeholder="例如：作品分享後，收到一位讀者的回饋" />
      </el-form-item>
      <el-form-item label="發生日期" required>
        <el-date-picker v-model="form.occurredOn" type="date" value-format="YYYY-MM-DD" :disabled-date="disabledDate" />
      </el-form-item>
      <el-form-item label="過程與帶來的價值（選填）">
        <el-input v-model="form.description" type="textarea" :rows="5" maxlength="50000" placeholder="做了哪些事？完成了什麼？帶來哪些理解、用途或回應？" />
        <FieldCounter :value="form.description" :limit="50000" />
        <p class="app-field-help">寫下做了什麼、如何分享，以及收到的回應；還沒有回應也可以記錄。</p>
      </el-form-item>
      <el-form-item class="related-card-field" label="相關卡片（選填）">
        <div class="related-card-control">
          <SelectionSummary v-if="selectedCard" :title="selectedCard.title" :metadata="'#' + selectedCard.id" label="相關卡片" />
          <div class="related-card-actions"><el-button :disabled="saving" @click="pickerVisible = true">{{ selectedCard ? '更換卡片' : '選擇卡片' }}</el-button><el-button v-if="selectedCard" text :disabled="saving" @click="selectedCard = null">移除關聯</el-button></div>
        </div>
        <p class="related-card-help">已有相關筆記或外部回應卡片時再關聯，不必另建卡片。</p>
      </el-form-item>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
    </el-form>
    <template #footer="{ requestClose }">
      <el-button :disabled="saving" @click="requestClose">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">儲存紀錄</el-button>
    </template>
  </AppDialog>
  <CardPickerDialog v-if="pickerVisible" v-model="pickerVisible" :initial-card-id="selectedCard?.id" title="關聯收益的卡片" description="選擇已有的相關筆記或外部回應；不會修改原卡片。" confirmation-hint="確認後帶回原表單，儲存收益紀錄後生效。" confirm-label="確認關聯" settings-style="plain" @confirm="selectCard" />
</template>

<style scoped>
.related-card-control { display: grid; gap: var(--space-sm); width: 100%; min-width: 0; }
.related-card-actions { display: flex; flex-wrap: wrap; gap: var(--space-xs); }
.related-card-actions :deep(.el-button) { margin-left: 0; }
.related-card-help { width: 100%; margin: var(--space-xs) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); }
</style>
