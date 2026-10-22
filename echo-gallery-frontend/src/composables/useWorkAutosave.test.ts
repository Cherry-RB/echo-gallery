import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkAutosave } from './useWorkAutosave'
import { workDrafts } from '../utils/workDrafts'
vi.mock('../utils/workDrafts', () => ({ workDrafts: { generation: () => '', put: vi.fn().mockResolvedValue(undefined), remove: vi.fn().mockResolvedValue(undefined) } }))
const deferred = <T>() => {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
describe('作品正文自動儲存', () => {
  beforeEach(() => { vi.useFakeTimers(); vi.clearAllMocks() })
  afterEach(() => vi.useRealTimers())
  it('無變更與送出前改回原文都不呼叫 API', async () => {
    const send = vi.fn()
    const auto = useWorkAutosave('1', 1, '原文', 0, send)
    await auto.flush(); auto.edit('原文新增'); auto.edit('原文')
    await vi.advanceTimersByTimeAsync(10000)
    expect(send).not.toHaveBeenCalled(); expect(auto.status.value).toBe('已儲存')
    await auto.dispose()
  })
  it('保留 Markdown 空白與換行，儲存後改回原文會再送出', async () => {
    const send = vi.fn().mockImplementation(async (body: string, expected: number) => ({ body, bodyVersion: expected + 1 }))
    const auto = useWorkAutosave('1', 1, 'a', 0, send)
    auto.edit('a  \n'); await auto.flush()
    auto.edit('a'); await auto.flush()
    expect(send.mock.calls).toEqual([['a  \n', 0], ['a', 1]])
    expect(auto.version.value).toBe(2); await auto.dispose()
  })
  it('儲存途中改回原文，會使用回應的新版本再儲存', async () => {
    const first = deferred<{ body: string; bodyVersion: number }>()
    const send = vi.fn().mockReturnValueOnce(first.promise).mockImplementation(async (body: string, v: number) => ({ body, bodyVersion: v + 1 }))
    const auto = useWorkAutosave('1', 1, 'a', 0, send)
    auto.edit('ab'); const pending = auto.flush(); await vi.advanceTimersByTimeAsync(0)
    auto.edit('a'); first.resolve({ body: 'ab', bodyVersion: 1 }); await pending
    expect(auto.dirty.value).toBe(true); expect(auto.status.value).toBe('編輯中')
    await vi.advanceTimersByTimeAsync(1200)
    expect(send.mock.calls).toEqual([['ab', 0], ['a', 1]])
    expect(auto.status.value).toBe('已儲存'); await auto.dispose()
  })
  it('持續輸入以最長等待儲存，沒有週期版本查詢', async () => {
    const send = vi.fn().mockImplementation(async (body: string, v: number) => ({ body, bodyVersion: v + 1 }))
    const auto = useWorkAutosave('1', 1, '', 0, send)
    for (let i = 0; i < 8; i++) { auto.edit(String(i)); await vi.advanceTimersByTimeAsync(1000) }
    expect(send).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(20000); expect(send).toHaveBeenCalledTimes(1)
    await auto.dispose()
  })
  it('版本衝突停止覆寫，保留草稿，重新載入不刪除舊稿', async () => {
    const send = vi.fn().mockRejectedValue({ response: { status: 409 } })
    const auto = useWorkAutosave('owner-a', 7, '舊文', 1, send)
    auto.edit('本機稿'); await auto.flush()
    expect(auto.conflict.value).toBe(true)
    auto.edit('本機稿繼續'); await vi.advanceTimersByTimeAsync(20000)
    expect(send).toHaveBeenCalledTimes(1)
    const writes = vi.mocked(workDrafts.put).mock.calls
    expect(writes.at(-1)?.[0]).toMatchObject({ owner: 'owner-a', workId: 7, body: '本機稿繼續', version: 1 })
    const oldKey = writes.at(-1)![0].key
    auto.reset('新版', 2); await auto.dispose()
    expect(workDrafts.remove).not.toHaveBeenCalledWith(oldKey)
  })
  it('不同分頁使用不同草稿位置，失敗不遺失新輸入', async () => {
    const first = deferred<{ body: string; bodyVersion: number }>()
    const send = vi.fn().mockReturnValue(first.promise)
    const a = useWorkAutosave('1', 1, '', 0, send), b = useWorkAutosave('1', 1, '', 0, send)
    a.edit('甲'); const saving = a.flush(); await vi.advanceTimersByTimeAsync(0)
    a.edit('甲乙'); b.edit('另一份')
    first.reject(new Error('斷線')); await saving; await b.backup()
    expect(a.body.value).toBe('甲乙'); expect(a.conflict.value).toBe(true)
    const keys = new Set(vi.mocked(workDrafts.put).mock.calls.map(([d]) => d.key))
    expect(keys.size).toBe(2); await a.dispose(); await b.dispose()
  })
})
