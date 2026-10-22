import 'fake-indexeddb/auto'
import { describe, expect, it } from 'vitest'
import { workDrafts, type WorkDraft } from './workDrafts'
import { useWorkAutosave } from '../composables/useWorkAutosave'
function draft(owner: string, workId: number, instance: string): WorkDraft {
  return { key: `${owner}:${workId}:${instance}`, owner, workId, instance, body: '尚未同步的正文', version: 4, sequence: 2, savedAt: Date.now() }
}
describe('作品本機復原草稿', () => {
  it('重設後舊分頁不能重建已清除的草稿，其他作品不受單件刪除影響', async () => {
    const editor = useWorkAutosave('reset-test', 8, '', 0, async body => ({ body, bodyVersion: 1 }))
    editor.edit('舊分頁'); await editor.backup()
    await workDrafts.clearWork('reset-test', 8)
    editor.edit('舊分頁仍在輸入'); await editor.backup()
    expect(await workDrafts.list('reset-test', 8)).toEqual([])
    expect(editor.conflict.value).toBe(true)
    await editor.dispose(); await workDrafts.clear('reset-test')
  })
  it('交易完成後可重讀，帳號、作品、分頁分別隔離，Demo 清理僅清除該 owner', async () => {
    const a = draft('draft-test-a', 1, 'tab-a'), b = draft('draft-test-a', 1, 'tab-b')
    const c = draft('draft-test-b', 1, 'tab-a'), d = draft('draft-test-a', 2, 'tab-a')
    await Promise.all([a, b, c, d].map(x => workDrafts.put(x)))
    expect(await workDrafts.list('draft-test-a', 1)).toHaveLength(2)
    expect(await workDrafts.list('draft-test-b', 1)).toEqual([c])
    await workDrafts.remove(a.key)
    expect(await workDrafts.list('draft-test-a', 1)).toEqual([b])
    await workDrafts.clear('draft-test-a')
    expect(await workDrafts.list('draft-test-a')).toEqual([])
    expect(await workDrafts.list('draft-test-b')).toEqual([c])
    await workDrafts.clear('draft-test-b')
  })
})
