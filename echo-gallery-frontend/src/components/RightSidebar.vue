<script setup lang="ts">
import { CollectionTag, DataAnalysis } from '@element-plus/icons-vue'
import { useQuery } from '@tanstack/vue-query'
import { sidebarApi } from '../utils/api/sidebarApi'

// =====================================================
// 🚀 TanStack Query 側邊欄資料獲取
// =====================================================
// 1. 取得右方側邊欄 核心數據統計資料
const { data: sidebarStats, isLoading: isStatsLoading } = useQuery({
  queryKey: ['sidebar', 'stats'],
  queryFn: sidebarApi.getSidebarStatus,
  // 選配：通常統計數據不需要每秒都變，可以設定 1 分鐘內不重複發送請求
  staleTime: 1000 * 60, // 標籤排行快取 1 分鐘
})

// 2. 取得右方側邊欄 標籤熱門排行
const { data: hotTags, isLoading: isTagsLoading } = useQuery({
  queryKey: ['sidebar', 'tags'],
  queryFn: sidebarApi.getSidebarTagsTop,
  // 預設給予空陣列，防止未載入完成時 v-for 報錯
  placeholderData: [],
  staleTime: 1000 * 60 * 5, // 標籤排行快取 5 分鐘
})

</script>

<template>
  <div class="stats-wrapper">

    <section class="sidebar-section" v-loading="isStatsLoading">
      <h3 class="section-title">
        <el-icon><DataAnalysis /></el-icon>
        <span>EchoGallery 統計</span>
      </h3>

      <div class="stats-grid" v-if="sidebarStats">
        <div class="stat-box highlight-box">
          <span class="stat-value">{{ sidebarStats.todayEchoCards ?? 0 }}</span>
          <span class="stat-label">今日回流卡片數</span>
        </div>
        <div class="stat-box">
          <span class="stat-value">{{ sidebarStats.totalCards ?? 0 }}</span>
          <span class="stat-label">未封存卡片</span>
        </div>
        <div class="stat-box">
          <span class="stat-value">{{ sidebarStats.highSnoozeCards ?? 0 }}</span>
          <span class="stat-label">稍後觀看 &gt; 10</span>
        </div>

        <div class="stat-box growth-overview">
          <div class="growth-stats">
            <el-tooltip content="種子卡片" placement="top">
              <span
                class="growth-stat seed-stat"
                role="img"
                tabindex="0"
                :aria-label="`種子卡片 ${sidebarStats.seedCards ?? 0} 張`"
              >
                {{ sidebarStats.seedCards ?? 0 }}<span aria-hidden="true">🌱</span>
              </span>
            </el-tooltip>
            <el-tooltip content="生長卡片" placement="top">
              <span
                class="growth-stat growing-stat"
                role="img"
                tabindex="0"
                :aria-label="`生長卡片 ${sidebarStats.growingCards ?? 0} 張`"
              >
                {{ sidebarStats.growingCards ?? 0 }}<span aria-hidden="true">🌿</span>
              </span>
            </el-tooltip>
            <el-tooltip content="成熟卡片" placement="top">
              <span
                class="growth-stat mature-stat"
                role="img"
                tabindex="0"
                :aria-label="`成熟卡片 ${sidebarStats.matureCards ?? 0} 張`"
              >
                {{ sidebarStats.matureCards ?? 0 }}<span aria-hidden="true">🌳</span>
              </span>
            </el-tooltip>
          </div>
        </div>

        <div class="stat-box">
          <span class="stat-value">{{ sidebarStats.unfinishedWorks ?? 0 }}</span>
          <span class="stat-label">未完成作品</span>
        </div>
        <div class="stat-box">
          <span class="stat-value">{{ sidebarStats.totalWorks ?? 0 }}</span>
          <span class="stat-label">作品總數</span>
        </div>
      </div>
    </section>

    <section class="sidebar-section" v-loading="isTagsLoading">
      <h3 class="section-title">
        <el-icon><CollectionTag /></el-icon>
        <span>熱門標籤排行</span>
      </h3>
      <div class="tag-ranking-list">
        <div v-for="(tag, index) in hotTags" :key="tag.name" class="tag-rank-item">
          <div class="tag-info">
            <span class="rank-badge" :class="{ 'top-three': index < 3 }">
              {{ index + 1 }}
            </span>
            <el-tag size="default" type="info" effect="plain" class="custom-tag">
              {{ tag.name }}
            </el-tag>
          </div>
          <span class="tag-count-text">{{ tag.cardCount }} 條</span>
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
.stats-wrapper {
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  box-sizing: border-box;
  gap: 32px; /* 擴大區塊大間距，看起來更有儀表板的通透感 */
  position: sticky;
  top: 0;
  overflow-y: auto;
  height: 100vh;
}

.sidebar-section {
  display: flex;
  flex-direction: column;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  margin: 0 0 16px 4px;
  font-weight: 600;
}
.section-title .el-icon {
  font-size: 16px;
  color: var(--el-text-color-secondary);
}

/* 📊 統計資訊網格（2 欄優化佈局） */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.stat-box {
  display: flex;
  flex-direction: column;
  padding: 12px 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}
/* 讓最重要的「今日回流」橫跨兩欄獨佔一列 */
.stat-box.highlight-box {
  grid-column: span 2;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
.stat-box.highlight-box .stat-value {
  color: var(--el-color-primary);
}
.stat-box.highlight-box .stat-label {
  color: var(--el-color-primary-light-2);
  font-weight: 500;
}

.stat-box.growth-overview {
  grid-column: span 2;
  background: var(--el-color-success-light-9);
}

.overview-label {
  margin-bottom: 8px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.growth-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.growth-stat {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: 6px 4px;
  border-radius: 6px;
  font-size: 18px;
  font-weight: 700;
  line-height: 1.2;
  outline: none;
}

.growth-stat:focus-visible {
  box-shadow: 0 0 0 2px var(--el-color-primary-light-5);
}

.seed-stat {
  color: var(--el-color-success-dark-2);
}

.growing-stat {
  color: var(--el-color-warning-dark-2);
}

.mature-stat {
  color: var(--el-color-primary);
}

.stat-value {
  font-size: 22px;
  font-weight: 700;
  color: var(--el-text-color-primary);
  line-height: 1.2;
}
.stat-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}

/* 🏷️ 標籤排行樣式 */
.tag-ranking-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.tag-rank-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 6px;
}
.tag-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.rank-badge {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  font-size: 11px;
  font-weight: bold;
  border-radius: 50%;
  background: var(--el-fill-color-darker);
  color: var(--el-text-color-secondary);
}
.rank-badge.top-three {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.custom-tag {
  border: none !important;
  background-color: var(--el-fill-color-light) !important;
  color: var(--el-text-color-primary) !important;
  font-weight: 500;
}
.tag-count-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 1200px) {
  .stats-wrapper {
    position: static;
    height: auto;
    overflow-y: visible;
    padding: 0;
    gap: 24px;
  }
}
</style>
