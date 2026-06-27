import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../utils/api/authApi'

export const useAuthStore = defineStore('auth', () => {
  // 1. 狀態 (State) - 初始化時直接從 localStorage 讀取，確保重整網頁不會跳登出
  const token = ref<string | null>(localStorage.getItem('token'))
  const username = ref<string>(localStorage.getItem('username') || '訪客')

  // 2. 計算屬性 (Getters) - 判斷目前是否為登入狀態
  const isAuthenticated = computed(() => !!token.value)

  // 3. 業務邏輯 (Actions) - 集中處理 API 與 LocalStorage 副作用

  // 登入 Action
  const login = async (loginForm: { email: string, password: string }) => {
    const res = await authApi.login(loginForm)
    if (res?.token) {
      setAuth(res.token, res.username);
    }
    return res
  }

  // 註冊 Action
  const register = async (registerForm: { username: string, email: string, password: string }) => {
    const res = await authApi.register(registerForm)
    if (res?.token) {
      setAuth(res.token, res.username);
    }
    return res
  }

  // 登出 Action
  const logout = async () => {
    try {
      await authApi.logout();
    } catch (err) {
      // 秉持你原先優秀的防禦性設計：後端失敗，前端依然要強制清理
      console.error('後端登出通知失敗，執行前端強制清理:', err)
    } finally {
      clearAuth()
    }
  }

  // 內部輔助函式：設定憑證
  function setAuth(userToken: string, name: string) {
    token.value = userToken
    username.value = name
    localStorage.setItem('token', userToken)
    localStorage.setItem('username', name)
  }

  // 內部輔助函式：清除憑證
  function clearAuth() {
    token.value = null
    username.value = '訪客'
    localStorage.removeItem('token')
    localStorage.removeItem('username')
  }

  return {
    token,
    username,
    isAuthenticated,
    login,
    register,
    logout
  }
})
