import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewDashboard from './OverviewDashboard.vue'
import type { TreasureSummary, TreasureTrial } from '../../types/treasure'

const state = vi.hoisted(() => ({ current: vi.fn(), push: vi.fn() }))
vi.mock('../../utils/api/overviewApi', () => ({ overviewApi: { getCurrentOverview: state.current } }))
vi.mock('vue-router', async importOriginal => ({
  ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ push: state.push }),
}))
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
const treasure = (overrides: Partial<TreasureSummary> = {}): TreasureSummary => ({
  id: 1, title: '熱氣球', rank: 'STONE', description: null, coverImageUrl: null,
  acquiredOn: '2026-10-16', isArchived: false, createdAt: '2026-10-16T00:00:00Z', updatedAt: '2026-10-16T00:00:00Z',
  activeTrial: null, yieldCount: 0, ...overrides,
})
const challenge = (overrides: Partial<TreasureTrial> = {}): TreasureTrial => ({
  id: 9, targetRank: 'BRONZE', title: '邀請真實使用者試用', criteria: '留下試用日期、使用情境與回饋。',
  status: 'PLANNED', resultNote: null, evidenceUrl: null, attemptedAt: null, passedAt: null,
  createdAt: '', updatedAt: '', ...overrides,
})
function render(items: TreasureSummary[], count = items.length) {
  state.current.mockResolvedValue({ todayReturnPoolCount: 0, recurringCardCount: 0, pausedCardCount: 0,
    needsProcessingCardCount: 0, activeExperimentCount: 0, treasureCount: count,
    experimentTries: [], treasureActions: items, attentionSignals: [],
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(OverviewDashboard, { global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      OverviewNavigation: true, ExperimentExplorationEditor: true, ExperimentExplorationHistoryDialog: true,
      TreasureTrialDialog: true, TreasureYieldDialog: true, TreasureYieldHistory: true, TreasureFormDialog: true,
      HistoryDialog: { name: 'HistoryDialog', props: ['modelValue', 'title'], template: '<section><slot name="actions" /><slot /></section>' },
      'el-button': { props: ['disabled'], emits: ['click'], template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'el-result': true,
    },
  } })
  wrappers.push(wrapper)
  return wrapper
}

describe('總覽成果行動入口', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); clients.splice(0).forEach(client => client.clear()) })

  it('空館藏使用明確收錄入口，直接開啟既有表單', async () => {
    const wrapper = render([])
    await flushPromises()
    expect(wrapper.text()).toContain('讓成果繼續產生價值')
    expect(wrapper.text()).toContain('尚無館藏')
    await wrapper.get('.treasure-empty-state button').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureFormDialog' }).props('modelValue')).toBe(true)
    expect(state.push).not.toHaveBeenCalled()
  })

  it('已有館藏但沒有可繼續檢定，不以普通成果或未來規劃補滿，也不誤報沒有館藏', async () => {
    const wrapper = render([treasure(), treasure({ id: 2, activeTrial: challenge({ targetRank: 'GOLD' }) })], 8)
    await flushPromises()
    expect(wrapper.text()).toContain('目前沒有可繼續的檢定')
    expect(wrapper.text()).not.toContain('尚無館藏')
    expect(wrapper.find('.treasure-action-item').exists()).toBe(false)
    expect(wrapper.find('.record-history-trigger').exists()).toBe(false)
    await wrapper.get('.treasure-empty-state button').trigger('click')
    expect(state.push).toHaveBeenCalledWith('/treasures')
    expect(wrapper.findComponent({ name: 'TreasureFormDialog' }).exists()).toBe(false)
  })

  it.each([
    ['PLANNED', '待嘗試'], ['ATTEMPTED', '已嘗試・待確認'], ['NOT_PASSED', '未通過・可再嘗試'],
  ] as const)('以檢定與真實條件提供行動判斷，%s 狀態直接開啟下一階且不顯示零計數', async (status, label) => {
    const wrapper = render([treasure({ activeTrial: challenge({ status }) })], 8)
    await flushPromises()
    expect(wrapper.text()).not.toContain('最多 3 件')
    expect(wrapper.text()).not.toContain('其他成果也能設定檢定')
    const main = wrapper.get('.treasure-challenge-main')
    expect(main.element.tagName).toBe('BUTTON')
    expect(main.attributes('data-rank')).toBe('BRONZE')
    expect(main.get('.treasure-challenge-title').text()).toBe('邀請真實使用者試用')
    expect(main.get('.treasure-challenge-criteria').text()).toBe('留下試用日期、使用情境與回饋。')
    expect(main.get('.treasure-action-status').text()).toBe(label)
    expect(wrapper.find('.record-history-trigger').exists()).toBe(false)
    await main.trigger('click')
    const dialog = wrapper.findComponent({ name: 'TreasureTrialDialog' })
    expect(dialog.props('initialMode')).toBe('route')
    expect(dialog.props('initialRank')).toBe('BRONZE')
    expect(state.push).not.toHaveBeenCalled()
  })

  it('檢定查看、產出新增及完整歷程分開，計數有名稱且不觸發導頁', async () => {
    const item = treasure({ yieldCount: 4, activeTrial: challenge({ status: 'NOT_PASSED' }) })
    const wrapper = render([item])
    await flushPromises()
    expect(wrapper.get('.treasure-action-title').attributes('href')).toBe('/treasures/1')
    expect(wrapper.text()).toContain('未通過')
    await wrapper.get('.treasure-output-controls button').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureTrialDialog' }).exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'TreasureYieldDialog' }).props('treasureId')).toBe(1)
    wrapper.findComponent({ name: 'TreasureYieldDialog' }).vm.$emit('update:modelValue', false)
    await flushPromises()
    await wrapper.get('.treasure-challenge-main').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureTrialDialog' }).props('initialMode')).toBe('route')
    wrapper.findComponent({ name: 'TreasureTrialDialog' }).vm.$emit('saved')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'TreasureTrialDialog' }).exists()).toBe(false)
    await wrapper.get('.treasure-output-controls .record-history-trigger').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureYieldHistory' }).props('treasureId')).toBe(1)
    expect(wrapper.get('.record-history-trigger').attributes('aria-label')).toBe('查看 4 筆價值產出')
    expect(state.push).not.toHaveBeenCalled()
  })

  it('金級不當作下一階行動，保留到館藏使用與記錄的入口', async () => {
    const wrapper = render([treasure({ rank: 'GOLD' })])
    await flushPromises()
    expect(wrapper.find('.treasure-challenge-main').exists()).toBe(false)
    expect(wrapper.text()).toContain('可以保持原級珍藏')
    await wrapper.get('.treasure-empty-state button').trigger('click')
    expect(state.push).toHaveBeenCalledWith('/treasures')
  })
})
