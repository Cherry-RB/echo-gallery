<script setup lang="ts">
import { useRoute } from 'vue-router'
import { workStages, type WorkSummary } from '../../types/work'
import { formatDate } from '../../utils/formatDate'
defineProps<{ work: WorkSummary }>()
const route = useRoute()
</script>
<template>
  <article class="resume-entry">
    <div class="resume-entry-main">
      <div class="resume-entry-heading">
        <router-link class="resume-entry-title" :to="{ path: `/works/${work.id}`, query: { from: route.fullPath } }">{{ work.title }}</router-link>
        <span class="resume-entry-stage">{{ workStages[work.stage] }}</span>
      </div>
      <div v-if="work.nextStep" class="resume-entry-next"><span class="resume-entry-label">下次接著做</span><p>{{ work.nextStep }}</p></div>
      <p v-else class="resume-entry-empty">尚未留下下一步</p>
      <p class="resume-entry-meta">最近編輯 {{ formatDate(work.editedAt) }}</p>
    </div>
  </article>
</template>
<style scoped>
.resume-entry { position: relative; display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-md); min-height: 152px; padding: var(--space-md); border: 1px solid var(--el-border-color-light); border-radius: var(--radius-md); background: var(--el-bg-color); min-width: 0; }
.resume-entry-main { display: grid; gap: var(--space-sm); min-width: 0; width: 100%; }
.resume-entry-heading { display: flex; align-items: baseline; flex-wrap: wrap; gap: var(--space-xs); }
.resume-entry-title { min-width: 0; color: var(--el-text-color-primary); font-size: var(--type-card-title); font-weight: var(--weight-semibold); line-height: var(--leading-card-title); text-decoration: none; overflow-wrap: anywhere; }
.resume-entry-stage, .resume-entry-meta { color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); }
.resume-entry-stage { flex-shrink: 0; white-space: nowrap; }
.resume-entry-meta { margin: 0; }
.resume-entry-next { min-width: 0; overflow-wrap: anywhere; font-size: var(--type-body); line-height: var(--leading-body); }
.resume-entry-label { display: block; color: var(--el-text-color-secondary); font-size: var(--type-metadata); line-height: var(--leading-metadata); margin-bottom: var(--space-2xs); }
.resume-entry-next p { display: -webkit-box; margin: 0; overflow: hidden; -webkit-box-orient: vertical; -webkit-line-clamp: 2; white-space: pre-wrap; }
.resume-entry-empty { margin: 0; color: var(--el-text-color-secondary); font-size: var(--type-body); line-height: var(--leading-body); }
.resume-entry-title::after { content: ""; position: absolute; inset: 0; border-radius: inherit; }
.resume-entry:hover { border-color: var(--el-color-primary-light-5); }
.resume-entry:hover .resume-entry-title { color: var(--el-color-primary); }
.resume-entry:focus-within { outline: 2px solid var(--el-color-primary); outline-offset: 2px; }
a:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
@media (max-width: 760px) { .resume-entry { flex-wrap: wrap; gap: var(--space-sm); } }
</style>
