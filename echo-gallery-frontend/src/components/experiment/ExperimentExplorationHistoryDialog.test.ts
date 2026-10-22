import { mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { workUseTarget } from '../../utils/workFlow'
import ExperimentExplorationHistoryDialog from './ExperimentExplorationHistoryDialog.vue'
vi.mock('../../utils/api/workApi', () => ({ workApi: { usages: vi.fn().mockResolvedValue([]) } }))
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => { workUseTarget.value = undefined })
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); workUseTarget.value = undefined })

const records = [{
  id: 12,
  tryText: '晚餐後畫兩分鐘',
  discovery: '開始前的阻力比畫畫本身更大',
  createdAt: '2026-10-02T06:30:00Z',
  exports: [{
    cardId: 8,
    cardTitle: '降低開始阻力',
    exportedAt: '2026-10-02T07:00:00Z',
  }],
}, {
  id: 11,
  tryText: null,
  discovery: '直接記下來也能保留線索',
  createdAt: '2026-10-01T06:30:00Z',
  exports: [],
}]

const mountDialog = () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(ExperimentExplorationHistoryDialog, {
  props: { modelValue: true, records },
  global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]],
    stubs: {
      'el-dialog': {
        props: ['modelValue', 'title'],
        template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><slot name="footer" /></section>',
      },
      'el-button': { emits: ['click'], template: '<button @click="$emit(\'click\', $event)"><slot /></button>' },
      'el-skeleton': true,
      'el-tooltip': { template: '<span><slot /></span>' },
      'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
      'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': { template: '<button><slot /></button>' },
      'el-icon': true,
      RouterLink: { template: '<a><slot /></a>' },
    },
  },
  })
  wrappers.push(wrapper); return wrapper
}

describe('ExperimentExplorationHistoryDialog', () => {
  it('用於作品時切換到單一作品流程，關閉後返回原歷程', async () => {
    const wrapper = mountDialog()
    wrapper.findAllComponents({ name: 'HistoryEvent' })[0]!.vm.$emit('command', 'use-work')
    await wrapper.vm.$nextTick()
    expect(workUseTarget.value?.recordIds).toEqual([12])
    expect(wrapper.text()).not.toContain('探索紀錄')
    workUseTarget.value = undefined
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('整理成卡片')
  })
  it('以同層欄位顯示試法、發現與整理結果', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('探索紀錄')
    expect(wrapper.text()).toContain('試法')
    expect(wrapper.text()).toContain('晚餐後畫兩分鐘')
    expect(wrapper.text()).toContain('發現')
    expect(wrapper.text()).toContain('開始前的阻力比畫畫本身更大')
    expect(wrapper.text()).toContain('整理結果')
    expect(wrapper.text()).toContain('降低開始阻力')
  })

  it('沒有試法時顯示明確的缺省語意', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('這次直接留下發現。')
    expect(wrapper.text()).toContain('直接記下來也能保留線索')
  })

  it('可從歷史瀏覽進入整理流程', async () => {
    const wrapper = mountDialog()

    await wrapper.findAll('button').find(button => button.text().includes('整理成卡片'))?.trigger('click')

    expect(wrapper.emitted('organize')).toBeTruthy()
  })

  it('有整理操作及空歷程時皆提供底部關閉入口', async () => {
    const wrapper = mountDialog()
    await wrapper.findAll('button').find(button => button.text() === '關閉')!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]])
    await wrapper.setProps({ records: [] })
    expect(wrapper.findAll('button').some(button => button.text() === '整理成卡片')).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '關閉')!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toEqual([[false], [false]])
    wrapper.unmount()
  })

  it('歷程沿用快速紀錄元件並轉送原有試法與發現模式', () => {
    const wrapper = mountDialog()
    const action = wrapper.findComponent({ name: 'ExperimentQuickRecordAction' })
    action.vm.$emit('select', 'TRY')
    action.vm.$emit('select', 'DISCOVERY')
    expect(wrapper.emitted('record')).toEqual([['TRY'], ['DISCOVERY']])
    expect(action.text()).toContain('＋ 試法')
    expect(action.text()).toContain('＋ 發現')
  })
})
