<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
import WorkFormDialog from './WorkFormDialog.vue'
import WorkResumeEntry from './WorkResumeEntry.vue'
import { Plus } from '@element-plus/icons-vue'
import SectionHeader from '../ui/SectionHeader.vue'
const props = defineProps<{ experimentId?: number }>()
const router = useRouter(), creating = ref(false)
const params = computed(() => ({ experimentId: props.experimentId, size: 3 }))
const query = useQuery({ queryKey: ['works', 'resume', params], queryFn: () => workApi.list(params.value) })
</script>
<template>
  <section class="resume-work">
    <SectionHeader eyebrow="爛紙頭" :title="experimentId ? '正在製作' : '接著創作'"><template #actions><el-button :icon="Plus" @click="creating = true">建立作品</el-button><router-link class="resume-all" :to="{ path: '/works', query: experimentId ? { experimentId } : {} }">查看全部作品</router-link></template></SectionHeader>
    <p v-if="query.isPending.value">正在載入作品…</p>
    <el-alert v-else-if="query.isError.value" title="無法載入作品" type="error" :closable="false"><el-button @click="query.refetch()">重新載入</el-button></el-alert>
    <div v-else-if="query.data.value?.content.length" class="resume-list" :class="{ 'resume-list--experiment': experimentId }"><WorkResumeEntry v-for="w in query.data.value.content" :key="w.id" :work="w" /></div>
    <p class="resume-note" v-if="experimentId && (query.data.value?.totalElements ?? 0) > 3">顯示最近編輯的 3 件作品，其餘可在作品庫查看。</p>
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
.resume-note { margin-top: var(--space-sm); color: var(--el-text-color-secondary); font-size: var(--type-secondary); line-height: var(--leading-ui); }
@media (max-width: 1200px) { .resume-list { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 760px) { .resume-list { grid-template-columns: minmax(0, 1fr); } }
</style>
