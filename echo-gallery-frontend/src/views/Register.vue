<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../utils/api/auth' // 💡 假設你的 authApi 裡有寫 register
import { useAsync } from '../utils/api/useAsync'

const router = useRouter()

// 1. 註冊需要三個欄位（比登入多一個 username）
const username = ref('')
const email = ref('')
const password = ref('')

// 2. 引入 useAsync 封裝，這次綁定 register 門禁
const { loading, execute: handleRegister } = useAsync(authApi.register)

const onSubmit = async () => {
  if (!username.value || !email.value || !password.value) return alert('請填寫完整欄位')

  try {
    // 3. 呼叫後端註冊 API
    const res = await handleRegister({
      username: username.value,
      email: email.value,
      password: password.value
    })

    // 4. 因為你的後端 AuthService.register 寫得很好，註冊成功會直接發 token
    // 這裡可以直接幫使用者完成「自動登入」的副作用！
    if (res && res.token) {
      localStorage.setItem('token', res.token)
      localStorage.setItem('username', res.username)
      alert('註冊成功，已為您自動登入！')
      router.push('/dashboard')
    }
  } catch (err) {
    console.error('註冊遭遇錯誤:', err)
  }
}
</script>

<template>
  <div class="register-container">
    <h2>註冊新帳號</h2>
    <input v-model="username" type="text" placeholder="請輸入使用者名稱" :disabled="loading" />
    
    <input v-model="email" type="email" placeholder="請輸入 Email" :disabled="loading" />
    <input v-model="password" type="password" placeholder="請輸入密碼" :disabled="loading" />

    <button @click="onSubmit" :disabled="loading">
      {{ loading ? '註冊中...' : '確認註冊' }}
    </button>

    <div class="auth-link">
      <router-link to="/login">已有帳號？前往登入</router-link>
    </div>
  </div>
</template>