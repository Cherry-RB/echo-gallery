<script setup lang="ts">
import { ref } from 'vue'
// 🌟 僅保留右側面板所需的圖示
import { CollectionTag, DataAnalysis } from '@element-plus/icons-vue'

// 1. 標籤排行資料模擬
const hotTags = ref([
  { name: 'Vue3', count: 142 },
  { name: 'TypeScript', count: 98 },
  { name: 'CSS佈局', count: 64 },
  { name: '演算法', count: 32 },
  { name: '經典文學', count: 18 }
])

// 2. 系統核心數據統計資料
const totalCards = ref(528)          
const todayReviewCount = ref(24)       
const highWatchLaterCount = ref(7)     
</script>

<template>
  <div class="stats-wrapper">
    
    <section class="sidebar-section">
      <h3 class="section-title">
        <el-icon><DataAnalysis /></el-icon>
        <span>EchoGallery 統計</span>
      </h3>
      
      <div class="stats-grid">
        <div class="stat-box highlight-box">
          <span class="stat-value">{{ todayReviewCount }}</span>
          <span class="stat-label">今日回流卡片數</span>
        </div>
        <div class="stat-box">
          <span class="stat-value">{{ totalCards }}</span>
          <span class="stat-label">已建立總卡片數</span>
        </div>
        <div class="stat-box">
          <span class="stat-value">{{ highWatchLaterCount }}</span>
          <span class="stat-label">稍後觀看 > 100</span>
        </div>
      </div>
    </section>

    <section class="sidebar-section">
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
          <span class="tag-count-text">{{ tag.count }} 條</span>
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