<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import LeftSidebar from '../components/LeftSidebar.vue'
import RightSidebar from '../components/RightSidebar.vue'
import QuickCreateCardDialog from '../components/QuickCreateCardDialog.vue'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'

const isLeftDrawerOpen = ref(false)
const isRightDrawerOpen = ref(false)
const isQuickCreateOpen = ref(false)

const route = useRoute()

const openQuickCreate = () => {
  isLeftDrawerOpen.value = false
  isQuickCreateOpen.value = true
}

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
      <div class="mobile-header-actions">
        <ThemeSwitcher compact />
        <button class="stats-toggle-btn" aria-label="開啟統計" @click="isRightDrawerOpen = true">📊</button>
      </div>
    </header>

    <aside class="sidebar desktop-only">
      <LeftSidebar @open-quick-create="openQuickCreate" />
    </aside>

    <el-drawer
      v-model="isLeftDrawerOpen"
      direction="ltr"
      size="260px"
      :with-header="false"
      destroy-on-close
      class="custom-mobile-drawer"
    >
      <LeftSidebar @open-quick-create="openQuickCreate" />
    </el-drawer>

    <main class="content-viewport">
       <RouterView v-slot="{ Component }">
        <KeepAlive :include="['BoardFlex', 'TagCenter', 'SearchView']">
          <component :is="Component" />
        </KeepAlive>
      </RouterView>
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

    <QuickCreateCardDialog v-model="isQuickCreateOpen" />
  </div>
</template>

<style scoped>
.main-layout {
  display: flex;
  width: 100%;
  min-height: 100dvh;
  text-align: left;
}

.mobile-header {
  display: none;
}

/* 🖥️ 桌機側邊欄：加入安全鎖，確保 Sticky 完美觸發 */
.sidebar {
  width: 240px;
  flex-shrink: 0;
  align-self: stretch;
  border-right: 1px solid var(--el-border-color-light);
  min-height: 100dvh;
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
  align-self: stretch;
  border-left: 1px solid var(--el-border-color-light);
  min-height: 100dvh;
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
    display: grid;
    grid-template-columns: 1fr auto 1fr;
    align-items: center;
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
  .hamburger-btn {
    justify-self: start;
  }

  .mobile-brand {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .mobile-header-actions {
    display: flex;
    align-items: center;
    gap: 4px;
    justify-self: end;
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
