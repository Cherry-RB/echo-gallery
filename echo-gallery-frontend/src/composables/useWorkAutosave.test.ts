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

  it('內容回復為已確認版本時，不送出請求', async () => {
    const send = vi.fn()
    const auto = useWorkAutosave('1', 1, '原文', 0, send)

    auto.edit('原文續寫')
    auto.edit('原文')
    await vi.advanceTimersByTimeAsync(10_000)

    expect(send).not.toHaveBeenCalled()
    expect(auto.status.value).toBe('已儲存')
    await auto.dispose()
  })

  it('保留儲存期間的新輸入，並以新版本接續儲存', async () => {
    const first = deferred<{ body: string; bodyVersion: number }>()
    const send = vi.fn().mockReturnValueOnce(first.promise).mockImplementation(async (body: string, version: number) => ({ body, bodyVersion: version + 1 }))
    const auto = useWorkAutosave('1', 1, 'a', 0, send)

    auto.edit('ab')
    const pending = auto.flush()
    await vi.advanceTimersByTimeAsync(0)
    auto.edit('a')
    first.resolve({ body: 'ab', bodyVersion: 1 })
    await pending
    await vi.advanceTimersByTimeAsync(1200)

    expect(send.mock.calls).toEqual([['ab', 0], ['a', 1]])
    expect(auto.status.value).toBe('已儲存')
    await auto.dispose()
  })

  it('伺服器明確回覆 409 時才視為版本衝突，且保留草稿', async () => {
    const send = vi.fn().mockRejectedValue({ response: { status: 409 } })
    const auto = useWorkAutosave('owner-a', 7, '舊文', 1, send)

    auto.edit('本機續寫')
    await auto.flush()

    expect(auto.conflict.value).toBe(true)
    expect(auto.error.value).toContain('重新載入')
    expect(workDrafts.put).toHaveBeenCalled()
    await auto.dispose()
  })

  it('持續輸入時以最長等待時間儲存，且不輪詢版本', async () => {
    const send = vi.fn().mockImplementation(async (body: string, version: number) => ({ body, bodyVersion: version + 1 }))
    const auto = useWorkAutosave('1', 1, '', 0, send)

    for (let index = 0; index < 8; index++) {
      auto.edit(String(index))
      await vi.advanceTimersByTimeAsync(1000)
    }

    expect(send).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(20_000)
    expect(send).toHaveBeenCalledTimes(1)
    await auto.dispose()
  })

  it('逾時或斷線不誤判為版本衝突，使用者可重試儲存', async () => {
    const send = vi.fn()
      .mockRejectedValueOnce(new Error('timeout'))
      .mockImplementationOnce(async (body: string, version: number) => ({ body, bodyVersion: version + 1 }))
    const auto = useWorkAutosave('owner-a', 7, '舊文', 1, send)

    auto.edit('本機續寫')
    await auto.flush()

    expect(auto.conflict.value).toBe(false)
    expect(auto.error.value).not.toBe('')
    expect(auto.dirty.value).toBe(true)

    await auto.flush()

    expect(send.mock.calls).toEqual([['本機續寫', 1], ['本機續寫', 1]])
    expect(auto.conflict.value).toBe(false)
    expect(auto.status.value).toBe('已儲存')
    await auto.dispose()
  })
})
