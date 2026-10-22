import { computed, ref } from 'vue'
import { workDrafts, type WorkDraft } from '../utils/workDrafts'
export function useWorkAutosave(owner: string, workId: number, initial: string, initialVersion: number,
  send: (body: string, version: number) => Promise<{ body: string; bodyVersion: number }>) {
  const body = ref(initial), acknowledged = ref(initial), version = ref(initialVersion)
  const saving = ref(false), error = ref(''), conflict = ref(false), storageError = ref('')
  const dirty = computed(() => body.value !== acknowledged.value)
  let instance = crypto.randomUUID(), key = `${owner}:${workId}:${instance}`
  const generation = workDrafts.generation(owner, workId)
  let sequence = 0, stopped = false, timer: ReturnType<typeof setTimeout> | undefined, maxTimer: ReturnType<typeof setTimeout> | undefined
  // 每個編輯分頁各有一筆草稿，且同一分頁依序寫入，避免舊回應移除新草稿。
  let persistence = Promise.resolve()
  function backup() {
    const draft: WorkDraft = { key, owner, workId, instance, body: body.value, version: version.value, sequence: ++sequence, savedAt: Date.now() }
    const preserve = dirty.value || saving.value || conflict.value
    persistence = persistence.catch(() => {}).then(async () => {
      // Demo 重設或作品刪除後，其他分頁不能重新寫回已清理的草稿。
      if (workDrafts.generation(owner, workId) !== generation) {
        stopped = true; clearTimers(); conflict.value = true
        error.value = '這份作品的本機草稿已清理。請保留需要的內容後重新載入。'
        await workDrafts.remove(draft.key); return
      }
      if (preserve) await workDrafts.put(draft); else await workDrafts.remove(draft.key)
      if (workDrafts.generation(owner, workId) !== generation) {
        stopped = true; clearTimers(); conflict.value = true
        error.value = '這份作品的本機草稿已清理。請保留需要的內容後重新載入。'
        await workDrafts.remove(draft.key)
      }
      storageError.value = ''
    }).catch(() => { storageError.value = '無法建立本機復原草稿，請先複製或下載尚未儲存的內容。' })
    return persistence
  }
  function clearTimers() { clearTimeout(timer); clearTimeout(maxTimer); timer = undefined; maxTimer = undefined }
  async function flush() {
    clearTimers()
    if (saving.value || conflict.value || stopped || !dirty.value) return
    saving.value = true; error.value = ''; await backup()
    const sent = body.value
    if (stopped) { saving.value = false; return }
    try {
      const result = await send(sent, version.value)
      acknowledged.value = result.body; version.value = result.bodyVersion
    } catch (e) {
      const status = (e as { response?: { status: number } }).response?.status
      // 回應不明時亦停止自動重試，重新載入時再確認伺服器版本。
      conflict.value = status === 409 || !status
      error.value = conflict.value ? '這份作品可能已有較新內容，目前修改尚未儲存。請先複製或下載，再重新載入。' : status === 401 || status === 403 ? '登入已失效，正文尚未儲存。請保留草稿，重新登入同一帳號後再復原。' : '正文尚未儲存，請保留草稿後重試。'
    } finally {
      saving.value = false; await backup()
      if (dirty.value && !error.value && !stopped) schedule()
    }
  }
  function schedule() {
    clearTimeout(timer)
    if (!dirty.value && !saving.value) { clearTimers(); return }
    if (stopped || conflict.value || error.value) return
    timer = setTimeout(() => void flush(), 1200)
    maxTimer ??= setTimeout(() => void flush(), 8000)
  }
  function edit(value: string) { body.value = value; void backup(); schedule() }
  function reset(value: string, revision: number) {
    instance = crypto.randomUUID(); key = `${owner}:${workId}:${instance}`
    clearTimers(); body.value = value; acknowledged.value = value; version.value = revision; error.value = ''; conflict.value = false
    // 保留重新載入前的草稿，使用者明確捨棄才移除。
  }
  async function dispose() { stopped = true; clearTimers(); await backup() }
  return { body, version, saving, dirty, error, conflict, storageError, edit, flush, reset, backup, dispose,
    status: computed(() => conflict.value ? '尚未儲存' : saving.value ? '儲存中' : error.value ? '儲存失敗' : dirty.value ? '編輯中' : '已儲存') }
}
