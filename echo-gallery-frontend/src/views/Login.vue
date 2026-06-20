<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../utils/api/auth'
import { useAsync } from '../utils/api/useAsync'

const router = useRouter()

// 1. 綁定表單輸入值
const email = ref('')
const password = ref('')

// 2. 引入 useAsync 封裝（因為 login 只需要 loading 和執行函式，data 與 error 可以不用解構出來）
const { loading, execute: handleLogin } = useAsync(authApi.login)

const onSubmit = async () => {
  if (!email.value || !password.value) return alert('請填寫完整欄位')

  try {
    // 3. 呼叫 execute，參數型態會被 TypeScript 嚴格把關 🛡️
    const res = await handleLogin({
      email: email.value,
      password: password.value
    })

    // 4. 登入成功後的副作用處理
    if (res && res.token) {
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      alert('登入成功！')
      router.push('/dashboard')
    }
  } catch (err) {
    // 錯誤通常已經被 Axios 攔截器全域處理（或在此處處理特定的商業邏輯錯誤）
    console.error('登入遭遇錯誤:', err)
  }
}
</script>

<template>
  <div class="login-container">
    <input v-model="email" type="email" placeholder="請輸入 Email" :disabled="loading" />
    <input v-model="password" type="password" placeholder="請輸入密碼" :disabled="loading" />

    <button @click="onSubmit" :disabled="loading">
      {{ loading ? '登入中...' : '確認登入' }}
    </button>

    <div class="auth-link">
      <router-link to="/register">還沒有帳號？立即註冊</router-link>
    </div>
  </div>
</template>