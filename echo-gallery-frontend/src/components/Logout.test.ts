import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Logout from './Logout.vue'

const router = vi.hoisted(() => ({
  back: vi.fn(),
  push: vi.fn(),
  replace: vi.fn(),
}))
const authStore = vi.hoisted(() => ({ logout: vi.fn() }))
const message = vi.hoisted(() => ({ success: vi.fn() }))

vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('../stores/authStore', () => ({ useAuthStore: () => authStore }))
vi.mock('element-plus', () => ({ ElMessage: message }))

describe('Logout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('使用者取消作品草稿確認時回到前一頁', async () => {
    authStore.logout.mockRejectedValueOnce('cancel')

    mount(Logout)

    await vi.waitFor(() => expect(router.back).toHaveBeenCalledOnce())
    expect(router.replace).not.toHaveBeenCalled()
    expect(router.push).not.toHaveBeenCalled()
  })
})
