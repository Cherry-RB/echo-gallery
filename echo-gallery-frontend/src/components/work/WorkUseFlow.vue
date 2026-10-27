<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import AppDialog from '../AppDialog.vue'
import WorkInfoFields, { WORK_FORM_WIDTH } from './WorkInfoFields.vue'
import PickerOption from '../ui/PickerOption.vue'
import { workStages } from '../../types/work'
import { useWorkDialogGuard } from '../../composables/useWorkDialogGuard'
import { workUseTarget } from '../../utils/workFlow'
import { workApi } from '../../utils/api/workApi'
import type { WorkInfo } from '../../types/work'
import { apiErrorMessage } from '../../utils/api/errorMessage'

const router = useRouter(), client = useQueryClient()
const shell = ref<InstanceType<typeof AppDialog>>()
const mode = ref<'new' | 'existing'>('new'), keyword = ref(''), page = ref(1)
const selectedWorkId = ref<number>(), busy = ref(false), error = ref(''), draftBaseline = ref<string>()
const form = reactive<WorkInfo>({ title: '', description: '', editorMode: 'INTERNAL', experimentId: null, externalUrl: '' })
const cardIds = computed(() => [...new Set(workUseTarget.value?.cardIds ?? [])])
const params = computed(() => ({ keyword: keyword.value, page: page.value - 1 }))
const works = useQuery({
  queryKey: computed(() => ['works', 'source-targets', params.value]),
  queryFn: () => workApi.list(params.value),
  enabled: computed(() => !!workUseTarget.value && mode.value === 'existing'),
})
useWorkDialogGuard(shell, () => !!workUseTarget.value)
onBeforeUnmount(() => { workUseTarget.value = undefined })
watch(workUseTarget, target => {
  if (!target) return
  mode.value = 'new'; keyword.value = ''; page.value = 1; selectedWorkId.value = undefined
  error.value = target.recordIds?.length ? '探索紀錄須先整理成卡片，才能用於作品。' : ''
  Object.assign(form, { title: '', description: '', editorMode: 'INTERNAL', experimentId: target.experimentId ?? null, externalUrl: '' })
  draftBaseline.value = JSON.stringify([mode.value, form, selectedWorkId.value])
}, { immediate: true })
watch(keyword, () => { page.value = 1 })
async function save() {
  if (busy.value || !cardIds.value.length || workUseTarget.value?.recordIds?.length) return
  if (mode.value === 'new' && !form.title.trim()) { error.value = '請填寫作品名稱。'; return }
  if (mode.value === 'existing' && !selectedWorkId.value) { error.value = '請選擇要加入的作品。'; return }
  busy.value = true; error.value = ''
  try {
    const sources = cardIds.value.map(cardId => ({ cardId }))
    const id = mode.value === 'new' ? (await workApi.create({ ...form, sources })).work.id : selectedWorkId.value!
    if (mode.value === 'existing') await workApi.addSources(id, sources)
    await client.invalidateQueries({ queryKey: ['works'] })
    workUseTarget.value = undefined
    void router.push('/works/' + id)
  } catch (e) { error.value = apiErrorMessage(e, '無法加入作品，選擇與草稿已保留。') }
  finally { busy.value = false }
}
</script>
<template>
  <AppDialog ref="shell" :model-value="!!workUseTarget" title="用於作品" :width="mode === 'new' ? WORK_FORM_WIDTH : 'min(560px, calc(100vw - 32px))'" :draft="[mode, form, selectedWorkId]" :draft-baseline="draftBaseline" :busy="busy" scroll-body @update:model-value="workUseTarget = undefined">
    <p class="app-dialog-intro">來源卡片：{{ workUseTarget?.cardTitle || (cardIds.length === 1 ? '#' + cardIds[0] : cardIds.length + ' 張卡片') }}。卡片會加入參考素材，正文可獨立撰寫。</p>
    <el-radio-group v-model="mode" :disabled="busy"><el-radio-button value="new">建立新作品</el-radio-button><el-radio-button value="existing">加入既有作品</el-radio-button></el-radio-group>
    <WorkInfoFields v-if="mode === 'new'" v-model="form" :disabled="busy" />
    <section v-else class="existing-picker work-options">
      <el-input v-model="keyword" :disabled="busy" clearable placeholder="搜尋作品名稱" aria-label="搜尋作品名稱" />
      <el-skeleton v-if="works.isPending.value" :rows="3" animated />
      <el-alert v-else-if="works.isError.value" title="無法載入作品" type="error" :closable="false"><el-button type="primary" link @click="works.refetch()">重新載入</el-button></el-alert>
      <p v-else-if="!works.data.value?.content.length">找不到符合條件的作品。</p>
      <PickerOption v-for="work in works.data.value?.content ?? []" :key="work.id" :title="work.title" :selected="selectedWorkId === work.id" :disabled="busy" @select="selectedWorkId = work.id">{{ [work.experimentTitle, workStages[work.stage]].filter(Boolean).join(' · ') }}</PickerOption>
      <el-pagination v-if="(works.data.value?.totalPages ?? 0) > 1" v-model:current-page="page" :disabled="busy" :page-size="20" :total="works.data.value?.totalElements ?? 0" layout="prev, pager, next" />
    </section>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template #footer="{ requestClose }"><el-button :disabled="busy" @click="requestClose">取消</el-button><el-button type="primary" :loading="busy" :disabled="!cardIds.length || !!workUseTarget?.recordIds?.length || (mode === 'existing' && !selectedWorkId)" @click="save">{{ mode === 'new' ? '建立並開始' : '加入作品' }}</el-button></template>
  </AppDialog>
</template>
<style scoped>.existing-picker { display: grid; gap: var(--space-sm); margin-top: var(--space-md); }</style>
