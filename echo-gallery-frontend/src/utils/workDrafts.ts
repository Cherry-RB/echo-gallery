export interface WorkDraft { key: string; owner: string; workId: number; instance: string; body: string; version: number; sequence: number; savedAt: number }
let connection: Promise<IDBDatabase> | undefined
function database() {
  connection ??= new Promise<IDBDatabase>((resolve, reject) => {
    const open = indexedDB.open('echo-work-drafts', 1)
    open.onupgradeneeded = () => open.result.createObjectStore('drafts', { keyPath: 'key' })
    open.onsuccess = () => resolve(open.result)
    open.onerror = () => { connection = undefined; reject(open.error) }
  })
  return connection
}
async function transaction<T>(mode: IDBTransactionMode, action: (store: IDBObjectStore) => IDBRequest<T>) {
  const db = await database()
  return new Promise<T>((resolve, reject) => {
    const tx = db.transaction('drafts', mode); const request = action(tx.objectStore('drafts'))
    tx.oncomplete = () => resolve(request.result)
    tx.onerror = () => reject(tx.error)
    tx.onabort = () => reject(tx.error)
  })
}
export const workDrafts = {
  generation: (owner: string, workId: number) => `${localStorage.getItem(`work-drafts-reset:${owner}`) || ''}:${localStorage.getItem(`work-drafts-reset:${owner}:${workId}`) || ''}`,
  put: (draft: WorkDraft) => transaction('readwrite', store => store.put(draft)),
  remove: (key: string) => transaction('readwrite', store => store.delete(key)),
  async list(owner: string, workId?: number) {
    const all = await transaction<WorkDraft[]>('readonly', store => store.getAll())
    return all.filter(d => d.owner === owner && (workId === undefined || d.workId === workId)).sort((a, b) => b.savedAt - a.savedAt)
  },
  async clear(owner: string) {
    localStorage.setItem(`work-drafts-reset:${owner}`, crypto.randomUUID())
    for (const d of await this.list(owner)) await this.remove(d.key)
  },
  async clearWork(owner: string, workId: number) {
    localStorage.setItem(`work-drafts-reset:${owner}:${workId}`, crypto.randomUUID())
    for (const d of await this.list(owner, workId)) await this.remove(d.key)
  },
}
