import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ElementPlus from 'element-plus'
import ActionSelect from './ExplorationActionSelect.vue'
const api = vi.hoisted(() => ({ methods: vi.fn(), threadOptions: vi.fn() }))
vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: api }))
const method = { id: 8, threadId: 3, threadName: '方向', content: '閱讀 20 分鐘', version: 1, discoveryCount: 0, hasHistory: false, lastOrganizedAt: null }
const wrappers: ReturnType<typeof mount>[] = []
beforeEach(() => {
  vi.resetAllMocks()
  api.methods.mockResolvedValue({ content: [method], totalPages: 2 })
  api.threadOptions.mockResolvedValue({ content: [{ id: 6, systemKey: 'UNASSIGNED' }], totalPages: 1 })
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); vi.restoreAllMocks() })
function render(extra: Record<string, unknown> = {}) {
  const w = mount(ActionSelect, { props: { experimentId: 1, threadId: 3, method: null, text: '', ...extra }, global: { stubs: {
    'el-autocomplete': { name: 'Autocomplete', props: ['fetchSuggestions', 'modelValue'], emits: ['update:modelValue', 'select'], template: '<div><slot name="suffix" /><slot name="footer" /></div>' },
    'el-button': { template: '<button><slot /></button>' }, 'el-pagination': true,
    'el-input': true, ExpandableText: true,
  } } }); wrappers.push(w); return w
}
type Item = { value: string; method?: typeof method; create?: boolean; empty?: boolean }
function search(w: ReturnType<typeof mount>, text: string) {
  return new Promise<Item[]>(done => w.findComponent({ name: 'Autocomplete' }).props('fetchSuggestions')(text, done))
}
describe('可輸入的行動備選', () => {
  it('空白展開與搜尋都限制探索線，同一 ID 的最近、常用與完整候選只列一次', async () => {
    const w = render({ recent: [method], favorites: [method], allowCreate: true })
    expect(await search(w, ' ')).toEqual([{ value: method.content, method }])
    expect(api.methods).toHaveBeenLastCalledWith(1, { threadId: 3, keyword: '', page: 0 })
    const matches = await search(w, '閱讀')
    expect(matches).toHaveLength(2)
    expect(matches[1]).toMatchObject({ value: '閱讀', create: true })
    expect(w.emitted('select')).toBeUndefined()
  })
  it('只有選項點選才採用既有行動，直接輸入只傳出新文字', async () => {
    const w = render({ allowCreate: true })
    const input = w.findComponent({ name: 'Autocomplete' })
    input.vm.$emit('update:modelValue', '閱讀 30 分鐘')
    expect(w.emitted('input')).toEqual([['閱讀 30 分鐘']])
    expect(w.emitted('select')).toBeUndefined()
    input.vm.$emit('select', { value: method.content, method })
    expect(w.emitted('select')).toEqual([[method]])
    expect((await search(w, method.content)).some(item => item.create)).toBe(false)
  })
  it('發現表單只選既有行動，不提供新增候選；失敗顯示可重試回饋', async () => {
    const w = render()
    expect((await search(w, '不同內容')).some(item => item.create)).toBe(false)
    api.methods.mockRejectedValueOnce(new Error('offline'))
    expect((await search(w, '不同內容'))[0]?.empty).toBe(true)
    expect(w.text()).toContain('重試')
  })
  it('預設探索線以 systemKey 辨識，不把其他探索線當成候選', async () => {
    const w = render({ threadId: null })
    await search(w, '')
    expect(api.methods).toHaveBeenCalledWith(1, { threadId: 6, keyword: '', page: 0 })
    api.threadOptions.mockResolvedValueOnce({ content: [], totalPages: 0 })
    api.methods.mockClear()
    expect((await search(w, ''))[0]?.empty).toBe(true)
    expect(api.methods).not.toHaveBeenCalled()
  })
  it('切換探索線後丟棄前一次尚未回來的搜尋結果', async () => {
    let resolve!: (value: unknown) => void
    api.methods.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const w = render()
    const done = vi.fn()
    const fetching = w.findComponent({ name: 'Autocomplete' }).props('fetchSuggestions')('', done)
    await w.setProps({ threadId: 9 })
    resolve({ content: [method], totalPages: 1 })
    await fetching
    expect(done).not.toHaveBeenCalled()
  })
  it('實際下拉元件支援空白展開與鍵盤選取，選取不會直接寫入資料', async () => {
    const w = mount(ActionSelect, { attachTo: document.body, props: { experimentId: 1, threadId: 3, method: null, text: '', allowCreate: true }, global: { plugins: [ElementPlus] } })
    wrappers.push(w)
    await flushPromises()
    const input = w.get('input')
    input.element.focus()
    await vi.waitFor(() => expect(api.methods).toHaveBeenCalled())
    await vi.waitFor(() => expect(document.body.querySelector('[role="option"]')?.textContent ?? '').toContain(method.content))
    await input.trigger('keydown', { key: 'ArrowDown', code: 'ArrowDown' })
    await input.trigger('keydown', { key: 'Enter', code: 'Enter' })
    expect(w.emitted('select')).toEqual([[method]])
  })
  it('新輸入在 debounce 前已使舊查詢失效', async () => {
    let resolve!: (value: unknown) => void
    api.methods.mockReturnValueOnce(new Promise(done => { resolve = done }))
    const w = render()
    const input = w.findComponent({ name: 'Autocomplete' })
    const done = vi.fn()
    const fetching = input.props('fetchSuggestions')('舊查詢', done)
    input.vm.$emit('update:modelValue', '新查詢')
    resolve({ content: [method], totalPages: 1 })
    await fetching
    expect(done).not.toHaveBeenCalled()
  })
  it('沒有分頁、錯誤或常用操作時，不渲染空白 footer', async () => {
    api.methods.mockResolvedValue({ content: [], totalPages: 0 })
    const w = render({ text: 'ff', allowCreate: true })
    expect(await search(w, 'ff')).toEqual([{ value: 'ff', create: true }])
    expect(w.find('.option-footer').exists()).toBe(false)
    api.methods.mockResolvedValue({ content: [method], totalPages: 2 })
    await search(w, '')
    await w.vm.$nextTick()
    expect(w.find('.option-footer').exists()).toBe(true)
  })
  it('長候選使用輸入欄位寬度，沒有操作時不產生浮層底部空白', async () => {
    const width = vi.spyOn(HTMLElement.prototype, 'offsetWidth', 'get').mockReturnValue(420)
    api.methods.mockResolvedValue({ content: [{ ...method, content: '連續的長行動'.repeat(100) }], totalPages: 1 })
    const w = mount(ActionSelect, { attachTo: document.body, props: { experimentId: 1, threadId: 3, method: null, text: '' }, global: { plugins: [ElementPlus], stubs: { transition: false } } })
    wrappers.push(w)
    await flushPromises()
    w.get('input').element.focus()
    await vi.waitFor(() => expect(document.body.querySelector('.action-option')).not.toBeNull())
    await vi.waitFor(() => expect((document.body.querySelector('.el-autocomplete-suggestion') as HTMLElement).style.width).toBe('420px'))
    expect(document.body.querySelector('.el-autocomplete-suggestion__footer')).toBeNull()
    width.mockRestore()
  })
})
