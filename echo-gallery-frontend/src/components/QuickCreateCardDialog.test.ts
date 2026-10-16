import { computed, ref } from 'vue'
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuickCreateCardDialog from './QuickCreateCardDialog.vue'
import CoverImageField from './ui/CoverImageField.vue'
import AppDialog from './AppDialog.vue'

const createCard = vi.fn()
const createPending = ref(false)

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('../utils/useCardStatus', () => ({
  useCardStatus: () => ({
    handleCreateCard: createCard,
    isCreatePending: createPending,
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
  beforeEach(() => { vi.clearAllMocks(); createPending.value = false })

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
        'el-checkbox': true,
        'el-radio-group': { template: '<div><slot /></div>' },
        'el-radio-button': { template: '<button><slot /></button>' },
        'el-button': { template: '<button type="button" @click="$emit(\'click\')"><slot /></button>' },
        'el-icon': { template: '<span><slot /></span>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-popover': true,
      },
    },
  })

  it('預設提供卡片筆記，並將更多內容列為按需補充', () => {
    const wrapper = mountDialog()

    expect(wrapper.text()).toContain('標題')
    expect(wrapper.text()).toContain('卡片筆記')
    expect(wrapper.text()).toContain('更多內容')
    expect(wrapper.text()).toContain('按需補充')
    expect(wrapper.text()).toContain('標籤')
    expect(wrapper.text()).toContain('卡片類型')
    expect(wrapper.text()).toContain('回流天數')
    expect(wrapper.text()).toContain('7 天')
    expect(wrapper.text()).toContain('10 天')
    expect(wrapper.text()).toContain('30 天')
  })

  it('建立中不能由取消或關閉回呼離開，填寫階段停用遮罩關閉', async () => {
    const wrapper = mountDialog()
    const dialog = wrapper.findComponent(AppDialog)
    expect(dialog.props('closeOnClickModal')).toBe(false)
    createPending.value = true
    await wrapper.vm.$nextTick()
    expect(dialog.props('busy')).toBe(true)
    const done = vi.fn()
    ;(dialog.props('beforeClose') as (done: () => void) => void)(done)
    expect(done).not.toHaveBeenCalled()
    await wrapper.findAll('footer button').find(button => button.text() === '取消')!.trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
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

  it('按需補充封面後共用預覽與載入失敗提示，不清除輸入', async () => {
    const wrapper = mountDialog()
    await wrapper.findAll('button').find(button => button.text() === '封面圖片')!.trigger('click')
    const field = wrapper.findComponent(CoverImageField)
    field.findComponent({ name: 'ElInput' }).vm.$emit('update:modelValue', 'https://example.com/cover.png')
    await wrapper.vm.$nextTick()
    await field.get('img').trigger('error')
    expect(field.text()).toContain('無法載入圖片')
    expect(field.props('modelValue')).toBe('https://example.com/cover.png')
    wrapper.unmount()
  })
})
