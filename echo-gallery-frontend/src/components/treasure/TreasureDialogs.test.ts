import { mount, flushPromises } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import TreasureFormDialog from './TreasureFormDialog.vue'
import TreasureYieldDialog from './TreasureYieldDialog.vue'
import TreasureSourcePicker from './TreasureSourcePicker.vue'
import TreasureSourceDialog from './TreasureSourceDialog.vue'
import TreasureWorkPicker from '../work/TreasureWorkPicker.vue'
import CoverImageField from '../ui/CoverImageField.vue'
import { ElMessage } from 'element-plus'

const api = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), update: vi.fn(), replaceSources: vi.fn(), replaceWorks: vi.fn(), passTrial: vi.fn(), createTrial: vi.fn(), updateTrial: vi.fn(), createYield: vi.fn(), updateYield: vi.fn(), confirm: vi.fn(), cards: vi.fn(), experiments: vi.fn(), works: vi.fn(), workCandidates: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
vi.mock('../../utils/api/cardApi', () => ({ cardApi: { searchCards: api.cards } }))
vi.mock('../../utils/api/experimentApi', () => ({ experimentApi: { getExperiments: api.experiments } }))
vi.mock('../../utils/api/workApi', () => ({ workApi: { fromTreasure: api.works, treasureCandidates: api.workCandidates, replaceTreasureWorks: api.replaceWorks } }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), success: vi.fn() }, ElMessageBox: { confirm: api.confirm } }))

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

describe('展品表單互動', () => {
  beforeEach(() => {
    clients.forEach(client => client.clear()); clients.length = 0
    vi.clearAllMocks()
    api.list.mockImplementation(({ page: index }) => Promise.resolve(page([{ id: index ? 2 : 1, title: index ? '來源 B' : '來源 A' }], 12)))
    api.cards.mockResolvedValue(page([{ id: '10', title: '來源卡片' }]))
    api.experiments.mockResolvedValue(page([]))
    api.works.mockResolvedValue([])
    api.workCandidates.mockResolvedValue(page([]))
  })

  it('作品管理儲存多選結果及原始快照', async () => {
    const work = { id: 10, title: '人生與我', stage: 'WORKING', editorMode: 'INTERNAL', archived: false }
    api.workCandidates.mockResolvedValue(page([work]))
    api.replaceWorks.mockResolvedValue([work])
    const wrapper = mount(TreasureWorkPicker, { props: { modelValue: true, treasureId: 3 }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.picker-results .picker-option').exists()).toBe(true))
    await wrapper.get('.picker-results .picker-option').trigger('click')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.replaceWorks).toHaveBeenCalledWith(3, [10], []))
    wrapper.unmount()
  })

  it('來源儲存失敗時保留選擇並顯示錯誤', async () => {
    api.replaceSources.mockRejectedValue({ response: { data: { message: '創作脈絡已變更' } } })
    const treasure = { id: 3, sourceCards: [], sourceTreasures: [], sourceExperiment: null } as any
    const wrapper = mount(TreasureSourceDialog, { props: { modelValue: true, treasure }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('創作脈絡已變更'))
    expect(api.replaceSources).toHaveBeenCalledWith(3, expect.objectContaining({ sourceTreasureIds: [1], expectedTreasureIds: [] }))
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('.source-summaries').text()).toContain('來源 A')
    wrapper.unmount()
  })

  it('編輯展品只提交基本資料，不覆蓋作品及來源', async () => {
    api.update.mockResolvedValue({ id: 1, title: '系列' })
    const treasure = { id: 1, title: '系列', description: '', artifactUrl: '', coverImageUrl: '', acquiredOn: '2026-01-01', sourceCards: [{ id: 10, title: '卡片' }], sourceTreasures: [], sourceExperiment: null } as any
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true, treasure }, global: global() })
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.update).toHaveBeenCalledOnce())
    expect(api.update.mock.calls[0]![1]).not.toHaveProperty('sourceCardIds')
    expect(api.update.mock.calls[0]![1]).not.toHaveProperty('workIds')
    wrapper.unmount()
  })
  it('建立展品保留指定來源實驗', async () => {
    api.create.mockResolvedValue({ id: 3 })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true, sourceExperiment: { id: 21, title: '實驗' } }, global: global() })
    await wrapper.get('input').setValue('成果')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ sourceExperimentId: 21, sourceCardIds: [], sourceTreasureIds: [] })))
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
    expect(ElMessage.success).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('跨頁來源選取保留先前選項', async () => {
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    await wrapper.get('.load-more-row button').trigger('click')
    await vi.waitFor(() => expect(wrapper.findAll('.source-options .picker-option')).toHaveLength(2))
    await wrapper.findAll('.source-options .picker-option')[1]!.trigger('click')
    ;(wrapper.vm as any).confirm()
    expect(wrapper.emitted('confirm')![0]![0]).toMatchObject({ treasures: [{ id: 1 }, { id: 2 }] })
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

  it('切換來源種類保留不同種類的選取', async () => {
    api.experiments.mockResolvedValue(page([{ id: 21, title: '實驗 A' }, { id: 22, title: '實驗 B' }]))
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'card')
    await vi.waitFor(() => expect(wrapper.text()).toContain('來源卡片'))
    await wrapper.get('.source-options .picker-option').trigger('click')
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'experiment')
    await vi.waitFor(() => expect(wrapper.findAll('.source-options .picker-option')).toHaveLength(2))
    await wrapper.findAll('.source-options .picker-option')[1]!.trigger('click')
    ;(wrapper.vm as any).confirm()
    expect(wrapper.emitted('confirm')![0]![0]).toMatchObject({ treasures: [{ id: 1 }], cards: [{ id: 10 }], experiment: { id: 22 } })
    wrapper.unmount()
  })
  it('來源選擇不會在確認前寫入', async () => {
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
    await vi.waitFor(() => expect(wrapper.find('.source-options .picker-option').exists()).toBe(true))
    await wrapper.get('.source-options .picker-option').trigger('click')
    expect(wrapper.emitted('confirm')).toBeUndefined()
    expect(api.update).not.toHaveBeenCalled()
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
    await flushPromises()
    expect(ElMessage.success).toHaveBeenCalledWith('收益已記錄，這份成果又留下了新的痕跡。')
    wrapper.unmount()
  })

  it('收益請求等待與失敗時不宣稱成功，保留輸入供重試', async () => {
    let rejectRequest!: (reason: unknown) => void
    api.createYield.mockImplementationOnce(() => new Promise((_resolve, reject) => { rejectRequest = reject }))
    const wrapper = mount(TreasureYieldDialog, { props: { modelValue: true, treasureId: 1 }, global: global() })
    await wrapper.get('input').setValue('實際收到的回饋')
    await wrapper.get('footer button:last-child').trigger('click')
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeUndefined()
    rejectRequest({ response: { data: { message: '儲存失敗' } } })
    await flushPromises()
    expect(wrapper.text()).toContain('儲存失敗')
    expect(ElMessage.success).not.toHaveBeenCalled()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(wrapper.get('input').element.value).toBe('實際收到的回饋')
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

  it('成果說明以字元數計數並保存', async () => {
    api.create.mockResolvedValue({ id: 3 })
    const wrapper = mount(TreasureFormDialog, { props: { modelValue: true }, global: global() })
    await wrapper.get('input').setValue('成果')
    await wrapper.get('textarea').setValue('A😀')
    expect(wrapper.get('.app-field-counter').text()).toContain('2 / 50000')
    await wrapper.get('footer button:last-child').trigger('click')
    await vi.waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ description: 'A😀' })))
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

  it('卡片來源可多選並在確認時保留全部選取', async () => {
    api.cards.mockResolvedValue(page([{ id: 10, title: '來源卡片 A' }, { id: 11, title: '來源卡片 B' }]))
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'card')
    await vi.waitFor(() => expect(wrapper.findAll('.source-options .picker-option')).toHaveLength(2))
    await wrapper.findAll('.source-options .picker-option')[0]!.trigger('click')
    await wrapper.findAll('.source-options .picker-option')[1]!.trigger('click')
    expect(wrapper.findAll('.source-options .picker-option').map(option => option.attributes('aria-pressed'))).toEqual(['true', 'true'])
    ;(wrapper.vm as unknown as { confirm: () => void }).confirm()
    expect(wrapper.emitted('confirm')).toEqual([[{ treasures: [], cards: [{ id: 10, title: '來源卡片 A', metadata: expect.any(Array) }, { id: 11, title: '來源卡片 B', metadata: expect.any(Array) }], experiment: null }]])
    wrapper.unmount()
  })

  it('卡片來源沿用 Card ID 搜尋，切換種類仍保留已選內容', async () => {
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'card')
    await flushPromises()
    await wrapper.get('input').setValue('#10')
    await vi.waitFor(() => expect(api.cards).toHaveBeenCalledWith(expect.objectContaining({ id: 10, archiveStatus: 'ALL' })))
    expect(api.cards.mock.calls.at(-1)?.[0]).not.toHaveProperty('title')
    wrapper.unmount()
  })

  it('續載失敗保留已選與已載入來源，重試後繼續累加', async () => {
    api.list.mockImplementation(({ page: index }) => index ? Promise.reject(new Error('暫時中斷')) : Promise.resolve(page([{ id: 1, title: '來源 A' }], 12)))
    const wrapper = mount(TreasureSourcePicker, { props: { sourceTreasures: [], sourceCards: [], sourceExperiment: null }, global: global() })
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
