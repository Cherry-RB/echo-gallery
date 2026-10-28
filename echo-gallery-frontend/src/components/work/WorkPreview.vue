<script setup lang="ts">
import { formatDate } from '../../utils/formatDate'
import { MoreFilled } from '@element-plus/icons-vue'
import type { WorkSummary } from '../../types/work'
import { workStages } from '../../types/work'
import { useRoute } from 'vue-router'
import { ref } from 'vue'
import { useQueryClient } from '@tanstack/vue-query'
import { ElMessageBox } from 'element-plus'
import { workApi } from '../../utils/api/workApi'
import WorkFormDialog from './WorkFormDialog.vue'
import type { WorkDetail } from '../../types/work'
const route = useRoute()
const props = defineProps<{ work: WorkSummary }>()
const client = useQueryClient(), editing = ref(false), current = ref<WorkDetail>(), busy = ref(false), error = ref('')
async function command(value: string) {
  if (busy.value) return
  error.value = ''; busy.value = true
  try {
    if (value === 'edit') { current.value = await workApi.detail(props.work.id); editing.value = true; return }
    try { await ElMessageBox.confirm(props.work.archived ? '還原後即可繼續編輯。' : '封存後仍可閱讀，還原後才能編輯。', props.work.archived ? '還原作品' : '封存作品', { confirmButtonText: props.work.archived ? '還原' : '封存', cancelButtonText: '取消' }) } catch { return }
    await workApi.update(props.work.id, props.work.archived ? 'restore' : 'archive'); await client.invalidateQueries({ queryKey: ['works'] }); await client.invalidateQueries({ queryKey: ['overview', 'current'] })
  } catch { error.value = '無法處理作品，請重試。' } finally { busy.value = false }
}
</script>
<template>
  <article class="work-preview app-collection-card">
    <header class="work-card-heading">
      <p class="work-meta">{{ workStages[work.stage] }} · {{ work.published ? '已發表' : '未發表' }}<span v-if="work.archived"> · 已封存</span></p>
      <el-dropdown trigger="click" :disabled="busy" @command="command">
        <el-button class="work-menu" text circle :icon="MoreFilled" :aria-label="`${work.title} 的更多操作`" />
        <template #dropdown><el-dropdown-menu><el-dropdown-item v-if="!work.archived" command="edit">編輯作品資訊</el-dropdown-item><el-dropdown-item command="archive" divided>{{ work.archived ? '還原作品' : '封存作品' }}</el-dropdown-item></el-dropdown-menu></template>
      </el-dropdown>
    </header>
    <router-link :to="{ path: `/works/${work.id}`, query: { from: route.fullPath } }" class="work-main">
      <h2>{{ work.title }}</h2>
      <p v-if="work.description" class="work-description">{{ work.description }}</p>
    </router-link>
    <div v-if="work.nextStep" class="work-next"><span>下次接著做</span><p>{{ work.nextStep }}</p></div>
    <footer class="work-card-footer">
      <div class="work-meta"><p>{{ work.experimentTitle || '無主要實驗' }}</p><p>最近編輯 {{ formatDate(work.editedAt) }}</p></div>
    </footer>
    <p v-if="error" class="work-error" role="alert">{{ error }}</p>
    <WorkFormDialog v-if="editing" v-model="editing" :work="current" />
  </article>
</template>
<style scoped>
.work-preview { position: relative; display: flex; flex-direction: column; gap: var(--space-sm); padding: var(--space-md); min-width: 0; overflow-wrap: anywhere; }
.work-card-heading { display: flex; justify-content: space-between; gap: var(--space-sm); align-items: center; }
.work-main { display: block; flex: 1; color: inherit; text-decoration: none; }
.work-main h2 { margin: 0; font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); color: var(--el-text-color-primary); }
.work-main::after { content: ""; position: absolute; inset: 0; border-radius: inherit; }
.work-menu { position: relative; z-index: 1; }
.work-preview:hover h2 { color: var(--el-color-primary); }
.work-meta { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); }
.work-meta p + p { margin-top: var(--space-2xs); }
.work-description { margin-top: var(--space-xs); color: var(--el-text-color-secondary); }
.work-next p, .work-description { font-size: var(--type-body); line-height: var(--leading-body); white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.work-next span { display: block; color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); margin-bottom: var(--space-2xs); }
.work-card-footer { display: flex; align-items: flex-end; justify-content: space-between; flex-wrap: wrap; gap: var(--space-sm); padding-top: var(--space-sm); border-top: 1px solid var(--el-border-color-light); margin-top: auto; }
.work-preview:focus-within { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
a:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
.work-error { color: var(--el-color-danger); font-size: var(--type-secondary); }
</style>
