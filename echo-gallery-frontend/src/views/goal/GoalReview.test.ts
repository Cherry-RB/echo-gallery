import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import ElementPlus from 'element-plus'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import GoalReview from './GoalReview.vue'
import { goalApi } from '../../utils/api/goalApi'
import type { GoalReview as Review } from '../../types/goal'

const { push, replace, registerLeave } = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn(), registerLeave: vi.fn() }))
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push, replace }), onBeforeRouteLeave: registerLeave, onBeforeRouteUpdate: vi.fn() }))
vi.mock('../../utils/api/goalApi', () => ({ goalApi: { review: vi.fn(), previewReview: vi.fn(), initializeReview: vi.fn(), sources: vi.fn(), saveReview: vi.fn(), submitReview: vi.fn() } }))
const route = reactive({ params: { id: '1', reviewId: '2' } })
const initial: Review = {
  id: 2, startsOn: '2026-11-01', endsOn: '2026-11-02', status: 'DRAFT', version: 1, goalVersion: 3, updatedAt: '', submittedAt: null,
  content: { observation: '', decision: '', direction: 'ALIGNED', informationHelpfulness: null, actionEffectiveness: null },
  snapshot: { formatVersion: 2, definition: { name: '保存當時的目標', startsOn: '2026-11-01', endsOn: '2026-11-08', expectation: '本期期待', motivation: '', imageUrl: '', status: 'ACTIVE', archived: false, pinned: false, pinOrder: 0, reviewDays: 7, remindersEnabled: true, allTags: false, sources: [], metrics: [] }, metrics: [], cardCount: 18, logs: [], sources: [], coverageAt: '', resultingStatus: 'ACTIVE' },
}
let wrapper: ReturnType<typeof mount>, client: QueryClient
async function open(review = initial, editing = true, existingClient?: QueryClient) {
  vi.mocked(goalApi.review).mockResolvedValue(review)
  vi.mocked(goalApi.sources).mockResolvedValue([])
  client = existingClient ?? new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
  wrapper = mount(GoalReview, { attachTo: document.body, props: { editing }, global: { plugins: [ElementPlus, [VueQueryPlugin, { queryClient: client }]], stubs: { RouterLink: { template: '<a><slot /></a>' } } } })
  await flushPromises()
}
const complete = () => wrapper.findAll('button').find(button => button.text() === '完成復盤')!
beforeEach(() => { vi.resetAllMocks(); route.params.reviewId = '2' })
afterEach(() => { wrapper?.unmount(); client?.clear() })

describe('復盤完成流程', () => {
  it('開啟空白復盤不寫入，首次輸入後返回才建立未完成紀錄', async () => {
    route.params.reviewId = 'new'
    vi.mocked(goalApi.previewReview).mockResolvedValue({ ...initial, id: 0 })
    let saved: Review = initial
    vi.mocked(goalApi.initializeReview).mockImplementation(async (_id, content) => {
      saved = { ...initial, content }
      vi.mocked(goalApi.review).mockResolvedValue(saved)
      return saved
    })
    replace.mockImplementation(async (path: string) => { route.params.reviewId = path.split('/')[4]! })
    await open()
    expect(goalApi.initializeReview).not.toHaveBeenCalled()
    await wrapper.findAll('textarea')[0]!.setValue('首次觀察')
    const leave = registerLeave.mock.calls.at(-1)![0] as () => Promise<boolean>
    expect(await leave()).toBe(true)
    expect(goalApi.initializeReview).toHaveBeenCalledWith(1, expect.objectContaining({ observation: '首次觀察' }))
    expect(saved.status).toBe('DRAFT')
    expect(replace).not.toHaveBeenCalled()
  })
  it('首次自動保存切換到已建立紀錄時，不被離開保護卡住', async () => {
    route.params.reviewId = 'new'
    vi.mocked(goalApi.previewReview).mockResolvedValue({ ...initial, id: 0 })
    vi.mocked(goalApi.initializeReview).mockImplementation(async (_id, content) => {
      const saved = { ...initial, content }
      vi.mocked(goalApi.review).mockResolvedValue(saved)
      return saved
    })
    let guardFinished = false
    replace.mockImplementation(async (path: string) => {
      const leave = registerLeave.mock.calls.at(-1)![0] as () => Promise<boolean>
      guardFinished = await leave()
      route.params.reviewId = path.split('/')[4]!
    })
    await open()
    await wrapper.findAll('textarea')[0]!.setValue('自動保存')
    await vi.waitFor(() => expect(guardFinished).toBe(true), { timeout: 3000 })
    await flushPromises()
    expect(goalApi.initializeReview).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('textarea')[0]!.element.value).toBe('自動保存')
  })
  it('保存後離開再進入未完成復盤，使用伺服器最新內容與版本', async () => {
    const revision: Review = { ...initial }
    await open(revision)
    let saved = revision
    vi.mocked(goalApi.saveReview).mockImplementation(async (_id, _reviewId, content, version) => {
      saved = { ...revision, content, version: version + 1 }
      return saved
    })
    await wrapper.findAll('textarea')[0]!.setValue('重新進入應保留這段文字')
    const leave = registerLeave.mock.calls.at(-1)![0] as () => Promise<boolean>
    expect(await leave()).toBe(true)
    expect(saved.content.observation).toBe('重新進入應保留這段文字')
    // 保留與正式頁面相同的 QueryClient，重現返回後再進入時仍有舊快取。
    wrapper.unmount()
    client.setQueryData(['goals', localStorage.getItem('id') || '', 'review', 1, 2], revision)
    await open(saved, true, client)
    expect(wrapper.findAll('textarea')[0]!.element.value).toBe('重新進入應保留這段文字')
    await wrapper.findAll('textarea')[0]!.setValue('接著編輯')
    const leaveAgain = registerLeave.mock.calls.at(-1)![0] as () => Promise<boolean>
    expect(await leaveAgain()).toBe(true)
    expect(goalApi.saveReview).toHaveBeenLastCalledWith(1, 2, expect.objectContaining({ observation: '接著編輯' }), 2)
  })
  it('尚未等到防抖儲存就返回時，先存妥再離開，重新進入可接續編輯', async () => {
    await open()
    let saved = initial
    vi.mocked(goalApi.saveReview).mockImplementation(async (_id, _reviewId, content, version) => {
      saved = { ...initial, content, version: version + 1 }
      return saved
    })
    await wrapper.findAll('textarea')[0]!.setValue('返回前剛輸入的文字')
    const leave = registerLeave.mock.calls.at(-1)![0] as () => Promise<boolean>
    expect(await leave()).toBe(true)
    expect(saved.content.observation).toBe('返回前剛輸入的文字')
    wrapper.unmount()
    await open(saved, true, client)
    expect(wrapper.findAll('textarea')[0]!.element.value).toBe('返回前剛輸入的文字')
    expect(wrapper.text()).toContain('已儲存')
  })
  it('已完成紀錄即使存在舊草稿快取，也不會先跳進編輯或寫入草稿', async () => {
    client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: Infinity } } })
    client.setQueryData(['goals', localStorage.getItem('id') || '', 'review', 1, 2], initial)
    await open({ ...initial, status: 'SUBMITTED', version: 3 }, false, client)
    expect(replace).not.toHaveBeenCalled()
    expect(goalApi.saveReview).not.toHaveBeenCalled()
    expect(wrapper.findAll('textarea')).toHaveLength(0)
    expect(wrapper.text()).toContain('已完成')
  })
  it('已完成復盤直接編輯，只有按儲存變更才更新同一筆', async () => {
    const submitted: Review = { ...initial, status: 'SUBMITTED', version: 3 }
    await open(submitted)
    vi.mocked(goalApi.saveReview).mockImplementation(async (_id, _reviewId, content, version) => {
      const saved = { ...submitted, content, version: version + 1 }
      vi.mocked(goalApi.review).mockResolvedValue(saved)
      return saved
    })
    await wrapper.findAll('textarea')[0]!.setValue('補充觀察')
    expect(goalApi.saveReview).not.toHaveBeenCalled()
    await wrapper.findAll('button').find(button => button.text() === '儲存變更')!.trigger('click'); await flushPromises()
    expect(goalApi.saveReview).toHaveBeenCalledWith(1, 2, expect.objectContaining({ observation: '補充觀察' }), 3)
    expect(goalApi.submitReview).not.toHaveBeenCalled()
    expect(replace).toHaveBeenCalledWith('/goals/1/reviews/2')
  })
  it('草稿保存失敗時不提交、不導頁，而且保留最新輸入', async () => {
    await open()
    vi.mocked(goalApi.saveReview).mockRejectedValue({ response: { data: { message: '復盤已有更新，請重新讀取' } } })
    await wrapper.findAll('textarea')[0]!.setValue('需要保留的觀察')
    await complete().trigger('click'); await flushPromises()
    expect(goalApi.submitReview).not.toHaveBeenCalled()
    expect(replace).not.toHaveBeenCalled()
    expect(wrapper.findAll('textarea')[0]!.element.value).toBe('需要保留的觀察')
    expect(wrapper.text()).toContain('復盤已有更新')
  })
  it('目標狀態必須確認，提交傳入版本及狀態，成功後導向已保存詳情', async () => {
    await open()
    vi.mocked(goalApi.saveReview).mockImplementation(async (_id, _reviewId, content, version) => ({ ...initial, content, version: version + 1 }))
    const submitted: Review = { ...initial, status: 'SUBMITTED', content: { ...initial.content, direction: 'COMPLETE' } }
    vi.mocked(goalApi.submitReview).mockImplementation(async () => {
      vi.mocked(goalApi.review).mockResolvedValue(submitted)
      return submitted
    })
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'COMPLETE'); await flushPromises()
    await complete().trigger('click'); await flushPromises()
    expect(goalApi.submitReview).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('請確認這次會變更目標狀態')
    wrapper.findComponent({ name: 'ElCheckbox' }).vm.$emit('update:modelValue', true); await flushPromises()
    await complete().trigger('click'); await flushPromises()
    expect(goalApi.submitReview).toHaveBeenCalledWith(1, 2, 2, expect.any(String), 3, 'COMPLETED')
    expect(replace).toHaveBeenCalledWith('/goals/1/reviews/2')
  })
  it('已完成詳情只顯示復盤快照；沒有快照的舊資料如實標示缺口', async () => {
    await open({ ...initial, status: 'SUBMITTED' }, false)
    expect(wrapper.text()).toContain('保存當時的目標')
    expect(wrapper.text()).toContain('18 張')
    expect(goalApi.review).toHaveBeenCalledTimes(1)
    wrapper.unmount(); client.clear()
    await open({ ...initial, status: 'SUBMITTED', snapshot: null }, false)
    expect(wrapper.text()).toContain('沒有完整的期間快照')
    expect(wrapper.text()).not.toContain('18 張')
  })
})
