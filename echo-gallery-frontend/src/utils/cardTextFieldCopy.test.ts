import { describe, expect, it } from 'vitest'
import { cardTextFieldCopy } from './cardTextFieldCopy'

describe('cardTextFieldCopy', () => {
  it('使用統一的 Card 文字欄位語意', () => {
    expect(cardTextFieldCopy.cardNote.label).toBe('卡片筆記')
    expect(cardTextFieldCopy.content.label).toBe('更多內容')
  })

  it('不再暗示 Card 是行動或覆盤容器', () => {
    const copy = Object.values(cardTextFieldCopy)
      .flatMap(field => [field.label, field.placeholder])
      .join(' ')

    expect(copy).not.toMatch(/Plan|Review|Action|行動|覆盤/)
  })
})
