import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ExperimentExplorationHistoryDialog from './ExperimentExplorationHistoryDialog.vue'

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

const mountDialog = () => mount(ExperimentExplorationHistoryDialog, {
  props: { modelValue: true, records },
  global: {
    stubs: {
      'el-dialog': {
        props: ['modelValue', 'title'],
        template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><slot name="footer" /></section>',
      },
      'el-button': { template: '<button @click="$emit(\'click\')"><slot /></button>' },
      'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' },
      'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': { template: '<button><slot /></button>' },
      'el-icon': true,
    },
  },
})

describe('ExperimentExplorationHistoryDialog', () => {
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
})
