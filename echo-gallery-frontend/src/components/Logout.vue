<script setup lang="ts">
import { useRouter } from 'vue-router'
import { authApi } from '../utils/api/auth'
import { ref } from 'vue';

const router = useRouter();
// 在 script 裡讀取 localStorage，並存成響應式變數
const currentUsername = ref(localStorage.getItem('username') || '訪客')

const handleLogout = async () => {
  try {
    // 1. 呼叫後端登出（讓後端走完 Spring Security 的登出流程）
    await authApi.logout()
  } catch (err) {
    // 就算後端出錯，前端還是要強制登出
    console.error('後端登出通知失敗:', err)
  } finally {
    // 2. 完美的副作用處理：清空前端快取
    localStorage.removeItem('token')
    localStorage.removeItem('username')

    // 同步更新響應式變數，讓畫面立刻變回預設值
    currentUsername.value = '訪客'
    
    alert('您已成功登出！')
    
    // 3. 導向登入頁
    router.push('/login')
  }
}
</script>

<template>
  <div class="navbar">
    <span>歡迎，{{ currentUsername }}</span>
    <button @click="handleLogout">安全登出</button>
  </div>
</template>