import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TreasureFormDialog from './TreasureFormDialog.vue'
import TreasureYieldDialog from './TreasureYieldDialog.vue'
import TreasureSourcePicker from './TreasureSourcePicker.vue'
import CoverImageField from '../ui/CoverImageField.vue'

const api = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), update: vi.fn(), passTrial: vi.fn(), createTrial: vi.fn(), updateTrial: vi.fn(), createYield: vi.fn(), updateYield: vi.fn(), confirm: vi.fn(), cards: vi.fn(), experiments: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { searchCards: api.cards } }))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: { getExperiments: api.experiments } }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn() }, ElMessageBox: { confirm: api.confirm } }))

const stubs = {
  ExpandableText: { props: ['content'], template: '<p>{{ content }}</p>' },
  CardPickerDialog: { props: ['modelValue'], emits: ['confirm', 'update:modelValue'], template: '<aside v-if="modelValue" class="shared-picker"><button class="confirm-card" @click="$emit(\'confirm\', { id: \'40\', title: \'另一張相關卡片\' })">選擇卡片</button><button class="cancel-picker" @click="$emit(\'update:modelValue\', false)">取消選擇</button></aside>' },
  AppDialog: { name: 'AppDialog', props: ['modelValue', 'title', 'beforeClose'], template: '<section v-if="modelValue"><h2>{{ title }}</h2><div class="el-dialog__body"><slot /></div><footer><slot name="footer" /></footer></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<label><span>{{ label }}</span><slot /></label>' },
  'el-input': { props: ['modelValue', 'type', 'disabled'], emits: ['update:modelValue'], template: '<textarea v-if="type === \'textarea\'" :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else :value="modelValue" :disabled="disabled" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-date-picker': { props: ['modelValue'], emits: ['update:modelValue'], template: '<input class="date-input" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { props: ['modelValue'], emits: ['update:modelValue', 'change'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value); $emit(\'change\', $event.target.value)"><slot /></select>' },
  'el-option': { props: ['label', 'value', 'disabled'], template: '<option :value="value" :disabled="disabled">{{ label }}</option>' },
  'el-radio-group': { name: 'ElRadioGroup', props: ['modelValue'], emits: ['update:modelValue'], template: '<div><slot /></div>' },
  'el-radio-button': { template: '<span><slot /></span>' },
  'el-radio': true, 'el-skeleton': true,
  'el-tabs': { name: 'ElTabs', props: ['modelValue'], emits: ['update:modelValue'], template: '<nav><slot /></nav>' },
  'el-tab-pane': { props: ['label', 'name'], template: '<span>{{ label }}</span>' },
  'el-checkbox': { props: ['modelValue'], emits: ['update:modelValue', 'change'], template: '<button type="button" class="source-check" :aria-pressed="modelValue" @click="$emit(\'update:modelValue\', !modelValue); $emit(\'change\', !modelValue)"><slot /></button>' },
  'el-tag': { template: '<span class="source-tag"><slot /></span>' },
  'el-pagination': { props: ['currentPage'], emits: ['current-change'], template: '<button type="button" class="next-page" @click="$emit(\'current-change\', currentPage + 1)">下一頁</button>' },
  'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}<slot /></p>' },
  'el-button': { props: ['disabled', 'loading'], emits: ['click'], template: '<button type="button" :disabled="disabled || loading" @click="$emit(\'click\')"><slot /></button>' },
}
const clients: QueryClient[] = []
function global() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } })
  clients.push(client)
  return { plugins: [[VueQueryPlugin, { queryClient: client }] as [typeof VueQueryPlugin, { queryClient: QueryClient }]], stubs }
}
const page = (content: Array<{ id: number | string; title: string }>, total = content.length) => ({ content, page: 0, size: 10, totalElements: total, totalPages: Math.ceil(total / 10) })

describe('奇珍表單互動', () => {
  beforeEach(() => {
    clients.forEach(client => client.clear()); clients.length = 0
    vi.clearAllMocks()
    api.list.mockImplementation(({ page: index }) => Promise.resolve(page([{ id: index ? 2 : 1, title: index ? '來源 B' : '來源 A' }], 12)))
    api.cards.mockResolvedValue(page([{ id: '10', title: '來源卡片' }]))
    api.experiments.mockResolvedValue(page([]))
  })

  it('跨頁選取來源後收入新成果，來源選取不遺失且不傳入等級', async () => {
    api.create.mockResolvedValue({ id: 3, title: '完成的新成果' })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    expect(wrapper.get('[data-source-manager]').text()).toBe('選擇來源')
    expect(wrapper.find('.source-summaries').exists()).toBe(false)
    expect(wrapper.get('.display-settings summary').text()).toContain('作品與封面')
    await wrapper.get('input').setValue('完成的新成果')
    await wrapper.get('[data-source-manager]').trigger('click')
    expect(wrapper.findComponent({ name: 'PickerWorkspace' }).exists()).toBe(true)
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.get('.load-more-row button').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.source-options .picker-option')).toHaveLength(2))
    expect(wrapper.findAll('.source-options .picker-option')[1]!.text()).toContain('來源 B')
    await wrapper.findAll('.source-options .picker-option')[1]!.trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '確認來源')!.trigger('click')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledOnce())
    expect(api.create.mock.calls[0]?.[0]).toMatchObject({ title: '完成的新成果', sourceTreasureIds: [1, 2], sourceCardId: null })
    expect(api.create.mock.calls[0]?.[0]).not.toHaveProperty('rank')
    await flushPromises()
    expect(wrapper.emitted('saved')).toHaveLength(1)
    wrapper.unmount()
  })

  it('來源循環被後端拒絕時保留輸入與開啟狀態', async () => {
    api.create.mockRejectedValue({ response: { data: { message: '此來源會造成循環關聯' } } })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('仍要保留的成果')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('此來源會造成循環關聯'))
    expect(wrapper.get('input').element.value).toBe('仍要保留的成果')
    expect(wrapper.emitted('saved')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('取消來源管理不改變成果草稿，確認來源後也不立即儲存成果', async () => {
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('保留的成果')
    await wrapper.get('[data-source-manager]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '取消變更')!.trigger('click')
    expect(wrapper.findComponent(TreasureSourcePicker).exists()).toBe(false)
    expect(wrapper.findAll('.el-dialog__body')).toHaveLength(1)
    expect(wrapper.find('.treasure-sources').exists()).toBe(false)
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('保留的成果')
    await wrapper.get('[data-source-manager]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.findAll('button').find(b => b.text() === '確認來源')!.trigger('click')
    expect(wrapper.get('.treasure-sources').text()).toContain('來源 A')
    expect(api.create).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('價值產出更正保留實際發生日期，不改寫為今天', async () => {
    api.updateYield.mockResolvedValue({ id: 9 })
    const wrapper = mount(TreasureYieldDialog, { props: { modelValue: true, treasureId: 1, yield: { id: 9, title: '再次運用', description: '真實經驗', occurredOn: '2026-08-10', relatedCard: null, createdAt: '', updatedAt: '' } }, global: global() })
    await wrapper.get('input').setValue('更正實際經驗')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.updateYield).toHaveBeenCalledOnce())
    expect(api.updateYield).toHaveBeenCalledWith(1, 9, expect.objectContaining({ title: '更正實際經驗', occurredOn: '2026-08-10' }))
    expect(api.createYield).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('同一彈窗跨來源種類選取保留奇珍多選，卡片與實驗仍為單選', async () => {
    api.experiments.mockResolvedValue(page([{ id: 21, title: '實驗 A' }, { id: 22, title: '實驗 B' }]))
    api.create.mockResolvedValue({ id: 3, title: '完成的新成果' })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('跨來源成果')
    await wrapper.get('[data-source-manager]').trigger('click')
    expect(wrapper.findAll('.el-dialog__body')).toHaveLength(1)
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    const picker = wrapper.findComponent(TreasureSourcePicker)
    picker.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'card')
    await vi.waitFor(() => expect(api.cards).toHaveBeenCalled())
    await flushPromises()
    await picker.get('.source-options .picker-option').trigger('click')
    await flushPromises()
    picker.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'experiment')
    await vi.waitFor(() => expect(api.experiments).toHaveBeenCalled())
    await flushPromises()
    await picker.findAll('.source-options .picker-option')[0]!.trigger('click')
    await flushPromises()
    await picker.findAll('.source-options .picker-option')[1]!.trigger('click')
    await flushPromises()
    picker.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'treasure')
    await vi.waitFor(() => expect(wrapper.get('.source-options .picker-option').attributes('aria-pressed')).toBe('true'))
    await wrapper.findAll('button').find(b => b.text() === '確認來源')!.trigger('click')
    expect(wrapper.findAll('.source-summaries .selection-summary')).toHaveLength(3)
    expect(wrapper.get('.treasure-sources').text()).toContain('來源 A')
    expect(wrapper.get('.source-summaries').text()).toContain('實驗 B')
    expect(wrapper.get('.source-summaries').text()).not.toContain('實驗 A')
    expect(api.create).not.toHaveBeenCalled()
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ sourceCardId: 10, sourceExperimentId: 22, sourceTreasureIds: [1] })))
    wrapper.unmount()
  })

  it('來源模式關閉時返回原表單，保留成果草稿與原來源', async () => {
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('尚未儲存的成果')
    await wrapper.get('[data-source-manager]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    const done = vi.fn()
    const dialog = wrapper.findComponent({ name: 'AppDialog' })
    ;(dialog.props('beforeClose') as (callback: () => void) => void)(done)
    await flushPromises()
    expect(done).not.toHaveBeenCalled()
    expect(wrapper.findComponent(TreasureSourcePicker).exists()).toBe(false)
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('尚未儲存的成果')
    expect(wrapper.find('.treasure-sources').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(api.create).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('完整分享過程可記成一筆，不需要先替價值產出分類', async () => {
    api.createYield.mockResolvedValue({ id: 10 })
    const wrapper = mount(TreasureYieldDialog, { props: { modelValue: true, treasureId: 1 }, global: global() })
    await wrapper.get('input').setValue('整理 MVP 成文章並分享')
    await wrapper.get('textarea').setValue('整理專案、撰寫文章、發布；尚未收到回饋。')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.createYield).toHaveBeenCalledOnce())
    expect(api.createYield).toHaveBeenCalledWith(1, expect.objectContaining({ title: '整理 MVP 成文章並分享', description: '整理專案、撰寫文章、發布；尚未收到回饋。' }))
    expect(api.cards).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('封面格式錯誤阻止提交，載入失敗只提示並保留網址與成果', async () => {
    api.create.mockResolvedValue({ id: 3 })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('保存成果')
    const field = wrapper.findComponent(CoverImageField)
    await field.get('input').setValue('不是網址')
    await wrapper.get('footer button:last-child').trigger('click')
    expect(api.create).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('僅接受 HTTP 或 HTTPS')
    await field.get('input').setValue('https://example.com/unavailable.png')
    await field.get('img').trigger('error')
    expect(wrapper.text()).toContain('無法載入圖片')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ coverImageUrl: 'https://example.com/unavailable.png' })))
    wrapper.unmount()
  })

  it('成果說明計數與來源移除不影響其他草稿欄位', async () => {
    api.create.mockResolvedValue({ id: 3 })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('有來源的成果')
    await wrapper.get('textarea').setValue('A😀')
    expect(wrapper.get('.app-field-counter').text()).toContain('2 / 50000')
    await wrapper.get('[data-source-manager]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.findAll('button').find(button => button.text() === '確認來源')!.trigger('click')
    await wrapper.get('[aria-label="移除來源奇珍：來源 A"]').trigger('click')
    expect(wrapper.find('.treasure-sources').exists()).toBe(false)
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('A😀')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ description: 'A😀', sourceTreasureIds: [] })))
    wrapper.unmount()
  })

  it('相關卡片使用完整摘要且可移除，保存時不保留舊關聯', async () => {
    api.updateYield.mockResolvedValue({ id: 9 })
    const wrapper = mount(TreasureYieldDialog, { props: { modelValue: true, treasureId: 1, yield: { id: 9, title: '產出', description: 'A😀', occurredOn: '2026-08-10', relatedCard: { id: 40, title: '完整的卡片名稱' }, createdAt: '', updatedAt: '' } }, global: global() })
    expect(wrapper.get('.selection-summary').text()).toContain('完整的卡片名稱')
    expect(wrapper.get('.app-field-counter').text()).toContain('2 / 50000')
    expect(wrapper.find('.source-tag').exists()).toBe(false)
    await wrapper.findAll('button').find(button => button.text() === '移除關聯')!.trigger('click')
    expect(wrapper.find('.selection-summary').exists()).toBe(false)
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.updateYield).toHaveBeenCalledWith(1, 9, expect.objectContaining({ relatedCardId: null })))
    wrapper.unmount()
  })

  it('卡片來源沿用 Card ID 搜尋，切換種類仍保留已選內容', async () => {
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCard: null, sourceExperiment: null }, global: global() })
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'card')
    await flushPromises()
    await wrapper.get('input').setValue('#10')
    await vi.waitFor(() => expect(api.cards).toHaveBeenCalledWith(expect.objectContaining({ id: 10, archiveStatus: 'ALL' })))
    expect(api.cards.mock.calls.at(-1)?.[0]).not.toHaveProperty('title')
    wrapper.unmount()
  })

  it('續載失敗保留已選與已載入來源，重試後繼續累加', async () => {
    api.list.mockImplementation(({ page: index }) => index ? Promise.reject(new Error('暫時中斷')) : Promise.resolve(page([{ id: 1, title: '來源 A' }], 12)))
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCard: null, sourceExperiment: null }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.get('.load-more-row button').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('後續來源讀取失敗'))
    expect(wrapper.get('.source-options .picker-option').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('.source-summaries').text()).toContain('來源 A')
    api.list.mockResolvedValueOnce(page([{ id: 2, title: '來源 B' }], 12))
    await wrapper.findAll('button').find(button => button.text() === '後續來源讀取失敗，重試')!.trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.source-options .picker-option')).toHaveLength(2))
    expect(wrapper.get('.source-options .picker-option').attributes('aria-pressed')).toBe('true')
    wrapper.unmount()
  })

  it('價值產出可選填卡片，取消選取保留原關聯，再選取後保存新關聯', async () => {
    api.updateYield.mockResolvedValue({ id: 9 })
    const wrapper = mount(TreasureYieldDialog, { props: { modelValue: true, treasureId: 1, yield: { id: 9, title: '再次使用', description: '', occurredOn: '2026-08-10', relatedCard: { id: 10, title: '原卡片' }, createdAt: '', updatedAt: '' } }, global: global() })
    await flushPromises()
    expect(api.cards).not.toHaveBeenCalled()
    await wrapper.get('.related-card-control button').trigger('click')
    await wrapper.get('.cancel-picker').trigger('click')
    expect(wrapper.get('.selection-summary strong').text()).toBe('原卡片')
    await wrapper.get('.related-card-control button').trigger('click')
    await wrapper.get('.confirm-card').trigger('click')
    expect(wrapper.get('.selection-summary strong').text()).toBe('另一張相關卡片')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.updateYield).toHaveBeenCalledOnce())
    expect(api.updateYield).toHaveBeenCalledWith(1, 9, expect.objectContaining({ occurredOn: '2026-08-10', relatedCardId: 40 }))
    expect(api.cards).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
