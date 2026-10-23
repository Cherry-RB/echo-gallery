<script lang="ts">
export interface SourceReadingItem {
  id: number | null
  cardId: number | null
  recordId: number | null
  title: string
  text: string
  tryText?: string | null
  discovery?: string | null
  experimentId: number | null
  experimentTitle: string | null
  archived: boolean
}
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import AppDialog from './AppDialog.vue'
import HistoryTimeline from './ui/HistoryTimeline.vue'
import HistoryEvent from './ui/HistoryEvent.vue'
import HistoryFields from './ui/HistoryFields.vue'
import { cardApi } from '../utils/api/cardApi'
import { cardTextFieldCopy } from '../utils/cardTextFieldCopy'

const props = withDefaults(defineProps<{
  modelValue: boolean
  source: SourceReadingItem
  closeLabel?: string
}>(), { closeLabel: '關閉' })
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const router = useRouter()
const card = useQuery({
  queryKey: computed(() => ['card', 'source-reading', props.source.cardId]),
  queryFn: () => cardApi.getCard(props.source.cardId!),
  enabled: computed(() => !!props.source.cardId && props.modelValue),
})
const recordParts = computed(() => ({ tryText: props.source.tryText, discovery: props.source.discovery }))
const copyText = computed(() => {
  if (!props.source.cardId || !card.data.value) return props.source.text
  return [card.data.value.url, card.data.value.cardNote, card.data.value.content]
    .filter((part): part is string => !!part?.trim())
    .join('\n\n')
})
async function copy() {
  try { await navigator.clipboard.writeText(copyText.value); ElMessage.success('已複製') }
  catch { ElMessage.error('無法存取剪貼簿，請手動複製。') }
}
function openOriginal() {
  const path = props.source.cardId ? `/card/${props.source.cardId}` : `/experiments/${props.source.experimentId}`
  emit('update:modelValue', false)
  void router.push(props.source.cardId ? path : { path, query: { history: '1' } })
}
</script>

<template>
  <AppDialog :model-value="modelValue" title="來源原文" pattern="workspace" width="min(820px, calc(100vw - 32px))" scroll-body @update:model-value="emit('update:modelValue', $event)">
    <article v-if="source.cardId" class="source-reading">
      <header class="source-reading-heading">
        <p class="source-reading-eyebrow">卡片</p>
        <h2>{{ card.data.value?.title || source.title }}</h2>
        <div v-if="card.data.value?.tags?.length" class="source-reading-tags"><el-tag v-for="tag in card.data.value.tags" :key="tag" size="small" effect="plain">{{ tag }}</el-tag></div>
      </header>
      <p v-if="card.isPending.value" class="source-reading-status">正在載入卡片內容…</p>
      <el-alert v-else-if="card.isError.value" title="無法載入這張卡片" type="error" :closable="false"><el-button link type="primary" @click="card.refetch()">重新載入</el-button></el-alert>
      <template v-else>
        <section v-if="card.data.value?.cardNote" class="source-reading-section"><h3>{{ cardTextFieldCopy.cardNote.label }}</h3><p>{{ card.data.value.cardNote }}</p></section>
        <section v-if="card.data.value?.content" class="source-reading-section"><h3>{{ cardTextFieldCopy.content.label }}</h3><p>{{ card.data.value.content }}</p></section>
        <section v-if="card.data.value?.url" class="source-reading-section"><h3>來源連結</h3><a :href="card.data.value.url" target="_blank" rel="noopener noreferrer">{{ card.data.value.url }}</a></section>
        <p v-if="!card.data.value?.cardNote && !card.data.value?.content && !card.data.value?.url" class="source-reading-empty">這張卡片尚未填寫可閱讀的內容。</p>
      </template>
    </article>
    <article v-else class="source-reading">
      <header class="source-reading-heading"><p class="source-reading-eyebrow">探索紀錄</p><h2>{{ source.title }}</h2><p v-if="source.experimentTitle" class="source-reading-meta">{{ source.experimentTitle }}{{ source.archived ? ' · 已封存' : '' }}</p></header>
      <HistoryTimeline><HistoryEvent :date="source.title" :show-timeline="false"><HistoryFields left-label="試法" right-label="發現"><template #left><p>{{ recordParts.tryText || '這筆紀錄沒有填寫試法。' }}</p></template><template #right><p>{{ recordParts.discovery || '這筆紀錄沒有填寫發現。' }}</p></template></HistoryFields></HistoryEvent></HistoryTimeline>
    </article>
    <template #footer="{ requestClose }"><el-button @click="requestClose">{{ closeLabel }}</el-button><el-button :disabled="!!source.cardId && !card.data.value" @click="copy">複製內容</el-button><el-button type="primary" plain @click="openOriginal">{{ source.cardId ? '查看原卡片' : '查看探索紀錄' }}</el-button></template>
  </AppDialog>
</template>

<style scoped>
.source-reading { width: min(100%, var(--reading-max-width)); margin: 0 auto; }
.source-reading-heading { padding-bottom: var(--space-lg); border-bottom: 1px solid var(--el-border-color-lighter); }
.source-reading-heading h2 { margin: var(--space-2xs) 0 0; overflow-wrap: anywhere; font-size: var(--type-section-title); line-height: var(--leading-section-title); }
.source-reading-eyebrow, .source-reading-meta { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-meta); line-height: var(--leading-metadata); }
.source-reading-meta { margin-top: var(--space-xs); }
.source-reading-tags { display: flex; flex-wrap: wrap; gap: var(--space-xs); margin-top: var(--space-md); }
.source-reading-status, .source-reading-empty { margin: var(--space-lg) 0 0; color: var(--el-text-color-secondary); font-size: var(--type-body); line-height: var(--leading-body); }
.source-reading-section { padding-block: var(--space-lg); border-bottom: 1px solid var(--el-border-color-lighter); }
.source-reading-section h3 { margin: 0; font-size: var(--type-card-title); line-height: var(--leading-ui); }
.source-reading-section p { margin: var(--space-sm) 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: var(--type-body); line-height: var(--leading-body); }
.source-reading-section a { display: inline-block; max-width: 100%; margin-top: var(--space-sm); overflow-wrap: anywhere; color: var(--el-color-primary); font-size: var(--type-ui); line-height: var(--leading-ui); }
</style>
