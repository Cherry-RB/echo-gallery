import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewDashboard from './OverviewDashboard.vue'
import type { TreasureSummary, TreasureTrial } from '../../types/treasure'
import type { OverviewResponse } from '../../types/overview'

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
function render(items: TreasureSummary[], count = items.length, workCount = 0, overrides: Partial<OverviewResponse['current']> = {}) {
  state.current.mockResolvedValue({ todayReturnPoolCount: 0, recurringCardCount: 0, pausedCardCount: 0,
    needsProcessingCardCount: 0, activeExperimentCount: 0, treasureCount: count, workCount,
    experimentTries: [], treasureActions: items, attentionSignals: [], ...overrides,
  })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(OverviewDashboard, { global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      OverviewNavigation: true, WorkResume: true, ExperimentExplorationEditor: true, ExperimentExplorationHistoryDialog: true,
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

  it('空館藏隱藏成果區塊，保留統計與展覽館入口', async () => {
    const wrapper = render([])
    await flushPromises()
    expect(wrapper.find('#overview-treasure-title').exists()).toBe(false)
    expect(wrapper.find('.action-grid').exists()).toBe(false)
    const stat = wrapper.findAll('.current-stat').find(item => item.get('small').text() === '館藏展品')!
    expect(stat.get('strong').text()).toBe('0')
    await stat.trigger('click')
    expect(state.push).toHaveBeenCalledWith('/treasures')
  })

  it('作品排在全部實驗摘要後方，保留實驗導頁與探索操作', async () => {
    const experimentTries = Array.from({ length: 9 }, (_, index) => ({
      experimentId: index + 1, experimentTitle: `探索 ${index + 1}`, currentTry: '繼續觀察', explorationRecordCount: 2,
    }))
    const wrapper = render([], 0, 1, { activeExperimentCount: 12, experimentTries })
    await flushPromises()
    const panel = wrapper.get('.experiment-tries-panel')
    const works = wrapper.get('work-resume-stub')
    expect(panel.element.compareDocumentPosition(works.element) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(works.attributes('hideempty')).toBeDefined()
    expect(wrapper.findAll('.experiment-try-item')).toHaveLength(9)
    expect(panel.get('[aria-label="尚未落幕的探索數量：12"]').text()).toBe('12')
    expect(panel.findAll('.record-history-trigger')).toHaveLength(9)
    await wrapper.get('.experiment-try-main').trigger('click')
    expect(state.push).toHaveBeenCalledWith({ name: 'ExperimentDetail', params: { id: 1 } })
    wrapper.findComponent({ name: 'ExperimentQuickRecordAction' }).vm.$emit('select', 'DISCOVERY')
    await flushPromises()
    expect(wrapper.findComponent({ name: 'ExperimentExplorationEditor' }).props('experimentId')).toBe(1)
  })

  it('沒有展品時仍顯示提醒，並使用單欄版面', async () => {
    const wrapper = render([], 0, 0, { attentionSignals: [{ key: 'processing', cardCount: 2 }] })
    await flushPromises()
    expect(wrapper.find('#overview-treasure-title').exists()).toBe(false)
    expect(wrapper.get('.action-grid').classes()).toContain('action-grid--single')
    await wrapper.get('.attention-item button').trigger('click')
    expect(state.push).toHaveBeenCalledWith('/overview/cards')
  })

  it('沒有試煉與只有未來規劃的展品仍顯示成果摘要，不誤列為目前試煉', async () => {
    const wrapper = render([treasure({ description: '已完成的閱讀成果' }), treasure({ id: 2, activeTrial: challenge({ targetRank: 'GOLD' }) })], 8)
    await flushPromises()
    expect(wrapper.text()).toContain('已完成的閱讀成果')
    expect(wrapper.get('#overview-treasure-title').text()).toBe('入藏之後')
    expect(wrapper.get('[aria-label="入藏之後數量：8"]').text()).toBe('8')
    expect(wrapper.text()).toContain('完成於 2026-10-16')
    expect(wrapper.text()).not.toContain('展櫃仍然空著')
    expect(wrapper.findAll('.treasure-action-item')).toHaveLength(2)
    expect(wrapper.find('.treasure-challenge-main').exists()).toBe(false)
    expect(wrapper.find('.record-history-trigger').exists()).toBe(false)
    await wrapper.get('.treasure-output-controls button').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureYieldDialog' }).props('treasureId')).toBe(1)
    expect(state.push).not.toHaveBeenCalled()
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
    expect(main.get('.treasure-challenge-title').text()).toBe('銅級試煉進行中：邀請真實使用者試用')
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
    expect(wrapper.get('.record-history-trigger').attributes('aria-label')).toBe('查看 4 筆收益')
    expect(state.push).not.toHaveBeenCalled()
  })

  it('金級與已通過試煉仍顯示展品並可記錄收益，不虛構下一階試煉', async () => {
    const wrapper = render([treasure({ rank: 'GOLD' }), treasure({ id: 2, rank: 'BRONZE', activeTrial: challenge({ status: 'PASSED' }) })])
    await flushPromises()
    expect(wrapper.find('.treasure-challenge-main').exists()).toBe(false)
    expect(wrapper.findAll('.treasure-action-item')).toHaveLength(2)
    expect(wrapper.find('.treasure-empty-state').exists()).toBe(false)
    await wrapper.get('.treasure-output-controls button').trigger('click')
    expect(wrapper.findComponent({ name: 'TreasureYieldDialog' }).props('treasureId')).toBe(1)
  })

  it('作品總數獨立於最近作品清單，點擊導向未封存作品列表', async () => {
    const wrapper = render([], 0, 12)
    await flushPromises()
    const stat = wrapper.findAll('.current-stat').find(item => item.get('small').text() === '未封存作品')!
    expect(stat.get('strong').text()).toBe('12')
    await stat.trigger('click')
    expect(state.push).toHaveBeenCalledWith('/works')
  })
})
