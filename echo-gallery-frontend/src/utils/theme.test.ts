import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

function mockMatchMedia(initialMatches: boolean) {
  let listener: ((event: MediaQueryListEvent) => void) | undefined
  const mediaQuery = {
    matches: initialMatches,
    media: '(prefers-color-scheme: dark)',
    onchange: null,
    addEventListener: vi.fn((_type: string, callback: (event: MediaQueryListEvent) => void) => {
      listener = callback
    }),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  } as unknown as MediaQueryList
  vi.stubGlobal('matchMedia', vi.fn(() => mediaQuery))
  return {
    change(matches: boolean) {
      listener?.({ matches } as MediaQueryListEvent)
    },
  }
}

describe('theme preference', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    document.documentElement.style.removeProperty('color-scheme')
  })

  afterEach(() => vi.unstubAllGlobals())

  it('套用並保存手動深色偏好', async () => {
    mockMatchMedia(false)
    const { initializeTheme, setThemePreference } = await import('./theme')

    initializeTheme()
    setThemePreference('dark')

    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(localStorage.getItem('echo-gallery-theme')).toBe('dark')
  })

  it('沒有保存偏好時跟隨系統變化', async () => {
    const media = mockMatchMedia(false)
    const { initializeTheme } = await import('./theme')

    initializeTheme()
    expect(document.documentElement.classList.contains('dark')).toBe(false)

    media.change(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
  })

  it('手動淺色不受後續系統切換影響', async () => {
    const media = mockMatchMedia(true)
    const { initializeTheme, setThemePreference } = await import('./theme')

    initializeTheme()
    setThemePreference('light')
    media.change(true)

    expect(document.documentElement.classList.contains('dark')).toBe(false)
    expect(localStorage.getItem('echo-gallery-theme')).toBe('light')
  })
})
