<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
// 🌟 引入原有與新加入的 Element Plus 官方圖示
import { 
  Calendar, 
  Refresh, 
  Star, 
  Loading, 
  Box, 
  Clock, 
  Search,
  Plus,
  Setting,
  User,
  RemoveFilled
} from '@element-plus/icons-vue'

const route = useRoute()

// 👤 從右側遷移過來的用戶狀態與基本資料
const isLoggedIn = ref(true)
const userProfile = ref({
  name: 'EchoUser',
  avatar: 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png',
  title: '全端開發工程師'
})

const menuItems = [
  { name: '今日', path: '/today', icon: Calendar },
  { name: '隨機', path: '/random', icon: Refresh },
  { name: '最新', path: '/', icon: Clock },
  { name: '熱門', path: '/trending', icon: Star },
  { name: '封存', path: '/archive', icon: Box },
  { name: '稍後再看', path: '/watch-later', icon: Loading },
  { name: '查詢', path: '/search', icon: Search }
]

const activeMenu = computed(() => route.path)
</script>

<template>
  <div class="sidebar-wrapper">
    <div class="sidebar-brand">
      <div class="brand-logo">🌌</div>
      <h2 class="brand-title">EchoGallery</h2>
    </div>

    <el-button type="primary" size="large" :icon="Plus" class="create-card-btn" style="font-size: 20px;height: 50px;">
      新建卡片
    </el-button>

    <el-menu
      :default-active="activeMenu"
      mode="vertical"
      router
      class="sidebar-menu-el"
    >
      <el-menu-item 
        v-for="item in menuItems" 
        :key="item.path" 
        :index="item.path"
      >
        <el-icon class="menu-icon" size="20">
          <component :is="item.icon"/>
        </el-icon>
        <template #title>
          <span style="font-size: 18px; margin-left: 10px;">{{ item.name }}</span>
        </template>
      </el-menu-item>
    </el-menu>

    <div class="brand-action-group">
      <div v-if="isLoggedIn" class="user-profile-card">
        <el-avatar :size="40" :src="userProfile.avatar" />
        <div class="user-meta">
          <span class="username">{{ userProfile.name }}</span>
          <span class="user-title">{{ userProfile.title }}</span>
        </div>
        
        <el-dropdown trigger="click" placement="bottom-end">
          <el-button link :icon="Setting" class="action-icon-btn" />
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item :icon="User">個人資料</el-dropdown-item>
              <el-dropdown-item :icon="Setting">偏好設定</el-dropdown-item>
              <el-dropdown-item divided :icon="RemoveFilled" @click="isLoggedIn = false">
                安全登出
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-else class="login-prompt-box">
        <el-button type="primary" plain class="w-full" @click="isLoggedIn = true">
          登入系統
        </el-button>
      </div>

    </div>

    <div class="sidebar-footer">
      <p class="version-text">v1.0.0</p>
    </div>
  </div>
</template>

<style scoped>
.sidebar-wrapper {
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  box-sizing: border-box;
  position: sticky;
  top: 0;
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-left: 12px;
  margin-bottom: 20px;
}
.brand-title {
  font-size: 20px;
  margin: 0;
  font-weight: 600;
}

/* 📥 新增：左側動作區塊容器與間距調配 */
.brand-action-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 0 4px;
  margin-bottom: 24px; /* 與下方 Menu 保持呼吸空間 */
  margin-top: 12px;
}

/* 用戶資料名片樣式 */
.user-profile-card {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-light);
  border-radius: 12px;
  gap: 10px;
}
.user-meta {
  display: flex;
  flex-direction: column;
  flex-grow: 1;
  min-width: 0;
}
.username {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-title {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  margin-top: 1px;
}
.action-icon-btn {
  font-size: 16px;
  color: var(--el-text-color-regular);
}
.login-prompt-box {
  padding: 4px 0;
}
.w-full {
  width: 100%;
}

/* 新建卡片按鈕樣式 */
.create-card-btn {
  width: 100%;
  font-weight: 600;
  letter-spacing: 1px;
  box-shadow: var(--el-box-shadow-light);
  margin-bottom: 12px;
}

.sidebar-menu-el {
  border-right: none !important;
}

.sidebar-footer {
  padding-top: 16px;
  /* margin-top: 16px; */
  border-top: 1px solid var(--el-border-color-light);
  text-align: center;
}
.version-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>