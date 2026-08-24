import { computed, readonly, ref } from 'vue'

export type ThemePreference = 'system' | 'light' | 'dark'

const STORAGE_KEY = 'echo-gallery-theme'
const preference = ref<ThemePreference>('system')
const systemPrefersDark = ref(false)
const isInitialized = ref(false)
let mediaQuery: MediaQueryList | null = null

const isThemePreference = (value: string | null): value is ThemePreference =>
  value === 'system' || value === 'light' || value === 'dark'

const resolvedDark = computed(() =>
  preference.value === 'dark' || (preference.value === 'system' && systemPrefersDark.value),
)

function applyTheme() {
  document.documentElement.classList.toggle('dark', resolvedDark.value)
  document.documentElement.style.colorScheme = resolvedDark.value ? 'dark' : 'light'
}

function handleSystemThemeChange(event: MediaQueryListEvent) {
  systemPrefersDark.value = event.matches
  if (preference.value === 'system') applyTheme()
}

export function initializeTheme() {
  if (isInitialized.value || typeof window === 'undefined' || typeof document === 'undefined') return

  let storedPreference: string | null = null
  try {
    storedPreference = window.localStorage.getItem(STORAGE_KEY)
  } catch {
    // localStorage 不可用時仍可跟隨系統主題。
  }
  preference.value = isThemePreference(storedPreference) ? storedPreference : 'system'
  mediaQuery = window.matchMedia?.('(prefers-color-scheme: dark)') ?? null
  systemPrefersDark.value = mediaQuery?.matches ?? false
  mediaQuery?.addEventListener('change', handleSystemThemeChange)
  isInitialized.value = true
  applyTheme()
}

export function setThemePreference(nextPreference: ThemePreference) {
  preference.value = nextPreference
  try {
    window.localStorage.setItem(STORAGE_KEY, nextPreference)
  } catch {
    // 儲存失敗不影響目前頁面的主題切換。
  }
  applyTheme()
}

export function useTheme() {
  initializeTheme()
  return {
    preference: readonly(preference),
    isDark: readonly(resolvedDark),
    setThemePreference,
  }
}
