import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '../utils/api/authApi'
import { workDrafts } from '../utils/workDrafts'
import { ElMessageBox } from 'element-plus'
import { queryClient } from '../utils/queryClient'

export const useAuthStore = defineStore('auth', () => {
  // 1. 狀態 (State) - 初始化時直接從 localStorage 讀取，確保重整網頁不會跳登出
  const token = ref<string | null>(localStorage.getItem('token'))
  const username = ref<string>(localStorage.getItem('username') || '訪客')
  const id = ref<number | null>(
    localStorage.getItem('id') ? Number(localStorage.getItem('id')) : null
  )

  // 2. 計算屬性 (Getters) - 判斷目前是否為登入狀態
  const isAuthenticated = computed(() => !!token.value)

  // 3. 業務邏輯 (Actions) - 集中處理 API 與 LocalStorage 副作用

  // 登入 Action
  const login = async (loginForm: { email: string, password: string }) => {
    const res = await authApi.login(loginForm)
    if (res?.token) {
      setAuth(res.token, res.username, res.id);
      localStorage.removeItem('demoSession')
    }
    return res
  }

  const startDemo = async (library: string) => {
    const res = await authApi.createDemoSession(library)
    if (res?.token) {
      setAuth(res.token, res.username, res.id)
      localStorage.setItem('demoSession', 'true')
    }
    return res
  }

  const resetDemo = async () => {
    const res = await authApi.resetDemoSession()
    if (id.value) await workDrafts.clear(String(id.value)).catch(() => {})
    await queryClient.cancelQueries()
    queryClient.clear()
    if (res?.token) setAuth(res.token, res.username, res.id)
    return res
  }

  // 註冊 Action
  const register = async (registerForm: { username: string, email: string, password: string }) => {
    const res = await authApi.register(registerForm)
    if (res?.token) {
      setAuth(res.token, res.username, res.id);
    }
    return res
  }

  // 登出 Action
  const logout = async () => {
    const owner = id.value ? String(id.value) : ''
    const drafts = await workDrafts.list(owner).catch(() => [])
    if (drafts.length) {
      const demo = localStorage.getItem('demoSession') === 'true'
      await ElMessageBox.confirm(demo ? '此展示帳號有尚未同步的作品草稿。登出會清理展示草稿，請先複製或下載需要保留的內容。' : '此瀏覽器有尚未同步的作品草稿。登出後仍會保留，需以同一帳號登入才能復原；請先確認已複製或下載重要內容。', '作品草稿尚未同步', { confirmButtonText: demo ? '清理草稿並登出' : '保留草稿並登出', cancelButtonText: '暫不登出' })
    }
    try {
      await authApi.logout();
    } catch (err) {
      // 秉持你原先優秀的防禦性設計：後端失敗，前端依然要強制清理
      console.error('後端登出通知失敗，執行前端強制清理:', err)
    } finally {
      if (localStorage.getItem('demoSession') === 'true') await workDrafts.clear(owner).catch(() => {})
      clearAuth()
    }
  }

  // 內部輔助函式：設定憑證
  function setAuth(userToken: string, name: string, inputUserId: number) {
    if (id.value !== inputUserId) {
      void queryClient.cancelQueries()
      queryClient.clear()
    }
    token.value = userToken
    username.value = name
    id.value = inputUserId
    localStorage.setItem('token', userToken)
    localStorage.setItem('username', name)
    localStorage.setItem('id', inputUserId.toString())
  }

  // 內部輔助函式：清除憑證
  function clearAuth() {
    void queryClient.cancelQueries()
    queryClient.clear()
    token.value = null
    username.value = '訪客'
    id.value = null
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('id')
    localStorage.removeItem('userId')
    localStorage.removeItem('demoSession')
  }

  return {
    token,
    username,
    id,
    isAuthenticated,
    login,
    startDemo,
    resetDemo,
    register,
    logout
  }
})
