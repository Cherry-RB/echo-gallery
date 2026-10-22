import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AppDialog from './AppDialog.vue'

const confirm = vi.hoisted(() => vi.fn())
vi.mock('element-plus', () => ({ ElMessageBox: { confirm } }))

const Dialog = defineComponent({
  props: ['modelValue'],
  template: '<section v-if="modelValue"><slot /><slot name="footer" /></section>',
})

describe('AppDialog 流程草稿基準', () => {
  it('重建對話框後，仍會以流程原始基準提示放棄變更', async () => {
    confirm.mockRejectedValueOnce('cancel')
    const wrapper = mount(AppDialog, {
      props: {
        modelValue: true,
        title: '用於作品',
        draft: { title: '尚未儲存的作品' },
        draftBaseline: JSON.stringify({ title: '' }),
      },
      global: { stubs: { 'el-dialog': Dialog } },
    })
    try {
      await nextTick()
      const dialog = wrapper.vm as unknown as { confirmNavigation: () => Promise<boolean> }
      expect(await dialog.confirmNavigation()).toBe(false)
      expect(confirm).toHaveBeenCalledOnce()
    } finally { wrapper.unmount() }
  })
})
