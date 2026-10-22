import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import TreasureList from './TreasureList.vue'

const api = vi.hoisted(() => ({ list: vi.fn(), stats: vi.fn(), detail: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ push: vi.fn() }) }))
vi.mock('element-plus', () => ({ ElMessage: { error: vi.fn() } }))

describe('奇珍列表快速操作', () => {
  it('從卡片開啟共用產出與結果彈窗，儲存後重新讀取摘要', async () => {
    const trial = { id: 8, title: '邀請讀者試讀', status: 'PLANNED', targetRank: 'BRONZE' }
    const treasure = { id: 1, title: '小說', rank: 'STONE', description: null, coverImageUrl: null, isArchived: false, yieldCount: 2, activeTrial: trial }
    api.list.mockResolvedValue({ content: [treasure], totalElements: 1 })
    api.stats.mockResolvedValue({ total: 1, stone: 1, bronze: 0, silver: 0, gold: 0 })
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
    const wrapper = mount(TreasureList, { global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      PageHeader: { template: '<header><slot name="actions" /></header>' },
      TreasureRankBadge: { props: ['rank'], template: '<span>{{ rank }}</span>' },
      TreasureFormDialog: { name: 'TreasureFormDialog', props: ['modelValue', 'treasure'], template: '<aside v-if="modelValue" class="shared-form">{{ treasure?.title }}</aside>' },
      TreasureTrialDialog: { props: ['treasureId', 'initialRank', 'initialMode'], template: '<aside class="shared-trial">{{ treasureId }}:{{ initialRank }}:{{ initialMode }}</aside>' },
      TreasureYieldDialog: { props: ['treasureId'], emits: ['update:modelValue'], template: '<aside class="shared-yield">{{ treasureId }}<button @click="$emit(\'update:modelValue\', false)">關閉</button></aside>' },
      RouterLink: { props: ['to'], template: '<a :href="to"><slot /></a>' },
      'el-button': { template: '<button><slot /></button>' },
      'el-dropdown': { name: 'ElDropdown', emits: ['command'], template: '<div><slot /></div>' },
      'el-select': { template: '<div><slot /></div>' },
      'el-option': { template: '<div><slot /></div>' },
      'el-tooltip': { template: '<div><slot /></div>' },
      AppDialog: { props: ['modelValue'], template: '<aside v-if="modelValue"><slot /></aside>' },
      TreasureYieldHistory: { props: ['treasureId'], template: '<aside class="shared-history">{{ treasureId }}</aside>' },
      'el-tag': { template: '<span><slot /></span>' },
      'el-input': true, 'el-radio-group': { name: 'ElRadioGroup', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' }, 'el-dropdown-menu': true,
      'el-pagination': true, 'el-result': true, AppEmptyState: true,
    } } })
    try {
      await vi.waitFor(() => expect(wrapper.text()).toContain('邀請讀者試讀'))
      expect(wrapper.text()).not.toContain('最近活動於')
      expect(wrapper.get('.output-count').text()).toBe('2')
      await wrapper.get('.output-count').trigger('click')
      expect(wrapper.get('.shared-history').text()).toBe('1')
      await wrapper.findAll('button').find(button => button.text() === '＋記收益')!.trigger('click')
      expect(wrapper.get('.shared-yield').text()).toContain('1')
      await wrapper.get('.shared-yield button').trigger('click')
      await wrapper.findAll('button').find(button => button.text() === '更新')!.trigger('click')
      expect(wrapper.get('.shared-trial').text()).toBe('1:BRONZE:attempt')
      api.list.mockResolvedValue({ content: [{ ...treasure, yieldCount: 3, activeTrial: { ...trial, status: 'ATTEMPTED' } }], totalElements: 1 })
      await client.invalidateQueries({ queryKey: ['treasures'] })
      await vi.waitFor(() => expect(wrapper.get('.output-count').text()).toBe('3'))
      expect(wrapper.text()).toContain('已嘗試')
      api.list.mockResolvedValue({ content: [{ ...treasure, yieldCount: 0, activeTrial: null }], totalElements: 1 })
      await client.invalidateQueries({ queryKey: ['treasures'] })
      await vi.waitFor(() => expect(wrapper.find('.treasure-card-footer').exists()).toBe(false))
      expect(wrapper.find('.output-count').exists()).toBe(false)
      expect(wrapper.find('.quick-output').exists()).toBe(true)
      api.detail.mockResolvedValue({ ...treasure, title: '完整成果內容' })
      wrapper.findComponent({ name: 'ElDropdown' }).vm.$emit('command', 'edit')
      await vi.waitFor(() => expect(wrapper.get('.shared-form').text()).toBe('完整成果內容'))
      expect(api.detail).toHaveBeenCalledWith(1)
      wrapper.findComponent({ name: 'TreasureFormDialog' }).vm.$emit('update:modelValue', false)
      await wrapper.vm.$nextTick()
      api.list.mockResolvedValue({ content: [{ ...treasure, isArchived: true }], totalElements: 1 })
      wrapper.findComponent({ name: 'ElRadioGroup' }).vm.$emit('update:modelValue', 'true')
      await wrapper.get('form').trigger('submit')
      await vi.waitFor(() => expect(api.list).toHaveBeenCalledWith(expect.objectContaining({ archived: true })))
      await wrapper.get('.quick-output').trigger('click')
      expect(wrapper.get('.shared-yield').text()).toContain('1')
      await wrapper.get('.output-count').trigger('click')
      expect(wrapper.findAll('.history-toolbar button').some(button => button.text() === '＋記收益')).toBe(true)
    } finally { wrapper.unmount(); client.clear() }
  })
})
