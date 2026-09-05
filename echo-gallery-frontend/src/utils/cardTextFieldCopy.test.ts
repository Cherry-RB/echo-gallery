import { describe, expect, it } from 'vitest'
import { cardTextFieldCopy } from './cardTextFieldCopy'

describe('cardTextFieldCopy', () => {
  it('使用統一的 Card 文字欄位語意', () => {
    expect(cardTextFieldCopy.reason.label).toBe('為什麼留下它／我思我長')
    expect(cardTextFieldCopy.summary.label).toBe('我見我聞／內容重點')
    expect(cardTextFieldCopy.content.label).toBe('延伸筆記／完整內容')
  })

  it('不再暗示 Card 是行動或覆盤容器', () => {
    const copy = Object.values(cardTextFieldCopy)
      .flatMap(field => [field.label, field.placeholder])
      .join(' ')

    expect(copy).not.toMatch(/Plan|Review|Action|行動|覆盤/)
  })
})
