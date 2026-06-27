<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/authStore'
import { ElMessage } from 'element-plus'

const authStore = useAuthStore()
const router = useRouter()

onMounted(async () => {
  try {
    await authStore.logout() // 觸發大腦
    ElMessage.success("登出成功")
  } finally {
    router.push('/login') // 統一在這裡收尾
  }
})
</script>
<template>
  <div v-loading="true" style="height: 100vh; display: flex; align-items: center; justify-content: center;">
    正在安全登出中...
  </div>
</template>
