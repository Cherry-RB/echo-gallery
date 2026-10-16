import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import CoverImage from './CoverImage.vue'
import { imageUrlError } from '../../utils/imageUrl'

describe('共用封面載入與失敗狀態', () => {
  it('列表與閱讀失敗後移除整個封面區域，更新網址可重新載入', async () => {
    for (const variant of ['collection', 'reading'] as const) {
      const wrapper = mount(CoverImage, { props: { src: 'https://example.com/broken.png', variant } })
      const oldImage = wrapper.get('img')
      await oldImage.trigger('error')
      expect(wrapper.find('.cover-media').exists()).toBe(false)
      await wrapper.setProps({ src: 'https://example.com/new.png' })
      expect(wrapper.find('img').exists()).toBe(true)
      await wrapper.get('img').trigger('load')
      expect(wrapper.get('img').classes()).not.toContain('is-pending')
      wrapper.unmount()
    }
  })

  it('編輯預覽失敗顯示提示，不修改原網址', async () => {
    const wrapper = mount(CoverImage, { props: { src: 'https://example.com/broken.png', variant: 'preview' } })
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('無法載入圖片')
    expect(wrapper.props('src')).toBe('https://example.com/broken.png')
    expect(wrapper.emitted('update:src')).toBeUndefined()
  })

  it('舊圖片的延遲錯誤不覆蓋新網址的成功狀態', async () => {
    const wrapper = mount(CoverImage, { props: { src: 'https://example.com/old.png', variant: 'preview' } })
    const oldImage = wrapper.get('img')
    await wrapper.setProps({ src: 'https://example.com/new.png' })
    await wrapper.get('img').trigger('load')
    await oldImage.trigger('error')
    expect(wrapper.get('img').attributes('src')).toBe('https://example.com/new.png')
    expect(wrapper.text()).not.toContain('無法載入圖片')
  })

  it('空白不顯示封面，不合法格式不發出圖片請求', () => {
    expect(mount(CoverImage, { props: { src: '' } }).find('img').exists()).toBe(false)
    const wrapper = mount(CoverImage, { props: { src: 'javascript:alert(1)', variant: 'preview' } })
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('僅接受 HTTP 或 HTTPS')
    expect(imageUrlError(' https://example.com/image ')).toBe('')
    expect(imageUrlError('https://example.com/' + 'a'.repeat(2048))).toContain('2048')
  })
})
