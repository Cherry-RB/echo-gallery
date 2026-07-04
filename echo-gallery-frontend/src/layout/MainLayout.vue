<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import LeftSidebar from '../components/LeftSidebar.vue'
import RightSidebar from '../components/RightSidebar.vue'

const isLeftDrawerOpen = ref(false)
const isRightDrawerOpen = ref(false)

const route = useRoute()

// 當偵測到頁面路由切換時，才自動關閉手機版抽屜
watch(() => route.path, () => {
  isLeftDrawerOpen.value = false
  isRightDrawerOpen.value = false
})
</script>

<template>
  <div class="main-layout">
    <header class="mobile-header">
      <button class="hamburger-btn" @click="isLeftDrawerOpen = true">☰</button>
      <div class="mobile-brand">
        <span class="brand-logo">🌌</span>
        <h2 class="brand-title">EchoGallery</h2>
      </div>
      <button class="stats-toggle-btn" @click="isRightDrawerOpen = true">📊</button>
    </header>

    <aside class="sidebar desktop-only">
      <LeftSidebar />
    </aside>

    <el-drawer
      v-model="isLeftDrawerOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      destroy-on-close
      class="custom-mobile-drawer"
    >
      <LeftSidebar />
    </el-drawer>

    <main class="content-viewport">
      <RouterView />
    </main>

    <aside class="stats-panel desktop-only">
      <RightSidebar />
    </aside>

    <el-drawer
      v-model="isRightDrawerOpen"
      direction="rtl"
      size="280px"
      :with-header="false"
      destroy-on-close
      class="custom-mobile-drawer"
    >
      <div class="mobile-drawer-content">
        <RightSidebar />
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  min-height: 100vh;
  text-align: left;
}

.mobile-header {
  display: none;
}

/* 🖥️ 桌機側邊欄：加入安全鎖，確保 Sticky 完美觸發 */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  height: 100vh;
  position: sticky;
  top: 0;
}

.content-viewport {
  flex: 1;
  min-width: 0;
  padding: 20px;
}

/* 🖥️ 桌機右側欄：同步加入安全鎖 */
.stats-panel {
  width: 280px;
  flex-shrink: 0;
  border-left: 1px solid var(--el-border-color-light);
  height: 100vh;
  position: sticky;
  top: 0;
}

/* 🧼 清除 Element Plus 抽屜預設的 Padding，交由內部組件自己決定邊距 */
:deep(.el-drawer__body) {
  padding: 0 !important;
}

/* =====================================================
   🌟 響應式斷點：一律在 1200px 乾淨切換
   ===================================================== */
@media (max-width: 1200px) {
  .main-layout {
    flex-direction: column;
  }

  .desktop-only {
    display: none !important;
  }

  .mobile-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 56px;
    padding: 0 16px;
    border-bottom: 1px solid var(--el-border-color-light);
    position: sticky;
    top: 0;
    z-index: 1000;
    background: var(--el-bg-color); /* ✨ 改用 Element 原生背景變數，自動支援未來的黑夜模式 */
  }

  .hamburger-btn, .stats-toggle-btn {
    background: none;
    border: none;
    font-size: 24px;
    cursor: pointer;
    color: var(--el-text-color-primary);
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .mobile-brand .brand-title {
    font-size: 18px;
    margin: 0;
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  .content-viewport {
    padding: 16px;
    width: 100%;
    box-sizing: border-box;
  }

  .mobile-drawer-content {
    height: 100%;
    padding: 24px 16px; /* 讓右側欄在手機抽屜裡的邊距與左側欄對齊 */
    overflow-y: auto;
    box-sizing: border-box;
  }
}
</style>
