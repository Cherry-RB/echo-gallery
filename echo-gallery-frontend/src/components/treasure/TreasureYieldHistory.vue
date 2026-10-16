<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessageBox } from 'element-plus'
import { treasureApi } from '../../utils/api/treasureApi'
import type { TreasureYield } from '../../types/treasure'
import { errorMessage, yieldHistoryDescription } from '../../utils/treasure'
import TreasureYieldDialog from './TreasureYieldDialog.vue'
import ExpandableText from '../ExpandableText.vue'
import AppEmptyState from '../ui/AppEmptyState.vue'
import HistoryTimeline from '../ui/HistoryTimeline.vue'
import HistoryEvent from '../ui/HistoryEvent.vue'
import HistoryDialog from '../ui/HistoryDialog.vue'
import RecordHistoryTrigger from '../ui/RecordHistoryTrigger.vue'
const props = defineProps<{ treasureId: number; archived?: boolean; compact?: boolean; embedded?: boolean }>()
const client = useQueryClient()
const page = ref(0)
const visible = ref(false)
const editing = ref<TreasureYield>()
const error = ref('')
const busy = ref(false)
const historyVisible = ref(false)
watch(() => props.treasureId, () => { page.value = 0; visible.value = false; historyVisible.value = false })
const query = useQuery({ queryKey: computed(() => ['treasures', props.treasureId, 'yields', page.value]), queryFn: () => treasureApi.yields(props.treasureId, page.value) })
const previewLimit = 3
const hasMore = computed(() => props.compact && (query.data.value?.totalElements ?? 0) > previewLimit)
const open = (item?: TreasureYield) => { editing.value = item; visible.value = true }
defineExpose({ open })
const command = async (value: string, item: TreasureYield) => {
  if (props.archived || busy.value) return
  if (value === 'edit') { open(item); return }
  if (value !== 'delete') return
  try { await ElMessageBox.confirm('刪除這筆價值產出？相關卡片仍會保留。', '刪除紀錄', { confirmButtonText: '刪除', cancelButtonText: '取消', type: 'warning' }) } catch { return }
  busy.value = true; error.value = ''
  try {
    await treasureApi.deleteYield(props.treasureId, item.id)
    if (query.data.value?.content.length === 1 && page.value > 0) page.value--
    await Promise.all([client.invalidateQueries({ queryKey: ['treasures'] }), client.invalidateQueries({ queryKey: ['overview'] })])
  } catch (e) { error.value = errorMessage(e) } finally { busy.value = false }
}
</script>
<template>
  <section class="yield-history" aria-label="價值產出歷程">
    <header v-if="!embedded" class="yield-heading">
      <h2 v-if="compact">價值產出<span v-if="hasMore" class="preview-range">・最近 {{ previewLimit }} 筆</span></h2>
      <div class="yield-actions"><RecordHistoryTrigger v-if="compact && query.data.value?.totalElements" label="產出紀錄" :count="query.data.value.totalElements" @click="historyVisible = true" /><button type="button" class="history-trigger" @click="open()">＋產出</button></div>
      <p class="app-dialog-intro">{{ yieldHistoryDescription }}</p>
    </header>
    <p v-if="query.isLoading.value">正在讀取…</p>
    <el-button v-else-if="query.isError.value" @click="query.refetch()">讀取失敗，重試</el-button>
    <template v-else>
      <HistoryTimeline>
        <HistoryEvent v-for="(item, index) in (compact ? query.data.value?.content.slice(0, previewLimit) : query.data.value?.content) ?? []" :key="item.id" :date="item.occurredOn" date-format="YYYY/MM/DD" date-prefix="發生於 " :latest="index === 0 && page === 0" :manageable="!archived" :busy="busy" :operation-label="item.title + '的操作'" @command="command($event, item)">
            <h3 :class="{ 'summary-title': compact }" :title="compact ? item.title : undefined">{{ item.title }}</h3>
            <section v-if="item.description" class="yield-description"><span>過程與帶來的價值</span><p v-if="compact" class="summary-copy">{{ item.description }}</p><ExpandableText v-else :content="item.description" :lines="6" /></section>
            <router-link v-if="item.relatedCard" class="related-card" :to="'/card/' + item.relatedCard.id">相關卡片 · {{ item.relatedCard.title }}</router-link>
        </HistoryEvent>
      </HistoryTimeline>
      <AppEmptyState v-if="!query.data.value?.content.length" description="尚無價值產出。當成果帶來新作品、回應或機會時，再留下紀錄。" />
    </template>
    <el-pagination v-if="!compact && (query.data.value?.totalElements ?? 0) > 10" :current-page="page + 1" :page-size="10" :total="query.data.value?.totalElements ?? 0" layout="prev, pager, next" @current-change="page = $event - 1" />
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <TreasureYieldDialog v-if="visible" v-model="visible" :treasure-id="treasureId" :yield="editing" />
    <HistoryDialog v-if="historyVisible" v-model="historyVisible" title="價值產出歷程" :description="yieldHistoryDescription">
      <template #actions><button type="button" class="history-trigger" @click="open()">＋產出</button></template>
      <TreasureYieldHistory :treasure-id="treasureId" :archived="archived" embedded />
      <template #footer><el-button @click="historyVisible = false">關閉</el-button></template>
    </HistoryDialog>
  </section>
</template>
<style scoped>
.yield-heading { display: flex; align-items: flex-start; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); margin-bottom: var(--space-md); }
.yield-heading h2 { margin: 0; font-size: var(--type-section-title); }
.preview-range { color: var(--el-text-color-secondary); font-size: var(--type-ui); font-weight: var(--weight-regular); white-space: nowrap; }
.yield-heading .app-dialog-intro { flex-basis: 100%; margin: 0; }
.yield-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-sm); margin-left: auto; }
.history-trigger { display: inline-flex; align-items: center; gap: var(--space-xs); padding: 0; border: 0; background: transparent; color: var(--el-color-primary); font: inherit; font-size: var(--type-caption); cursor: pointer; }
.history-trigger:hover { text-decoration: underline; }
.history-trigger:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
h3 { margin: var(--space-sm) 0; font-size: var(--type-card-title); }
.summary-title, .summary-copy { display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.summary-copy { margin: var(--space-xs) 0 0; font-size: var(--type-ui); line-height: var(--leading-ui); white-space: pre-line; }
.yield-description > span { color: var(--el-text-color-secondary); font-size: var(--type-meta); }
.related-card { display: block; margin-top: var(--space-sm); color: var(--el-color-primary); font-size: var(--type-ui); overflow-wrap: anywhere; }
</style>
