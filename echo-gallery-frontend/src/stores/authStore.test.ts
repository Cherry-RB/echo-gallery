import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from './authStore'

const authApiMock = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  logout: vi.fn(),
  createDemoSession: vi.fn(),
  resetDemoSession: vi.fn(),
}))

vi.mock('../utils/api/authApi', () => ({
  authApi: authApiMock,
}))

describe('authStore', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('登入成功後保存認證資訊', async () => {
    authApiMock.login.mockResolvedValue({
      id: 42,
      token: 'valid-token',
      username: 'echo-user',
      email: 'echo@example.com',
    })

    const store = useAuthStore()
    await store.login({ email: 'echo@example.com', password: 'password123' })

    expect(store.token).toBe('valid-token')
    expect(store.username).toBe('echo-user')
    expect(localStorage.getItem('token')).toBe('valid-token')
    expect(localStorage.getItem('username')).toBe('echo-user')
  })

  it('後端登出失敗時仍清除前端認證資訊', async () => {
    authApiMock.login.mockResolvedValue({
      id: 42,
      token: 'valid-token',
      username: 'echo-user',
      email: 'echo@example.com',
    })
    authApiMock.logout.mockRejectedValue(new Error('network error'))
    vi.spyOn(console, 'error').mockImplementation(() => undefined)

    const store = useAuthStore()
    await store.login({ email: 'echo@example.com', password: 'password123' })
    await store.logout()

    expect(store.token).toBeNull()
    expect(store.username).toBe('訪客')
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('username')).toBeNull()
  })

  it('Demo 建立與重設會保存新的認證資訊並標記展示工作階段', async () => {
    authApiMock.createDemoSession.mockResolvedValue({ id: 7, token: 'demo-token', username: 'Demo' })
    authApiMock.resetDemoSession.mockResolvedValue({ id: 7, token: 'reset-token', username: 'Demo' })

    const store = useAuthStore()
    await store.startDemo('tech')
    expect(localStorage.getItem('demoSession')).toBe('true')
    expect(store.token).toBe('demo-token')

    await store.resetDemo()
    expect(store.token).toBe('reset-token')
    expect(localStorage.getItem('demoSession')).toBe('true')
  })
})
