import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OverviewRecent from './OverviewRecent.vue'
import type { TreasureEvent } from '../../types/treasure'

const state = vi.hoisted(() => ({ recent: vi.fn(), push: vi.fn() }))
vi.mock('../../utils/api/overviewApi', () => ({ overviewApi: { getRecentOverview: state.recent } }))
vi.mock('vue-router', async importOriginal => ({
  ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ push: state.push }),
}))
const wrappers: ReturnType<typeof mount>[] = []
const clients: QueryClient[] = []
function render(yields: TreasureEvent[] = [], progress: TreasureEvent[] = []) {
  state.recent.mockResolvedValue({ period: {
    flow: { reengagedCardCount: 0, reviewedCardCount: 0 },
    treasureGrowth: { collectedCount: 0, promotedTreasureCount: 0, yieldTreasureCount: 0, yieldRecordCount: 0 },
    recentTreasureYields: yields, recentTreasureProgress: progress, activities: [], recentExperimentMaterials: [],
  } })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(OverviewRecent, { global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' }, OverviewNavigation: true,
      'el-radio-group': { name: 'ElRadioGroup', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
      'el-radio-button': true, 'el-tag': { template: '<span><slot /></span>' }, 'el-icon': true,
      'el-tooltip': true, 'el-result': true,
    },
  } })
  wrappers.push(wrapper)
  return wrapper
}

describe('展品近期資訊依用途分區', () => {
  beforeEach(() => vi.clearAllMocks())
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); clients.splice(0).forEach(client => client.clear()) })

  it('價值產出與入藏檢定分開呈現，各自有日期、標題與成果入口', async () => {
    const wrapper = render([
      { treasureId: 1, treasureTitle: '熱氣球', kind: 'YIELD', eventTitle: '收到使用回饋', eventAt: '2026-10-16T00:00:00Z', rank: null },
    ], [
      { treasureId: 2, treasureTitle: '貓咪戰士', kind: 'COLLECTED', eventTitle: '收入館藏', eventAt: '2026-10-15T00:00:00Z', rank: null },
      { treasureId: 1, treasureTitle: '熱氣球', kind: 'PROMOTED', eventTitle: '完成正式投稿', eventAt: '2026-10-14T00:00:00Z', rank: 'BRONZE' },
      { treasureId: 1, treasureTitle: '熱氣球', kind: 'TRIAL_ATTEMPTED', eventTitle: '邀請使用者試用', eventAt: '2026-10-13T00:00:00Z', rank: null },
    ])
    await flushPromises()
    const yieldSection = wrapper.get('[data-treasure-section="yields"]')
    const progressSection = wrapper.get('[data-treasure-section="progress"]')
    expect(yieldSection.text()).toContain('新的收穫')
    expect(yieldSection.text()).toContain('近 30 天・最近 1 筆')
    expect(yieldSection.get('h3').text()).toBe('收到使用回饋')
    expect(yieldSection.get('.treasure-record-source').text()).toBe('熱氣球')
    expect(yieldSection.get('time').attributes('datetime')).toBe('2026-10-16T00:00:00Z')
    expect(yieldSection.get('.treasure-record-main').attributes('href')).toBe('/treasures/1')
    expect(yieldSection.text()).not.toContain('完成正式投稿')
    expect(progressSection.text()).toContain('近期入藏與試煉')
    expect(progressSection.text()).toContain('近 30 天・最近 3 筆')
    const progressItems = progressSection.findAll('.content-card')
    expect(progressItems[0]!.get('h3').text()).toBe('貓咪戰士')
    expect(progressItems[0]!.text().match(/收入館藏/g)).toHaveLength(1)
    expect(progressItems[0]!.find('.treasure-record-source').exists()).toBe(false)
    expect(progressItems[1]!.text()).toContain('晉級至銅級')
    expect(progressItems[2]!.text()).toContain('嘗試紀錄')
    expect(progressSection.text()).not.toContain('收到使用回饋')
  })

  it('空期間保留說明與館藏入口，切換期間同步查詢與範圍', async () => {
    const wrapper = render([])
    await flushPromises()
    expect(wrapper.text()).toContain('這段時間尚無新的收益紀錄')
    expect(wrapper.text()).toContain('這段時間還沒有新的入藏、試煉嘗試或晉級紀錄')
    expect(wrapper.findAll('.treasure-gallery-link').every(link => link.attributes('href') === '/treasures')).toBe(true)
    wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 7)
    await flushPromises()
    expect(state.recent).toHaveBeenLastCalledWith(7)
    expect(wrapper.text()).toContain('近 7 天')
    expect(wrapper.text()).not.toContain('最近 0 筆')
  })

  it('一區沒有資料時，不隱藏另一區的紀錄', async () => {
    const wrapper = render([
      { treasureId: 1, treasureTitle: '熱氣球', kind: 'YIELD', eventTitle: '讀者回應', eventAt: '2026-10-16T00:00:00Z', rank: null },
    ])
    await flushPromises()
    expect(wrapper.get('[data-treasure-section="yields"]').text()).toContain('讀者回應')
    expect(wrapper.get('[data-treasure-section="progress"]').text()).toContain('這段時間還沒有新的入藏')
  })
})
