import { mount } from '@vue/test-utils'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { defineComponent, nextTick, onMounted, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { afterEach, describe, expect, it, vi } from 'vitest'
import History from './ExperimentExplorationHistoryDialog.vue'
const api = vi.hoisted(() => ({ records: vi.fn(), deleteRecord: vi.fn() }))
vi.mock('../../utils/api/explorationActivityApi', () => ({ explorationActivityApi: api }))
vi.mock('vue-router', async importOriginal => ({ ...await importOriginal<typeof import('vue-router')>(), useRouter: () => ({ beforeEach: () => vi.fn() }) }))
const record = { id: 7, threadId: 3, threadName: '方向', methodId: null, version: 2, tryText: null, discovery: '原始觀察', createdAt: '2026-10-26T00:00:00Z', occurredAt: '2026-10-26T00:00:00Z', exports: [] }
const clients: QueryClient[] = [], wrappers: ReturnType<typeof mount>[] = []
// Element Plus 關閉動畫結束後會再送出 false；暫時切換子流程不得關閉外層歷程。
const HistoryShell = defineComponent({
  props: ['modelValue'], emits: ['update:modelValue', 'opened'],
  setup(props, { emit }) {
    watch(() => props.modelValue, visible => { if (!visible) void nextTick(() => emit('update:modelValue', false)) })
    onMounted(() => emit('opened'))
  },
  template: '<section v-if="modelValue" class="el-dialog"><div class="el-dialog__body"><header><slot name="actions" /></header><slot /></div><footer><slot name="footer" /></footer></section>',
})
afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()); vi.restoreAllMocks(); vi.clearAllMocks() })
async function render(totalPages = 1, totalElements = 1) {
  api.records.mockResolvedValue({ content: [record], totalPages, totalElements })
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(History, { props: { modelValue: true, experimentId: 1, records: [] }, global: {
    plugins: [[VueQueryPlugin, { queryClient: client }]], stubs: {
      HistoryDialog: HistoryShell,
      ExplorationOrganizationDialog: { name: 'ExplorationOrganizationDialog', props: ['modelValue', 'initialRecords'], emits: ['update:modelValue'], template: '<div class="organization"><button @click="$emit(\'update:modelValue\', false)">取消</button></div>' },
      ExperimentExplorationEditor: { name: 'ExperimentExplorationEditor', props: ['modelValue', 'mode', 'intent', 'record'], emits: ['update:modelValue'], template: '<div class="editor"><button @click="$emit(\'update:modelValue\', false)">取消</button></div>' },
      ExperimentQuickRecordAction: { name: 'QuickRecord', emits: ['select'], template: '<div><button @click="$emit(\'select\', \'TRY\')">設定下一步</button><button @click="$emit(\'select\', \'DISCOVERY\')">記下發現</button></div>' },
      'el-button': { template: '<button><slot /></button>' }, 'el-pagination': { name: 'Pagination', props: ['currentPage'], emits: ['update:currentPage'], template: '<nav />' }, 'el-alert': true,
      'el-skeleton': true, 'el-tooltip': { template: '<slot />' },
      'el-dropdown': { template: '<div><slot /><slot name="dropdown" /></div>' }, 'el-dropdown-menu': { template: '<div><slot /></div>' },
      'el-dropdown-item': { template: '<button><slot /></button>' },
    },
  } }); wrappers.push(wrapper)
  await vi.waitFor(() => expect(wrapper.text()).toContain('原始觀察')); return wrapper
}
describe('活動紀錄歷程', () => {
  it('讀取分頁活動資料，提供歸屬與整理，不提供原紀錄用於作品', async () => {
    const w = await render()
    expect(api.records).toHaveBeenCalledWith(1, { page: 0 })
    expect(w.text()).not.toContain('用於作品')
    await w.findAll('button').find(b => b.text() === '調整歸屬')!.trigger('click')
    expect(w.findComponent({ name: 'ExperimentExplorationEditor' }).props('intent')).toBe('ASSIGN')
    expect(w.findComponent({ name: 'ExperimentExplorationEditor' }).props('record')).toEqual(record)
  })
  it('單筆整理沿用原紀錄身份', async () => {
    const w = await render()
    await w.findAll('button').find(b => b.text() === '整理紀錄')!.trigger('click')
    expect(w.findComponent({ name: 'ExplorationOrganizationDialog' }).props('initialRecords')).toEqual([record])
    expect(w.find('.organization').exists()).toBe(true)
  })
  it.each(['編輯發現', '調整歸屬', '整理紀錄', '整理內容', '設定下一步', '記下發現'])('%s 取消後返回同一頁及捲動位置，不把切換事件回傳成歷程關閉', async action => {
    const w = await render(3, 45)
    const pagination = w.findComponent({ name: 'Pagination' })
    pagination.vm.$emit('update:currentPage', 2)
    await vi.waitFor(() => expect(api.records).toHaveBeenCalledWith(1, { page: 1 }))
    await vi.waitFor(() => expect(w.text()).toContain('原始觀察'))
    const historyBody = w.get('.el-dialog__body').element as HTMLElement
    historyBody.scrollTop = 184
    await w.findAll('button').find(b => b.text() === action)!.trigger('click')
    await nextTick()
    expect(w.emitted('update:modelValue')).toBeUndefined()
    expect(w.findComponent(HistoryShell).exists()).toBe(false)
    const child = w.find('.editor').exists() ? w.get('.editor') : w.get('.organization')
    await child.get('button').trigger('click')
    await vi.waitFor(() => expect(w.findComponent(HistoryShell).exists()).toBe(true))
    expect(w.text()).toContain('原始觀察')
    expect(w.findComponent({ name: 'Pagination' }).props('currentPage')).toBe(2)
    await vi.waitFor(() => expect((w.get('.el-dialog__body').element as HTMLElement).scrollTop).toBe(184))
    expect(w.emitted('update:modelValue')).toBeUndefined()
  })
  it('刪除確認取消仍保留探索紀錄，真正關閉才通知父層', async () => {
    vi.spyOn(ElMessageBox, 'confirm').mockRejectedValueOnce('cancel')
    const w = await render()
    await w.findAll('button').find(b => b.text() === '刪除發現')!.trigger('click')
    await nextTick()
    expect(api.deleteRecord).not.toHaveBeenCalled()
    expect(w.text()).toContain('原始觀察')
    expect(w.emitted('update:modelValue')).toBeUndefined()
    await w.findAll('button').find(b => b.text() === '關閉')!.trigger('click')
    expect(w.emitted('update:modelValue')).toEqual([[false]])
  })
  it('顯示總筆數及分組列表，紀錄清除後回到有效分頁', async () => {
    const w = await render(3, 45)
    expect(w.get('.history-total').text()).toContain('45')
    expect(w.find('.exploration-history-list .exploration-history-entry').exists()).toBe(true)
    expect(w.text()).toContain('探索線：方向')
    expect(w.text()).toContain('未指定行動')
    w.findComponent({ name: 'Pagination' }).vm.$emit('update:currentPage', 3)
    await vi.waitFor(() => expect(api.records).toHaveBeenCalledWith(1, { page: 2 }))
    api.records.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 })
    await clients[clients.length - 1]!.invalidateQueries({ queryKey: ['experiment-activity-records'] })
    await vi.waitFor(() => expect(api.records).toHaveBeenLastCalledWith(1, { page: 0 }))
    await vi.waitFor(() => expect(w.text()).toContain('尚無活動探索紀錄'))
  })
  it('切換另一個實驗時不沿用前一個實驗的分頁', async () => {
    const w = await render(3, 45)
    w.findComponent({ name: 'Pagination' }).vm.$emit('update:currentPage', 2)
    await vi.waitFor(() => expect(api.records).toHaveBeenCalledWith(1, { page: 1 }))
    await w.setProps({ experimentId: 2 })
    await vi.waitFor(() => expect(api.records).toHaveBeenLastCalledWith(2, { page: 0 }))
    await vi.waitFor(() => expect(w.findComponent({ name: 'Pagination' }).props('currentPage')).toBe(1))
  })
})
