import { nextTick, onBeforeUnmount, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import type AppDialog from '../components/AppDialog.vue'
// 作品彈窗在瀏覽器返回或路由離開時，沿用同一份草稿確認。
export function useWorkDialogGuard(dialog: Ref<InstanceType<typeof AppDialog> | undefined>, visible: () => boolean, returnFromChild?: () => boolean | Promise<boolean>) {
  const router = useRouter()
  const remove = router.beforeEach(async () => {
    if (!visible()) return true
    // 閱讀子視窗中的返回先恢復選取流程，保留尚未提交的素材。
    if (await returnFromChild?.()) return false
    await nextTick()
    return !visible() || (await dialog.value?.confirmNavigation()) === true
  })
  onBeforeUnmount(remove)
}
