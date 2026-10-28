<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
import WorkFormDialog from './WorkFormDialog.vue'
import WorkResumeEntry from './WorkResumeEntry.vue'
import { Plus } from '@element-plus/icons-vue'
import SectionHeader from '../ui/SectionHeader.vue'
const props = defineProps<{ experimentId?: number; compact?: boolean }>()
const router = useRouter(), creating = ref(false)
const params = computed(() => ({ experimentId: props.experimentId, size: 3 }))
const query = useQuery({ queryKey: ['works', 'resume', params], queryFn: () => workApi.list(params.value) })
</script>
<template>
  <section class="resume-work" :class="{ 'resume-work--compact': compact }">
    <SectionHeader eyebrow="創作台" :title="experimentId ? '正在製作' : '接著創作'" :count="compact ? query.data.value?.totalElements : undefined"><template #actions><el-button :icon="Plus" :text="compact" :type="compact ? 'primary' : undefined" @click="creating = true">建立作品</el-button><router-link class="resume-all" :to="{ path: '/works', query: experimentId ? { experimentId } : {} }">{{ compact ? '查看全部' : '查看全部作品' }}</router-link></template></SectionHeader>
    <p v-if="query.isPending.value">正在載入作品…</p>
    <el-alert v-else-if="query.isError.value" title="無法載入作品" type="error" :closable="false"><el-button @click="query.refetch()">重新載入</el-button></el-alert>
    <div v-else-if="query.data.value?.content.length" class="resume-list" :class="{ 'resume-list--experiment': experimentId }"><WorkResumeEntry v-for="w in query.data.value.content" :key="w.id" :work="w" :compact="compact" /></div>
    <p v-else-if="compact" class="resume-note">{{ experimentId ? '此實驗目前沒有未封存的作品。' : '目前沒有未封存的作品。' }}可以先建立一件作品，留下想接著做的事。</p>
    <p class="resume-note" v-if="experimentId && (query.data.value?.totalElements ?? 0) > 3">顯示最近編輯的 3 件作品，其餘可在創作台查看。</p>
    <WorkFormDialog v-if="creating" v-model="creating" :experiment-id="experimentId" @saved="router.push(`/works/${$event.work.id}`)" />
  </section>
</template>
<style scoped>
.resume-work { padding: var(--panel-padding); min-width: 0; background: var(--el-bg-color); border: 1px solid var(--el-border-color-light); border-radius: var(--panel-radius); }
.resume-work :deep(.app-section-actions) { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-sm); }
.resume-work :deep(.app-section-actions .el-button) { margin: 0; }
.resume-list { display: grid; grid-template-columns:repeat(3, minmax(0, 1fr)); gap: var(--space-md); margin-top: var(--space-md); }
.resume-all { color: var(--el-color-primary); font-size: var(--type-ui-label); line-height: var(--leading-ui); text-decoration: none; }
.resume-all:hover { text-decoration: underline; }
.resume-all:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.resume-work--compact .resume-all { font-size: var(--type-meta); }
.resume-work--compact .resume-list { gap: 12px; }
.resume-work--compact :deep(.app-section-actions .el-button) { padding-inline: var(--space-xs); font-size: var(--type-meta); }
.resume-note { margin-top: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
@media (max-width: 1200px) { .resume-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
.resume-work--compact .resume-list { grid-template-columns: repeat(3, minmax(0, 1fr)); }
@media (max-width: 900px) { .resume-work--compact .resume-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) {
  .resume-list { grid-template-columns: minmax(0, 1fr); gap: 0; margin-inline: calc(var(--panel-padding) * -1); }
  .resume-list :deep(.resume-entry) { border: 0; border-top: 1px solid var(--el-border-color-light); border-radius: 0; min-height: 0; }
  .resume-work--compact .resume-list { grid-template-columns: minmax(0, 1fr); margin-inline: 0; gap: 8px; }
  .resume-work--compact .resume-list :deep(.resume-entry) { border: 1px solid var(--el-border-color-lighter); border-radius: var(--radius-md); }
}
</style>
