<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { workApi } from '../../utils/api/workApi'
import WorkWorkspace from '../../components/work/WorkWorkspace.vue'
import DetailShell from '../../components/ui/DetailShell.vue'
import { ArrowLeft } from '@element-plus/icons-vue'
const route = useRoute(), id = computed(() => Number(route.params.id))
const router = useRouter()
const initialLoadComplete = ref(false)
const initialLoadFailed = ref(false)
const query = useQuery({
  queryKey: ['works', 'detail', id],
  queryFn: () => workApi.detail(id.value),
  // 先明確取得該路由當下的版本，再讓後續失效機制做背景更新。
  enabled: computed(() => Number.isSafeInteger(id.value) && id.value > 0 && initialLoadComplete.value),
  refetchOnWindowFocus: false,
})
let initialRequest = 0
async function fetchInitial() {
  const currentId = id.value
  const request = ++initialRequest
  initialLoadComplete.value = false
  initialLoadFailed.value = false
  if (!Number.isSafeInteger(currentId) || currentId <= 0) {
    initialLoadComplete.value = true
    initialLoadFailed.value = true
    return
  }
  const result = await query.refetch()
  if (request !== initialRequest || currentId !== id.value) return
  initialLoadFailed.value = result.isError
  initialLoadComplete.value = true
}
watch(id, () => { void fetchInitial() }, { immediate: true })
const waitingForLatest = computed(() => !initialLoadComplete.value)
// 已有作品資料時，背景重新載入失敗不可中斷正在編輯的工作區。
const hasInitialLoadError = computed(() => initialLoadFailed.value || (query.isError.value && !query.data.value))
</script>
<template>
  <DetailShell v-if="waitingForLatest || hasInitialLoadError || !query.data.value">
    <template #navigation><nav class="app-detail-navigation"><el-button text :icon="ArrowLeft" @click="router.push('/works')">返回爛紙頭</el-button></nav></template>
    <el-skeleton v-if="waitingForLatest" :rows="8" animated aria-label="正在載入作品" aria-busy="true" />
    <el-result v-else icon="error" title="無法載入作品" sub-title="作品可能已被刪除，或暫時無法連線。"><template #extra><el-button @click="fetchInitial">重新載入</el-button></template></el-result>
  </DetailShell>
  <el-alert v-if="query.isError.value && !initialLoadFailed" class="work-detail-refresh-error" title="暫時無法更新作品資訊，正在編輯的內容仍會保留。" type="warning" :closable="false">
    <el-button link type="primary" @click="query.refetch()">重新載入</el-button>
  </el-alert>
  <WorkWorkspace v-if="!waitingForLatest && !hasInitialLoadError && query.data.value" :key="id" :initial="query.data.value" />
</template>

<style scoped>
.work-detail-refresh-error { margin: 0 auto var(--space-md); max-width: var(--page-max-width); }
</style>
