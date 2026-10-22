<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
const props = defineProps<{ treasureId: number }>()
const query = useQuery({ queryKey: computed(() => ['works', 'treasure', props.treasureId]), queryFn: () => workApi.fromTreasure(props.treasureId) })
</script>
<template>
  <section v-if="query.data.value" class="treasure-work-link"><h3>來源作品</h3><p>{{ query.data.value.work.title }}{{ query.data.value.work.archived ? '（已封存）' : '' }}</p><router-link v-if="query.data.value.work.editorMode === 'INTERNAL'" :to="`/works/${query.data.value.work.id}?mode=read`">閱讀作品最新版</router-link><a v-else-if="query.data.value.externalUrl" :href="query.data.value.externalUrl" target="_blank" rel="noopener noreferrer">開啟原始文件</a><p v-else>尚未設定外部文件網址。</p><router-link :to="`/works/${query.data.value.work.id}`">前往作品工作區</router-link></section>
  <p v-else-if="query.isError.value">無法載入來源作品。<el-button @click="query.refetch()">重試</el-button></p>
</template>
<style scoped>
.treasure-work-link { display: flex; flex-wrap: wrap; gap: var(--space-xs) var(--space-md); margin-block: var(--space-md); padding-block: var(--space-md); border-block: 1px solid var(--el-border-color-light); min-width: 0; }
.treasure-work-link h3, .treasure-work-link p { flex-basis: 100%; margin: 0; overflow-wrap: anywhere; }
.treasure-work-link h3 { font-size: var(--type-secondary); font-weight: var(--weight-semibold); line-height: var(--leading-ui); }
.treasure-work-link p, .treasure-work-link a { font-size: var(--type-secondary); line-height: var(--leading-ui); }
.treasure-work-link a { color: var(--el-color-primary); text-decoration: none; overflow-wrap: anywhere; }
.treasure-work-link a:hover { text-decoration: underline; }
.treasure-work-link a:focus-visible { outline: 2px solid var(--el-color-primary); outline-offset: var(--space-2xs); }
</style>
