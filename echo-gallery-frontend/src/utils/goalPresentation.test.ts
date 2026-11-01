import { afterEach, describe, expect, it, vi } from 'vitest'
import { goalDates } from './goalPresentation'
describe('目標預設七天期間', () => {
  afterEach(() => vi.useRealTimers())
  it('依台北日期建立，跨年也加七個日曆日', () => {
    vi.useFakeTimers(); vi.setSystemTime(new Date('2026-12-30T17:00:00Z'))
    expect(goalDates()).toEqual({ startsOn: '2026-12-31', endsOn: '2027-01-07' })
  })
})
