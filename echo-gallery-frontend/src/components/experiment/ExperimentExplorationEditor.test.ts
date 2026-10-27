import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import Editor from './ExperimentExplorationEditor.vue'
const api = vi.hoisted(() => ({ state: vi.fn(), method: vi.fn(), focus: vi.fn(), updateMethod: vi.fn(), createMethod: vi.fn(), createRecord: vi.fn(), updateRecord: vi.fn(), assign: vi.fn() }))
vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: api }))
vi.mock('vue-router', async original => ({ ...await original<typeof import('vue-router')>(), useRouter: () => ({ beforeEach: () => vi.fn() }) }))
const method = { id: 8, threadId: 3, threadName: '方向', content: '原行動', version: 4, discoveryCount: 2, hasHistory: false, lastOrganizedAt: null }
const state = { currentTry: method.content, currentMethod: method, currentThreadId: 3, recentMethods: [method], favoriteMethods: [], favoriteTries: [], version: 0, records: [], recordCount: 2 }
const record = { id: 7, threadId: 3, threadName: '方向', methodId: 8, version: 2, tryText: method.content, discovery: '原觀察', occurredAt: '2026-10-26T00:00:00Z', createdAt: '2026-10-26T00:00:00Z', updatedAt: '2026-10-26T00:00:00Z', exports: [] }
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.resetAllMocks()
  api.state.mockResolvedValue(state); api.method.mockResolvedValue(method); api.focus.mockResolvedValue(state)
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })
async function render(extra: Record<string, unknown> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const w = mount(Editor, { props: { modelValue: true, experimentId: 1, mode: 'TRY', ...extra }, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
    AppDialog: { name: 'AppDialog', props: ['modelValue', 'title', 'draftBaseline', 'draft'], emits: ['update:modelValue'], template: '<section v-if="modelValue"><h2>{{ title }}</h2><slot /><footer><slot name="footer" :request-close="() => $emit(\'update:modelValue\', false)" /></footer></section>' },
    ExplorationThreadSelect: { name: 'ThreadSelect', props: ['modelValue', 'disabled'], emits: ['update:modelValue', 'label-change'], template: '<div class="thread-select" />' },
    ExplorationActionSelect: { name: 'ActionSelect', props: ['method', 'text', 'threadId', 'favorites'], emits: ['input', 'select', 'toggle-favorite'], template: '<div class="action-select" />' },
    ExplorationOrganizationDialog: { name: 'ExplorationOrganizationDialog', props: { modelValue: Boolean, method: Object, newMethodContent: String, mode: String, expectedFocusVersion: Number, focusFavoriteMethodIds: Array, returnToEditor: Boolean }, emits: ['update:modelValue', 'return-to-editor', 'saved'], template: '<section v-if="modelValue" class="organization" />' },
    'el-form': { template: '<form><slot /></form>' }, 'el-form-item': { props: ['label', 'error'], template: '<label><slot name="label">{{ label }}</slot><slot /><span>{{ error }}</span></label>' },
    'el-button': { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    'el-input': { name: 'Input', props: ['modelValue'], emits: ['update:modelValue'], template: '<textarea :value="modelValue" />' },
    'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' }, 'el-skeleton': true,
  } } }); wrappers.push(w)
  await vi.waitFor(() => expect(w.findComponent({ name: 'AppDialog' }).props('draftBaseline')).toBeDefined())
  return w
}
async function click(w: ReturnType<typeof mount>, text: string) { await w.vm.$nextTick(); await w.findAll('button').find(b => b.text() === text)!.trigger('click') }

describe('下一步與發現的共用表單', () => {
  it('更新在原彈窗展開，取消保留原行動且不寫入；複製同文不能保存', async () => {
    const w = await render()
    const dialog = w.findComponent({ name: 'AppDialog' }).element
    await click(w, '更新')
    expect(w.findComponent({ name: 'AppDialog' }).element).toBe(dialog)
    expect(w.text()).toContain('設定下一步')
    await click(w, '返回行動選擇')
    expect(w.findComponent({ name: 'ActionSelect' }).props('method')).toEqual(method)
    await click(w, '複製')
    expect(w.text()).toContain('複製後的行動內容需與來源不同')
    expect(w.findAll('button').find(b => b.text() === '建立副本並設為下一步')!.attributes('disabled')).toBeDefined()
    await click(w, '取消')
    expect(api.focus).not.toHaveBeenCalled(); expect(api.createMethod).not.toHaveBeenCalled(); expect(api.updateMethod).not.toHaveBeenCalled()
  })
  it('直接輸入新內容只建立並採用，不會默默更新原行動', async () => {
    const w = await render()
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('input', '新的方向')
    await click(w, '儲存')
    await vi.waitFor(() => expect(api.focus).toHaveBeenCalledWith(1, { threadId: 3, newContent: '新的方向', expectedVersion: 0 }))
    expect(api.updateMethod).not.toHaveBeenCalled()
  })
  it('清空只清除下一步，切換探索線保留尚未建立的新文字', async () => {
    const w = await render()
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('input', '新文字')
    w.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    await w.vm.$nextTick()
    expect(w.findComponent({ name: 'ActionSelect' }).props('text')).toBe('新文字')
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('input', '  ')
    await click(w, '儲存')
    await vi.waitFor(() => expect(api.focus).toHaveBeenCalledWith(1, { threadId: 9, methodId: null, expectedVersion: 0 }))
  })
  it('複製與採用、常用設定只由最後一次儲存提交', async () => {
    const w = await render()
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('toggle-favorite', method)
    await click(w, '複製')
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '另一種行動')
    expect(api.focus).not.toHaveBeenCalled()
    await click(w, '建立副本並設為下一步')
    await vi.waitFor(() => expect(api.focus).toHaveBeenCalledWith(1, { threadId: 3, methodId: 8, newContent: '另一種行動', action: 'COPY', expectedVersion: 0, expectedMethodVersion: 4, favoriteMethodIds: [8] }))
  })
  it('已有發現的更新帶入整理與下一步版本；取消整理保留原草稿', async () => {
    const w = await render()
    await click(w, '更新')
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '更新草稿')
    await click(w, '下一步：整理舊紀錄')
    const org = w.findComponent({ name: 'ExplorationOrganizationDialog' })
    expect(org.props('newMethodContent')).toBe('更新草稿'); expect(org.props('expectedFocusVersion')).toBe(0)
    expect(api.updateMethod).not.toHaveBeenCalled(); expect(api.focus).not.toHaveBeenCalled()
    expect(org.props('returnToEditor')).toBe(true)
    org.vm.$emit('return-to-editor', '整理時調整的行動')
    org.vm.$emit('update:modelValue', false)
    await w.vm.$nextTick()
    expect(w.findComponent({ name: 'Input' }).props('modelValue')).toBe('整理時調整的行動')
    await click(w, '下一步：整理舊紀錄')
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).vm).toBe(org.vm)
    expect(api.focus).not.toHaveBeenCalled()
  })
  it.each([true, false])('沒有發現的更新，底部按鈕明示是否設為下一步（%s）', async settingNext => {
    const emptyMethod = { ...method, discoveryCount: 0 }
    api.state.mockResolvedValue({ ...state, currentMethod: emptyMethod })
    api.method.mockResolvedValue(emptyMethod)
    const w = await render(settingNext ? {} : { intent: 'EDIT', method: emptyMethod })
    if (settingNext) await click(w, '更新')
    expect(w.text()).toContain('更新後的行動內容必須與原內容不同')
    const submit = settingNext ? '更新並設為下一步' : '更新行動'
    expect(w.findAll('button').find(b => b.text() === submit)!.attributes('disabled')).toBeDefined()
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '  原行動  ')
    await click(w, submit)
    expect(api.focus).not.toHaveBeenCalled(); expect(api.updateMethod).not.toHaveBeenCalled()
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '修正行動')
    await click(w, settingNext ? '更新並設為下一步' : '更新行動')
    await vi.waitFor(() => expect(settingNext ? api.focus : api.updateMethod).toHaveBeenCalled())
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).exists()).toBe(false)
    if (settingNext) expect(api.focus).toHaveBeenCalledWith(1, expect.objectContaining({ action: 'UPDATE', newContent: '修正行動', expectedMethodVersion: 4 }))
    else expect(api.updateMethod).toHaveBeenCalledWith(1, 8, '修正行動', 4)
  })
  it('有發現也不能以同文字進入整理，切換模式會清除上一種模式的錯誤', async () => {
    const w = await render()
    await click(w, '更新')
    expect(w.text()).toContain('更新後的行動內容必須與原內容不同')
    expect(w.text()).toContain('整理文字會保存到探索線的歷次整理')
    await click(w, '下一步：整理舊紀錄')
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).exists()).toBe(false)
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '  原行動  ')
    await click(w, '下一步：整理舊紀錄')
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).exists()).toBe(false)
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '修改後行動')
    await click(w, '下一步：整理舊紀錄')
    const org = w.findComponent({ name: 'ExplorationOrganizationDialog' })
    expect(org.props('newMethodContent')).toBe('修改後行動')
    org.vm.$emit('update:modelValue', false)
    await w.vm.$nextTick()
    await click(w, '返回行動選擇')
    api.focus.mockRejectedValueOnce({ response: { data: { message: '測試用的儲存錯誤' } } })
    await click(w, '儲存')
    await vi.waitFor(() => expect(w.text()).toContain('測試用的儲存錯誤'))
    await click(w, '更新')
    expect(w.text()).not.toContain('測試用的儲存錯誤')
  })
  it('從探索線新增備選不改變下一步', async () => {
    const w = await render({ intent: 'CREATE', threadId: 3 })
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '未來想做的事')
    await click(w, '儲存')
    await vi.waitFor(() => expect(api.createMethod).toHaveBeenCalledWith(1, { threadId: 3, content: '未來想做的事' }))
    expect(api.focus).not.toHaveBeenCalled()
  })
  it.each(['EDIT', 'ASSIGN'])('發現 %s 入口共用可編輯表單，內容與跨線歸屬一次保存', async intent => {
    const w = await render({ mode: 'DISCOVERY', intent, record })
    expect(w.text()).toContain('編輯發現')
    expect(w.findComponent({ name: 'ThreadSelect' }).props('disabled')).not.toBe(true)
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '修正觀察')
    w.findComponent({ name: 'ThreadSelect' }).vm.$emit('update:modelValue', 9)
    await w.vm.$nextTick()
    expect(w.findComponent({ name: 'ActionSelect' }).props('method')).toBeNull()
    expect(w.findComponent({ name: 'Input' }).props('modelValue')).toBe('修正觀察')
    await click(w, '儲存')
    await vi.waitFor(() => expect(api.updateRecord).toHaveBeenCalledWith(1, record, '修正觀察', null, undefined, 9))
    expect(api.assign).not.toHaveBeenCalled(); expect(api.focus).not.toHaveBeenCalled()
  })
  it('發現欄搜尋文字不能被當成新行動儲存，清空則解除行動', async () => {
    const w = await render({ mode: 'DISCOVERY', intent: 'EDIT', record })
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('input', '沒有選取的搜尋')
    await w.vm.$nextTick()
    expect(w.findAll('button').find(b => b.text() === '儲存')!.attributes('disabled')).toBeDefined()
    w.findComponent({ name: 'ActionSelect' }).vm.$emit('input', '')
    await click(w, '儲存')
    await vi.waitFor(() => expect(api.updateRecord).toHaveBeenCalledWith(1, record, '原觀察', null, undefined, 3))
  })
  it('發現的歸屬設定預設收合，展開再收合不會遺失發現與行動草稿', async () => {
    const w = await render({ mode: 'DISCOVERY', intent: 'EDIT', record })
    const context = w.get('details.discovery-context')
    expect(context.attributes('open')).toBeUndefined()
    expect(context.get('summary').text()).toContain('方向 · 原行動')
    const element = context.element as HTMLDetailsElement
    element.open = true
    await context.trigger('toggle')
    w.findComponent({ name: 'Input' }).vm.$emit('update:modelValue', '保留草稿')
    element.open = false
    await context.trigger('toggle')
    expect(w.findComponent({ name: 'Input' }).props('modelValue')).toBe('保留草稿')
    expect(w.findComponent({ name: 'ActionSelect' }).props('method')).toEqual(method)
    expect(api.updateRecord).not.toHaveBeenCalled()
  })
  it('調整歸屬入口預設展開，欄位旁返回選擇與底部取消區分作用', async () => {
    const finding = await render({ mode: 'DISCOVERY', intent: 'ASSIGN', record })
    expect(finding.get('details.discovery-context').attributes('open')).toBeDefined()
    const action = await render()
    await click(action, '更新')
    expect(action.get('.action-label').text()).toContain('更新原行動')
    expect(action.get('.action-label').text()).toContain('返回行動選擇')
    expect(action.get('footer').text()).toContain('取消')
    expect(action.get('footer').text()).toContain('下一步：整理舊紀錄')
    expect(action.find('.editing-state').exists()).toBe(false)
  })
})
