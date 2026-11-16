<script setup lang="ts">
import { ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from '../AppDialog.vue'
import TreasureSourcePicker from './TreasureSourcePicker.vue'
import type { TreasureDetail, TreasureReference } from '../../types/treasure'
import { treasureApi } from '../../utils/api/treasureApi'
import { errorMessage } from '../../utils/treasure'

const props = defineProps<{ modelValue: boolean; treasure: TreasureDetail }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const picker = ref<InstanceType<typeof TreasureSourcePicker>>()
const saving = ref(false)
const error = ref('')
const queryClient = useQueryClient()
const originalSources = { expectedCardIds: props.treasure.sourceCards.map(item => item.id), expectedExperimentId: props.treasure.sourceExperiment?.id ?? null, expectedTreasureIds: props.treasure.sourceTreasures.map(item => item.id) }
const save = async (value: { cards: TreasureReference[]; experiment: TreasureReference | null; treasures: TreasureReference[] }) => {
  if (saving.value) return
  saving.value = true; error.value = ''
  try {
    await treasureApi.replaceSources(props.treasure.id, { sourceCardIds: value.cards.map(item => item.id), sourceExperimentId: value.experiment?.id ?? null, sourceTreasureIds: value.treasures.map(item => item.id), ...originalSources })
    await Promise.all([queryClient.invalidateQueries({ queryKey: ['treasures'] }), queryClient.invalidateQueries({ queryKey: ['treasure-source-picker'] })])
    ElMessage.success('創作脈絡已更新。'); emit('update:modelValue', false)
  } catch (e) { error.value = errorMessage(e) } finally { saving.value = false }
}
</script>

<template>
  <AppDialog :busy="saving" :draft="picker?.draft" :draft-ready="!!picker" :model-value="modelValue" title="管理創作脈絡" width="min(980px, calc(100vw - 32px))" scroll-body :close-on-click-modal="!saving" :close-on-press-escape="!saving" @update:model-value="value => !saving && emit('update:modelValue', value)">
    <p class="app-dialog-intro">關聯卡片、實驗或其他展品，讓日後能回看這件展品如何形成。</p>
    <TreasureSourcePicker ref="picker" :treasure-id="treasure.id" :source-cards="treasure.sourceCards" :source-experiment="treasure.sourceExperiment" :source-treasures="treasure.sourceTreasures" @confirm="save" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button :disabled="saving" @click="requestClose">取消</el-button><el-button type="primary" :loading="saving" @click="picker?.confirm()">儲存創作脈絡</el-button></template>
  </AppDialog>
</template>
