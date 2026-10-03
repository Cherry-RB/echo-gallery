<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

type OverviewView = 'current' | 'recent' | 'cards'

const route = useRoute()
const router = useRouter()

const selectedView = computed<OverviewView>({
  get: () => {
    if (route.name === 'OverviewRecent') return 'recent'
    if (route.name === 'CardReturnOverview') return 'cards'
    return 'current'
  },
  set: (view) => {
    const destination = {
      current: '/overview',
      recent: '/overview/recent',
      cards: '/overview/cards',
    }[view]
    router.push(destination)
  },
})
</script>

<template>
  <el-radio-group v-model="selectedView" class="overview-navigation" aria-label="總覽內容">
    <el-radio-button value="current">現在行動</el-radio-button>
    <el-radio-button value="recent">近期回顧</el-radio-button>
    <el-radio-button value="cards">回流狀態</el-radio-button>
  </el-radio-group>
</template>

<style scoped>
@media (max-width: 600px) {
  .overview-navigation { display: flex; width: 100%; }
  .overview-navigation :deep(.el-radio-button) { flex: 1 1 0; }
  .overview-navigation :deep(.el-radio-button__inner) { width: 100%; padding-inline: 8px; }
}
</style>
