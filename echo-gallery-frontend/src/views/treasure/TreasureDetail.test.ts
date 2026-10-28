import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TreasureDetail from './TreasureDetail.vue'

const api = vi.hoisted(() => ({ detail: vi.fn(), route: vi.fn(), yields: vi.fn(), relations: vi.fn(), attempts: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRoute: () => ({ params: { id: '1' } }), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() }, ElMessageBox: { confirm: vi.fn() }, ElCollapseTransition: { template: '<div><slot /></div>' } }))

const trial = { id: 4, title: '在新情境使用', criteria: '完成一次實際運用並留下紀錄', targetRank: 'BRONZE', status: 'PLANNED', createdAt: '2026-10-14T10:00:00Z', resultNote: null, attemptedAt: null, passedAt: null }
const detail = { id: 1, title: '完成的 MVP', rank: 'STONE', nextRank: 'BRONZE', description: '成果內容', acquiredOn: '2026-10-14', createdAt: '2026-10-14T10:00:00Z', updatedAt: '2026-10-14T10:00:00Z', sourceTreasures: [{ id: 2, title: '來源成果' }], sourceCard: null, sourceExperiment: null, trialCount: 1, yieldCount: 0, activeTrial: trial, isArchived: false }
const page = (content: unknown[]) => ({ content, totalElements: content.length, totalPages: 1, page: 0, size: 10 })
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
function render(realRoute = false) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  const wrapper = mount(TreasureDetail, { global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
    TreasureFormDialog: true,
    TreasureTrialDialog: { name: 'TreasureTrialDialog', props: ['modelValue', 'initialMode', 'initialRank'], emits: ['update:modelValue'], template: '<aside v-if="modelValue" class="trial-dialog">{{ initialMode }}:{{ initialRank }}</aside>' },
    TreasureYieldHistory: { props: ['treasureId', 'archived'], template: '<aside class="shared-yields">{{ treasureId }}:{{ archived }}</aside>' },
    TreasureChallengeRoute: realRoute ? false : { props: ['trials', 'archived', 'modelValue'], emits: ['edit', 'attempt', 'help'], template: `<section class="shared-route" :data-viewed="modelValue"><button @click="$emit('help')">說明</button><button v-if="!archived" @click="$emit('edit', 'SILVER')">設定銀級</button></section>` },
    TreasureRankBadge: { props: ['rank'], template: '<span>{{ rank }}</span>' },
    ExpandableText: { props: ['content'], template: '<p>{{ content }}</p>' },
    AppEmptyState: { props: ['description'], template: '<p>{{ description }}<slot /></p>' },
    AppDialog: { props: ['modelValue'], template: '<aside v-if="modelValue"><div class="el-dialog__body"><slot /></div><slot name="footer" /></aside>' },
    RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
    'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'el-dropdown': { template: '<div><slot /></div>' },
    'el-dropdown-menu': true, 'el-dropdown-item': true,
    'el-tag': { template: '<span><slot /></span>' },
    'el-icon': true, 'el-pagination': true, 'el-result': true,
  } } })
  wrappers.push(wrapper)
  return wrapper
}

describe('展品詳情的操作脈絡', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.detail.mockResolvedValue(detail)
    api.route.mockResolvedValue([trial])
    api.yields.mockResolvedValue(page([]))
    api.relations.mockResolvedValue(page([]))
    api.attempts.mockResolvedValue(page([]))
  })
  afterEach(() => { wrappers.splice(0).forEach(wrapper => wrapper.unmount()); clients.splice(0).forEach(client => client.clear()) })

  it('實際成長區塊收合後，設定其他等級與記錄都不改變展開狀態', async () => {
    const wrapper = render(true)
    await vi.waitFor(() => expect(wrapper.find('[data-rank="BRONZE"] .stage-view').exists()).toBe(true))
    const bronze = '[data-rank="BRONZE"]'
    await wrapper.get(`${bronze} .stage-view`).trigger('click')
    expect(wrapper.findAll('.stage-view[aria-expanded="true"]')).toHaveLength(0)
    const silverButtons = wrapper.findAll('[data-rank="SILVER"] .stage-actions button')
    await silverButtons.find(button => button.text() === '設定')!.trigger('click')
    expect(wrapper.get('.trial-dialog').text()).toBe('edit:SILVER')
    expect(wrapper.findAll('.stage-view[aria-expanded="true"]')).toHaveLength(0)
    wrapper.findComponent({ name: 'TreasureTrialDialog' }).vm.$emit('update:modelValue', false)
    await wrapper.vm.$nextTick()
    await wrapper.findAll(`${bronze} .stage-actions button`).find(button => button.text() === '＋記錄')!.trigger('click')
    expect(wrapper.get('.trial-dialog').text()).toBe('attempt:BRONZE')
    expect(wrapper.findAll('.stage-view[aria-expanded="true"]')).toHaveLength(0)
  })

  it('查看其他等級時，開啟記錄及晉級資料刷新仍保留查看選擇', async () => {
    const wrapper = render(true)
    await vi.waitFor(() => expect(wrapper.find('[data-rank="SILVER"] .stage-view').exists()).toBe(true))
    await wrapper.get('[data-rank="SILVER"] .stage-view').trigger('click')
    await wrapper.findAll('[data-rank="BRONZE"] .stage-actions button').find(button => button.text() === '＋記錄')!.trigger('click')
    expect(wrapper.get('[data-rank="SILVER"] .stage-view').attributes('aria-expanded')).toBe('true')
    clients[clients.length - 1]!.setQueryData(['treasures', 'detail', 1], { ...detail, rank: 'BRONZE', nextRank: 'SILVER' })
    await wrapper.vm.$nextTick()
    expect(wrapper.get('[data-rank="SILVER"] .stage-view').attributes('aria-expanded')).toBe('true')
  })

  it('詳情使用完整路線，說明與設定開啟同一個流程元件', async () => {
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.shared-route').exists()).toBe(true))
    await wrapper.findAll('.shared-route button')[1]!.trigger('click')
    expect(wrapper.get('.trial-dialog').text()).toBe('edit:SILVER')
    expect(wrapper.get('.shared-route').attributes('data-viewed')).toBe('BRONZE')
    await wrapper.findAll('.shared-route button')[0]!.trigger('click')
    expect(wrapper.get('.trial-dialog').text()).toBe('help:SILVER')
    expect(wrapper.get('.shared-yields').text()).toBe('1:false')
    expect(wrapper.find('.detail-main-column .detail-content').exists()).toBe(true)
    expect(wrapper.find('.detail-main-column .yield-panel .shared-yields').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('查看等級說明')
    expect(api.route).toHaveBeenCalledWith(1)
  })

  it('封存狀態傳入路線與共用產出歷程，不提供新增設定操作', async () => {
    api.detail.mockResolvedValue({ ...detail, isArchived: true })
    const wrapper = render()
    await vi.waitFor(() => expect(wrapper.find('.shared-route').exists()).toBe(true))
    expect(wrapper.findAll('.shared-route button')).toHaveLength(1)
    expect(wrapper.get('.shared-yields').text()).toBe('1:true')
    expect(wrapper.text()).toContain('來源成果')
  })
})
