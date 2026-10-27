import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Organization from './ExplorationOrganizationDialog.vue'
const api = vi.hoisted(() => ({ records: vi.fn(), preview: vi.fn(), commit: vi.fn() }))
vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: api }))
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ beforeEach: () => vi.fn() }) }))
const record = { id: 7, threadId: 3, threadName: '方向', methodId: 8, version: 2, tryText: '舊試法', discovery: '觀察', createdAt: '2026-10-26T00:00:00Z', occurredAt: '2026-10-26T00:00:00Z', updatedAt: '2026-10-26T00:00:00Z', exports: [] }
const method = { id: 8, threadId: 3, threadName: '方向', content: '舊試法', version: 2, discoveryCount: 1, hasHistory: false, lastOrganizedAt: null }
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })
beforeEach(() => {
  vi.clearAllMocks()
  api.records.mockResolvedValue({ content: [record], totalPages: 1, totalElements: 1 })
  api.preview.mockResolvedValue({ token: 'signed-preview', groups: [{ key: 'method:8', threadId: 3, threadName: '方向', methodId: 8, methodContent: '舊試法', recordCount: 1, text: '觀察與心得', historyText: '' }], cardContent: '卡片草稿' })
  api.commit.mockResolvedValue({ cardId: null, clearedCount: 1, threadIds: [3] })
})
function render(extra: Record<string, unknown> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const w = mount(Organization, { props: { modelValue: true, experimentId: 1, initialRecords: [record], ...extra }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      AppDialog: { name: 'AppDialog', props: ['modelValue', 'busy'], template: '<section v-if="modelValue"><slot /><footer><slot name="footer" :request-close="() => {}" /></footer></section>' },
      CardPickerDialog: true, QuickCreateCardDialog: true, ExplorationMethodPicker: true,
      'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
      'el-input': { name: 'DraftInput', props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea :value="modelValue" />' },
      'el-radio-group': { template: '<div><slot /></div>' }, 'el-radio-button': true,
      'el-checkbox': { name: 'Checkbox', props: ['modelValue'], emits: ['update:modelValue'], template: '<span><slot /></span>' }, 'el-skeleton': true, 'el-alert': { props: ['title'], template: '<p>{{ title }}</p>' }, 'el-result': true, 'el-pagination': true,
    },
  } }); wrappers.push(w); return w
}
async function click(w: ReturnType<typeof mount>, text: string) { await w.vm.$nextTick(); await w.findAll('button').find(b => b.text() === text)!.trigger('click') }
describe('整理交易的前端流程', () => {
  it.each([0, 500])('預覽先於保存，結果不明（%s）時鎖定草稿並以相同請求識別重試', async status => {
    api.commit.mockRejectedValueOnce(status ? { response: { status } } : new Error('連線中斷'))
    const w = render()
    await click(w, '預覽整理')
    await vi.waitFor(() => expect(w.text()).toContain('本輪整理文字'))
    expect(api.commit).not.toHaveBeenCalled()
    w.findComponent({ name: 'DraftInput' }).vm.$emit('update:modelValue', '使用者整理草稿')
    await click(w, '確認保存')
    await vi.waitFor(() => expect(w.text()).toContain('重試確認上次操作'))
    expect(w.findComponent({ name: 'AppDialog' }).props('busy')).toBe(true)
    expect(w.emitted('saved')).toBeUndefined()
    await click(w, '重試確認上次操作')
    await vi.waitFor(() => expect(w.emitted('saved')).toHaveLength(1))
    expect(api.commit.mock.calls[0]![1]).toEqual(api.commit.mock.calls[1]![1])
    expect(api.commit.mock.calls[1]![1]).toMatchObject({ drafts: [{ key: 'method:8', text: '使用者整理草稿' }], clearRecords: false })
    expect(w.findComponent({ name: 'AppDialog' }).props('busy')).toBe(false)
  })
  it('更新行動強制預覽完整集合；最後確認仍拒絕同文字且保留全部草稿', async () => {
    const w = render({ mode: 'UPDATE_METHOD', method, newMethodContent: '舊試法' })
    await vi.waitFor(() => expect(w.text()).toContain('更新後行動'))
    expect(api.preview).toHaveBeenCalledWith(1, 'UPDATE_METHOD', [expect.objectContaining({ methodId: 8, allRecords: true })], undefined)
    expect(api.commit).not.toHaveBeenCalled()
    await click(w, '確認更新')
    expect(api.commit).not.toHaveBeenCalled()
    expect(w.get('.field-error').text()).toContain('更新後的行動內容必須與原內容不同')
    const inputs = w.findAllComponents({ name: 'DraftInput' })
    inputs[1]!.vm.$emit('update:modelValue', '  舊試法  ')
    await click(w, '確認更新')
    expect(api.commit).not.toHaveBeenCalled()
    inputs[1]!.vm.$emit('update:modelValue', '新的行動文字')
    await click(w, '確認更新')
    await vi.waitFor(() => expect(api.commit).toHaveBeenCalled())
    expect(api.commit.mock.calls[0]![1]).toMatchObject({ clearRecords: true, newMethodContent: '新的行動文字', drafts: [{ key: 'method:8', text: '觀察與心得' }] })
  })
  it('無試法歷史可單獨輸出，不夾帶同線其他試法', async () => {
    api.records.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 })
    const w = render({ mode: 'CARD', threadId: 3, unassignedOnly: true, initialRecords: [] })
    w.findAllComponents({ name: 'Checkbox' }).find(c => c.text().includes('歷次整理'))!.vm.$emit('update:modelValue', true)
    w.findAllComponents({ name: 'Checkbox' }).find(c => c.text().includes('全部發現'))!.vm.$emit('update:modelValue', true)
    await click(w, '預覽整理')
    await vi.waitFor(() => expect(api.preview).toHaveBeenCalledWith(1, 'CARD', [{ threadId: 3, methodId: null, recordIds: [], allRecords: true, includeMethod: false, includeHistory: true }], undefined))
  })
  it('從設定下一步進入整理時，一次提交更新、採用與常用草稿', async () => {
    const w = render({ mode: 'UPDATE_METHOD', method, newMethodContent: '新行動', expectedFocusVersion: 5, focusFavoriteMethodIds: [8] })
    await vi.waitFor(() => expect(w.text()).toContain('更新後行動'))
    await click(w, '確認更新')
    await vi.waitFor(() => expect(api.commit).toHaveBeenCalled())
    expect(api.commit.mock.calls[0]![1]).toMatchObject({ newMethodContent: '新行動', expectedFocusVersion: 5, focusFavoriteMethodIds: [8], clearRecords: true })
  })
  it('重新讀取相同原文顯示結果，原文收合區與草稿分開', async () => {
    const w = render()
    await click(w, '預覽整理')
    await vi.waitFor(() => expect(w.find('details.source-panel').exists()).toBe(true))
    expect(w.get('details.source-panel').attributes('open')).toBeUndefined()
    expect(w.get('details.source-panel summary').text()).toContain('1 筆發現')
    expect(w.find('.draft-group > .metadata').exists()).toBe(false)
    expect(w.get('.source-content').text()).toContain('觀察與心得')
    expect(w.get('.source-content').text()).not.toContain('本輪整理文字')
    await click(w, '重新讀取原文')
    await vi.waitFor(() => expect(w.get('[role="status"]').text()).toContain('內容沒有變更'))
    expect(api.commit).not.toHaveBeenCalled()
  })
  it('返回編輯保留整理草稿，重新進入後才確認更新；獨立整理仍提供取消', async () => {
    const w = render({ mode: 'UPDATE_METHOD', method, newMethodContent: '行動草稿', returnToEditor: true })
    await vi.waitFor(() => expect(w.text()).toContain('更新後行動'))
    const inputs = w.findAllComponents({ name: 'DraftInput' })
    inputs[0]!.vm.$emit('update:modelValue', '尚未保存的整理')
    inputs[1]!.vm.$emit('update:modelValue', '整理時修正行動')
    await click(w, '返回編輯')
    expect(w.emitted('return-to-editor')).toEqual([['整理時修正行動']])
    expect(w.emitted('update:modelValue')).toEqual([[false]])
    expect(api.commit).not.toHaveBeenCalled()
    await w.setProps({ modelValue: false })
    await w.setProps({ modelValue: true, newMethodContent: '回到編輯後再修正' })
    expect(api.preview).toHaveBeenCalledTimes(1)
    expect(w.findAllComponents({ name: 'DraftInput' })[0]!.props('modelValue')).toBe('尚未保存的整理')
    expect(w.findAllComponents({ name: 'DraftInput' })[1]!.props('modelValue')).toBe('回到編輯後再修正')
    await click(w, '確認更新')
    await vi.waitFor(() => expect(api.commit).toHaveBeenCalledWith(1, expect.objectContaining({ newMethodContent: '回到編輯後再修正', drafts: [{ key: 'method:8', text: '尚未保存的整理' }] })))
    const standalone = render({ mode: 'UPDATE_METHOD', method })
    await vi.waitFor(() => expect(standalone.text()).toContain('更新後行動'))
    expect(standalone.findAll('button').some(b => b.text() === '取消')).toBe(true)
    expect(standalone.findAll('button').some(b => b.text() === '返回編輯')).toBe(false)
  })
  it.each([false, true])('更新原文時依草稿是否已編輯（%s）同步或保留，提交使用新版本', async edited => {
    const w = render()
    await click(w, '預覽整理')
    await vi.waitFor(() => expect(w.text()).toContain('本輪整理文字'))
    if (edited) w.findComponent({ name: 'DraftInput' }).vm.$emit('update:modelValue', '我的整理草稿')
    api.preview.mockResolvedValueOnce({ token: 'fresh-preview', groups: [{ key: 'method:8', threadId: 3, threadName: '方向', methodId: 8, methodContent: '舊試法', recordCount: 2, text: '新增的原始觀察', historyText: '' }], cardContent: '新卡片草稿' })
    await click(w, '重新讀取原文')
    await vi.waitFor(() => expect(w.get('.source-content').text()).toContain('新增的原始觀察'))
    expect(w.findComponent({ name: 'DraftInput' }).props('modelValue')).toBe(edited ? '我的整理草稿' : '新增的原始觀察')
    expect(w.get('[role="status"]').text()).toContain(edited ? '已編輯的草稿保留' : '未編輯的草稿已更新')
    await click(w, '確認保存')
    await vi.waitFor(() => expect(api.commit).toHaveBeenCalled())
    expect(api.commit.mock.calls[0]![1]).toMatchObject({ token: 'fresh-preview', drafts: [{ key: 'method:8', text: edited ? '我的整理草稿' : '新增的原始觀察' }] })
  })
})
