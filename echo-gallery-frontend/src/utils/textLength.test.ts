import { describe, expect, it } from 'vitest'
import { getTextLength, trimToTextLength } from './textLength'

describe('textLength', () => {
  it('以 Unicode 字元而非 UTF-16 code unit 計數', () => {
    expect(getTextLength('中文🙂')).toBe(3)
  })

  it('依相同規則截斷輸入內容', () => {
    expect(trimToTextLength('甲🙂乙', 2)).toBe('甲🙂')
  })
})
