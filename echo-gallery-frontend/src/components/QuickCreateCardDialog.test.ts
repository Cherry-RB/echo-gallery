import { computed, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuickCreateCardDialog from './QuickCreateCardDialog.vue'

const createCard = vi.fn()

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../utils/useCardStatus', () => ({
  useCardStatus: () => ({
    handleCreateCard: createCard,
    isCreatePending: ref(false),
  }),
}))

vi.mock('../utils/composables/useTags', () => ({
  useTags: () => ({
    tagPopoverVisible: ref(false),
    tagSearchQuery: ref(''),
    filteredExistingTags: computed(() => []),
    handleToggleSelectTag: vi.fn(),
    handleCloseTag: vi.fn(),
    handleConfirmAddTag: vi.fn(),
  }),
}))

describe('QuickCreateCardDialog', () => {
  beforeEach(() => vi.clearAllMocks())

  const mountDialog = () => mount(QuickCreateCardDialog, {
    props: { modelValue: true },
    global: {
      stubs: {
        'el-dialog': {
          props: ['title'],
          template: '<section><h2>{{ title }}</h2><slot /><footer><slot name="footer" /></footer></section>',
        },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': {
          props: ['label'],
          template: '<label><span>{{ label }}</span><slot /></label>',
        },
        'el-input': true,
        'el-input-number': true,
        'el-radio-group': { template: '<div><slot /></div>' },
        'el-radio-button': { template: '<button><slot /></button>' },
        'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
        'el-icon': { template: '<span><slot /></span>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-popover': true,
      },
    },
  })

  it('預設直接提供標題、完整內容、標籤、類型與回流設定', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('標題')
    expect(wrapper.text()).toContain('延伸筆記／完整內容')
    expect(wrapper.text()).toContain('標籤')
    expect(wrapper.text()).toContain('卡片類型')
    expect(wrapper.text()).toContain('回流天數')
    expect(wrapper.text()).toContain('7 天')
    expect(wrapper.text()).toContain('10 天')
    expect(wrapper.text()).toContain('30 天')
  })

  it('可直接選擇常用回流天數，不需要先展開設定', async () => {
    const wrapper = mountDialog()
    const intervalButtons = wrapper.findAll('.quick-interval-button')

    await intervalButtons[2].trigger('click')

    expect(intervalButtons[2].classes()).toContain('active')
    expect(wrapper.find('.create-details-toggle').exists()).toBe(false)
  })

  it('暫停回流後顯示清楚的選取狀態', async () => {
    const wrapper = mountDialog()
    const pauseButton = wrapper.get('.pause-recurrence-button')

    await pauseButton.trigger('click')

    expect(pauseButton.text()).toBe('已暫停')
    expect(pauseButton.classes()).toContain('active')
    expect(pauseButton.attributes('aria-pressed')).toBe('true')
  })
})
