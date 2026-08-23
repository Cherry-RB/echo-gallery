import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'
import CardTextMode from './CardTextMode.vue'
import type { CardDto } from '../types/card'

describe('CardTextMode', () => {
  it('看板卡片不顯示 nextShowAt', () => {
    const card: CardDto = {
      id: '1', type: 'note', title: '測試卡片', tags: [], showContentPreview: false,
      intervalDays: 10, nextShowAt: '2099-12-31T00:00:00Z', openCount: 0,
      likeCount: 0, isArchived: false, growthStatus: 'UNMARKED', createdAt: '', updatedAt: '',
    }
    const wrapper = mount(CardTextMode, {
      props: { data: card, boardType: 'all' },
      global: {
        plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }]],
        stubs: {
          'el-card': { template: '<div><slot /></div>' },
          'el-button': true,
          'el-dropdown': true,
          'el-tag': true,
          'el-tooltip': true,
          'el-icon': true,
        },
      },
    })
    expect(wrapper.text()).not.toContain('2099-12-31')
  })
})
