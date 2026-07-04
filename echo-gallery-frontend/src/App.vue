<script setup lang="ts">
import { ref, onMounted } from 'vue'

const debugInfo = ref('')
const showDebug = ref(true)

function describeEl(selector: string) {
  const els = document.querySelectorAll(selector)
  if (els.length === 0) return [`${selector}: (找不到這個元素)`]
  const lines: string[] = []
  els.forEach((el, i) => {
    const cs = getComputedStyle(el as HTMLElement)
    const rect = (el as HTMLElement).getBoundingClientRect()
    lines.push(
      `${selector}[${i}] display=${cs.display} width=${Math.round(rect.width)} ` +
      `visibility=${cs.visibility} opacity=${cs.opacity}`
    )
  })
  return lines
}

onMounted(() => {
  setTimeout(() => {
    const lines: string[] = []
    lines.push(`innerWidth: ${window.innerWidth} | matchMedia1200: ${window.matchMedia('(max-width:1200px)').matches}`)
    lines.push('--- .main-layout ---')
    lines.push(...describeEl('.main-layout'))
    lines.push('--- .desktop-only ---')
    lines.push(...describeEl('.desktop-only'))
    lines.push('--- .sidebar ---')
    lines.push(...describeEl('.sidebar'))
    lines.push('--- .stats-panel ---')
    lines.push(...describeEl('.stats-panel'))
    lines.push('--- .mobile-header ---')
    lines.push(...describeEl('.mobile-header'))
    lines.push('--- .content-viewport ---')
    lines.push(...describeEl('.content-viewport'))

    // 額外：找出目前套用在 .desktop-only 身上的所有 CSS 規則來源
    const target = document.querySelector('.desktop-only')
    if (target) {
      lines.push('--- .desktop-only 的 class 屬性原始值 ---')
      lines.push(target.className)
      lines.push('--- .desktop-only 的所有 data- 屬性 ---')
      lines.push(JSON.stringify((target as HTMLElement).dataset))
    }

    debugInfo.value = lines.join('\n')
  }, 800)
})
</script>

<template>
  <div
    v-if="showDebug"
    style="position: fixed; top: 0; left: 0; right: 0; z-index: 999999; background: #000; color: #0f0; font-size: 10px; font-family: monospace; white-space: pre-wrap; padding: 8px; max-height: 60vh; overflow-y: auto; word-break: break-all;"
  >
    <button style="position: absolute; top: 4px; right: 4px; background: red; color: white; border: none; padding: 2px 8px;" @click="showDebug = false">關閉</button>
    {{ debugInfo || '偵測中...' }}
  </div>
  <router-view />
</template>
