import { mount } from '@vue/test-utils'
import type { Component } from 'vue'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TreasureTrialDialog from './TreasureTrialDialog.vue'
import TreasureChallengeRoute from './TreasureChallengeRoute.vue'
import { ElMessage } from 'element-plus'

const api = vi.hoisted(() => ({ route: vi.fn(), attempts: vi.fn(), createTrial: vi.fn(), updateTrial: vi.fn(), recordAttempt: vi.fn(), confirm: vi.fn() }))
vi.mock('../../utils/api/treasureApi', () => ({ treasureApi: api }))
vi.mock('element-plus', async importOriginal => ({ ...await importOriginal<typeof import('element-plus')>(), ElMessage: { success: vi.fn() }, ElMessageBox: { confirm: api.confirm } }))
const bronze = { id: 2, targetRank: 'BRONZE' as const, title: '正式投稿', criteria: '保留提交紀錄', status: 'NOT_PASSED' as const, resultNote: '前次未達條件', attemptedAt: '2026-10-01T10:00:00Z', evidenceUrl: null, passedAt: null, createdAt: '', updatedAt: '' }
const clients: QueryClient[] = []
const wrappers: ReturnType<typeof mount>[] = []
const stubs = {
  AppDialog: { props: ['modelValue', 'title'], template: '<section v-if="modelValue"><h1>{{ title }}</h1><div class="el-dialog__body"><slot /></div><footer><slot name="footer" /></footer></section>' },
  ExpandableText: { props: ['content'], template: '<p>{{ content }}</p>' },
  'el-button': { props: ['disabled', 'loading'], template: '<button :disabled="disabled || loading"><slot /></button>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { props: ['label'], template: '<label>{{ label }}<slot /></label>' },
  'el-input': { props: ['modelValue', 'type'], emits: ['update:modelValue'], template: '<textarea v-if="type === \'textarea\'" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" /><input v-else :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />' },
  'el-select': { props: ['modelValue'], emits: ['update:modelValue'], template: '<select :value="modelValue" @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' },
  'el-option': { props: ['label', 'value'], template: '<option :value="value">{{ label }}</option>' },
  'el-date-picker': { props: ['modelValue'], template: '<input class="date-input" :value="modelValue" />' },
  'el-alert': { props: ['title'], template: '<p role="alert">{{ title }}</p>' },
  'el-pagination': true,
}
function render(component: Component, props: Record<string, unknown>) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } }); clients.push(client)
  const wrapper = mount(component, { props, global: { plugins: [[VueQueryPlugin, { queryClient: client }]], stubs } }); wrappers.push(wrapper)
  return wrapper
}
function button(wrapper: ReturnType<typeof mount>, text: string) { return wrapper.findAll('button').find(item => item.text() === text)! }
describe('每階唯一檢定與多次嘗試流程', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.route.mockResolvedValue([bronze])
    api.attempts.mockResolvedValue({ content: [{ id: 1, titleSnapshot: '舊投稿', criteriaSnapshot: '舊條件', status: 'NOT_PASSED', resultNote: '前次經驗', attemptedAt: '2026-10-01T10:00:00Z' }], totalElements: 1 })
    api.confirm.mockResolvedValue('confirm')
  })
  afterEach(() => { wrappers.splice(0).forEach(w => w.unmount()); clients.splice(0).forEach(c => c.clear()) })

  it('未設定與已規劃維持同一骨架，未來規劃可見且不能記錄嘗試', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'SILVER', trials: [{ ...bronze, targetRank: 'SILVER', status: 'PLANNED' }] })
    expect(wrapper.text()).toContain('預先規劃')
    expect(button(wrapper, '＋記錄')).toBeUndefined()
    expect(button(wrapper, '設定')).toBeDefined()
    await wrapper.setProps({ modelValue: 'BRONZE' })
    expect(wrapper.text()).toContain('這件奇珍尚未接受新的試煉')
    expect(button(wrapper, '設定')).toBeDefined()
    expect(wrapper.text()).not.toContain('踏出私人領域')
  })

  it('範例在編輯器內展開，收合仍保留自訂草稿', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'edit' })
    await vi.waitFor(() => expect(wrapper.find('textarea').exists()).toBe(true))
    await wrapper.get('input').setValue('我的檢定')
    await wrapper.get('textarea').setValue('我的通過條件')
    await button(wrapper, '從範例開始').trigger('click')
    expect(wrapper.findAll('.example-preview')).toHaveLength(1)
    expect(wrapper.find('textarea').exists()).toBe(true)
    await button(wrapper, '收合範例').trigger('click')
    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('我的檢定')
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('我的通過條件')
    expect(api.updateTrial).not.toHaveBeenCalled()
  })

  it('採用範例須確認覆蓋，取消不遺失草稿；編輯定義不重送前次結果', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'edit' })
    await vi.waitFor(() => expect(wrapper.find('textarea').exists()).toBe(true))
    await wrapper.get('textarea').setValue('修訂後條件')
    await button(wrapper, '從範例開始').trigger('click')
    api.confirm.mockRejectedValueOnce('cancel')
    await button(wrapper, '帶入名稱與條件').trigger('click')
    await vi.waitFor(() => expect(api.confirm).toHaveBeenCalledOnce())
    await button(wrapper, '收合範例').trigger('click')
    api.updateTrial.mockResolvedValue({ ...bronze, criteria: '修訂後條件' })
    await button(wrapper, '儲存試煉').trigger('click')
    await vi.waitFor(() => expect(api.updateTrial).toHaveBeenCalledWith(1, 2, { targetRank: 'BRONZE', title: '正式投稿', criteria: '修訂後條件' }))
    expect(api.recordAttempt).not.toHaveBeenCalled()
  })

  it('未通過後可新增獨立嘗試；確認通過前取消不送出請求', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'attempt' })
    await vi.waitFor(() => expect(wrapper.find('select').exists()).toBe(true))
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('')
    await wrapper.get('textarea').setValue('第二次已完成正式投稿')
    await wrapper.get('select').setValue('PASSED')
    api.confirm.mockRejectedValueOnce('cancel')
    await button(wrapper, '儲存嘗試紀錄').trigger('click')
    await vi.waitFor(() => expect(api.confirm).toHaveBeenCalledOnce())
    expect(api.recordAttempt).not.toHaveBeenCalled()
    expect(ElMessage.success).not.toHaveBeenCalled()
    api.recordAttempt.mockResolvedValue({ id: 3 })
    await button(wrapper, '儲存嘗試紀錄').trigger('click')
    await vi.waitFor(() => expect(api.recordAttempt).toHaveBeenCalledWith(1, 2, expect.objectContaining({ status: 'PASSED', resultNote: '第二次已完成正式投稿' })))
    expect(api.createTrial).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(ElMessage.success).toHaveBeenCalledWith('試煉已然通過。這件奇珍的來歷，又添上了新的一筆。'))
  })

  it.each([
    ['ATTEMPTED', '試煉已經展開，結果仍未揭曉。', '這次試煉未能晉級'],
    ['NOT_PASSED', '這次試煉未能晉級，但經歷已經留下。', '結果仍未揭曉'],
  ] as const)('依 %s 顯示相符提示，不混入另一種結果', (status, expected, absent) => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'BRONZE', trials: [{ ...bronze, status }] })
    expect(wrapper.text()).toContain(expected)
    expect(wrapper.text()).not.toContain(absent)
  })

  it('金級仍可延續價值，不顯示尚未接受試煉的提示', () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'GOLD', modelValue: 'GOLD', trials: [] })
    expect(wrapper.text()).toContain('金級並非故事的終點。這件奇珍仍可能產生新的價值。')
    expect(wrapper.text()).not.toContain('這件奇珍尚未接受新的試煉')
  })

  it('檢定歷程顯示當時條件快照，封存後仍可閱讀但不能新增', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'BRONZE', trials: [bronze], archived: true })
    await vi.waitFor(() => expect(wrapper.text()).toContain('前次經驗'))
    await wrapper.get('[aria-label="查看 1 筆嘗試紀錄"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('舊條件'))
    expect(button(wrapper, '＋記錄')).toBeUndefined()
    expect(button(wrapper, '設定')).toBeUndefined()
    expect(wrapper.get('[aria-label="當時的試煉"]').text()).toContain('舊條件')
    expect(wrapper.get('[aria-label="這次嘗試"]').text()).toContain('前次經驗')
    expect(wrapper.find('.history-actions .history-record-action').exists()).toBe(false)
  })

  it('嘗試歷程工具列可記錄同一份檢定，通過後立即移除新增入口', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'BRONZE', trials: [bronze] })
    await vi.waitFor(() => expect(wrapper.find('[aria-label="查看 1 筆嘗試紀錄"]').exists()).toBe(true))
    await wrapper.get('[aria-label="查看 1 筆嘗試紀錄"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.find('.history-actions .history-record-action').exists()).toBe(true))
    expect(wrapper.get('.history-actions').text()).toBe('＋記錄')
    await wrapper.get('.history-record-action').trigger('click')
    expect(wrapper.emitted('attempt')).toEqual([[bronze]])
    expect(wrapper.findComponent({ name: 'HistoryDialog' }).exists()).toBe(true)
    await wrapper.setProps({ rank: 'BRONZE', trials: [{ ...bronze, status: 'PASSED' }] })
    expect(wrapper.find('.history-record-action').exists()).toBe(false)
  })

  it('未來階級的歷程只供閱讀，不提供提前記錄入口', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'SILVER', trials: [{ ...bronze, targetRank: 'SILVER' }] })
    await vi.waitFor(() => expect(wrapper.find('[aria-label="查看 1 筆嘗試紀錄"]').exists()).toBe(true))
    await wrapper.get('[aria-label="查看 1 筆嘗試紀錄"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('舊條件'))
    expect(wrapper.find('.history-record-action').exists()).toBe(false)
    expect(wrapper.emitted('attempt')).toBeUndefined()
  })

  it('返回檢定前取消捨棄，保留未儲存的條件', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'edit' })
    await vi.waitFor(() => expect(wrapper.find('textarea').exists()).toBe(true))
    await wrapper.get('textarea').setValue('未儲存條件')
    api.confirm.mockRejectedValueOnce('cancel')
    await button(wrapper, '取消').trigger('click')
    await vi.waitFor(() => expect(api.confirm).toHaveBeenCalledOnce())
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('未儲存條件')
    expect(api.updateTrial).not.toHaveBeenCalled()
  })

  it('空白未來檢定可套用範例，只有儲存才送出，未來階級提示清楚', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialRank: 'SILVER', initialMode: 'edit' })
    await vi.waitFor(() => expect(wrapper.find('textarea').exists()).toBe(true))
    expect(wrapper.text()).toContain('通過銅級後，才能記錄銀級嘗試')
    await button(wrapper, '從範例開始').trigger('click')
    await button(wrapper, '帶入名稱與條件').trigger('click')
    expect(api.confirm).not.toHaveBeenCalled()
    expect(api.createTrial).not.toHaveBeenCalled()
    expect((wrapper.get('input').element as HTMLInputElement).value).toContain('取得讀者回饋')
    await button(wrapper, '儲存試煉').trigger('click')
    await vi.waitFor(() => expect(api.createTrial).toHaveBeenCalledWith(1, expect.objectContaining({ targetRank: 'SILVER' })))
    await vi.waitFor(() => expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]))
  })

  it('四階說明只提供閱讀，不提供範例套用與編輯入口', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'help' })
    await vi.waitFor(() => expect(wrapper.findAll('.growth-guide')).toHaveLength(4))
    expect(wrapper.text()).toContain('目前等級')
    expect(wrapper.find('.example-preview').exists()).toBe(false)
    expect(button(wrapper, '帶入名稱與條件')).toBeUndefined()
    await button(wrapper, '關閉').trigger('click')
    expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
  })

  it('目前等級與查看階段分開，收合後仍保留檢定名稱', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'BRONZE', trials: [bronze] })
    expect(wrapper.get('.is-current').attributes('data-rank')).toBe('STONE')
    expect(wrapper.get('.is-viewed').attributes('data-rank')).toBe('BRONZE')
    await wrapper.get('.is-viewed .stage-view').trigger('click')
    expect(wrapper.find('.is-viewed').exists()).toBe(false)
    expect(wrapper.text()).toContain('正式投稿')
    expect(wrapper.find('.stage-content').exists()).toBe(false)
    const silver = wrapper.findAll('.stage')[2]!
    await silver.get('.stage-view').trigger('click')
    expect(wrapper.findAll('.is-viewed')).toHaveLength(1)
    expect(wrapper.get('.is-viewed').attributes('data-rank')).toBe('SILVER')
    expect(wrapper.get('.is-current').attributes('data-rank')).toBe('STONE')
    expect(wrapper.find('.milestone-node').exists()).toBe(false)
  })

  it('嘗試結果必須主動選擇，直接開啟可取消回到原畫面', async () => {
    const wrapper = render(TreasureTrialDialog, { modelValue: true, treasureId: 1, currentRank: 'STONE', nextRank: 'BRONZE', initialMode: 'attempt' })
    await vi.waitFor(() => expect(wrapper.find('textarea').exists()).toBe(true))
    await wrapper.get('textarea').setValue('已發出投稿')
    await button(wrapper, '儲存嘗試紀錄').trigger('click')
    expect(api.recordAttempt).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('結果判定')
    await button(wrapper, '取消').trigger('click')
    await vi.waitFor(() => expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false]))
  })

  it('獨立的原生查看按鈕提供收合語意，操作群組不含巢狀按鈕', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'BRONZE', trials: [bronze] })
    const header = wrapper.get('.is-viewed .stage-view')
    expect(header.element.tagName).toBe('BUTTON')
    expect(wrapper.get('#' + header.attributes('aria-controls')).attributes('role')).toBe('region')
    expect(wrapper.find('button button').exists()).toBe(false)
    expect(header.attributes('aria-expanded')).toBe('true')
    await header.trigger('click')
    expect(header.attributes('aria-expanded')).toBe('false')
    await header.trigger('click')
    expect(header.attributes('aria-expanded')).toBe('true')
    await vi.waitFor(() => expect(wrapper.find('.attempt-summary-heading .record-history-trigger').exists()).toBe(true))
    expect(wrapper.get('.attempt-summary-heading .record-count').text()).toBe('1')
  })

  it('收合時仍可直接設定與記錄，兩者不切換查看中的階級', async () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'STONE', modelValue: 'SILVER', trials: [bronze] })
    const stage = wrapper.get('[data-rank="BRONZE"]')
    const actions = stage.findAll('.stage-actions button')
    expect(actions.map(action => action.text())).toEqual(['查看', '設定', '＋記錄'])
    await actions[1]!.trigger('click')
    expect(wrapper.emitted('edit')).toEqual([['BRONZE']])
    await actions[2]!.trigger('click')
    expect(wrapper.emitted('attempt')).toEqual([[bronze]])
    expect(wrapper.get('.is-viewed').attributes('data-rank')).toBe('SILVER')
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    expect(stage.find('.stage-content').exists()).toBe(false)
  })

  it('已通過與已取得階級僅供查看，未來規劃不能記錄', () => {
    const wrapper = render(TreasureChallengeRoute, { treasureId: 1, rank: 'BRONZE', modelValue: 'GOLD', trials: [{ ...bronze, status: 'PASSED' }, { ...bronze, id: 3, targetRank: 'GOLD', status: 'PLANNED' }] })
    expect(wrapper.get('[data-rank="BRONZE"]').findAll('.stage-actions button').map(action => action.text())).toEqual(['查看'])
    expect(wrapper.get('[data-rank="GOLD"]').findAll('.stage-actions button').map(action => action.text())).toEqual(['收合', '設定'])
    expect(button(wrapper, '＋記錄')).toBeUndefined()
    expect(wrapper.get('[data-rank="STONE"]').find('button').exists()).toBe(false)
  })
})
