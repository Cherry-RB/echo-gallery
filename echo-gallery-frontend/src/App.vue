<script setup lang="ts">
import { ref, onMounted } from 'vue'

const debugInfo = ref('')
const showDebug = ref(true)

onMounted(() => {
  setTimeout(() => {
    const lines: string[] = []
    lines.push(`innerWidth: ${window.innerWidth} | matchMedia1200: ${window.matchMedia('(max-width:1200px)').matches}`)
    lines.push(`document.styleSheets.length: ${document.styleSheets.length}`)

    let foundDesktopOnly = 0
    let foundMobileHeader = 0
    let foundMediaBlocks = 0

    for (let i = 0; i < document.styleSheets.length; i++) {
      const sheet = document.styleSheets[i]
      let rules: CSSRuleList | null = null
      try {
        rules = sheet.cssRules
      } catch (e) {
        lines.push(`sheet[${i}] href=${sheet.href} -> 無法讀取 (可能跨網域): ${e}`)
        continue
      }
      if (!rules) continue

      lines.push(`sheet[${i}] href=${sheet.href} rules=${rules.length}`)

      for (let j = 0; j < rules.length; j++) {
        const rule = rules[j]
        if (rule instanceof CSSMediaRule) {
          foundMediaBlocks++
          if (rule.media.mediaText.includes('1200')) {
            lines.push(`  [MEDIA] ${rule.media.mediaText} (內含 ${rule.cssRules.length} 條規則)`)
            for (let k = 0; k < rule.cssRules.length; k++) {
              const inner = rule.cssRules[k] as CSSStyleRule
              lines.push(`     -> ${inner.selectorText} { ${inner.style.cssText} }`)
            }
          }
        } else if (rule instanceof CSSStyleRule) {
          if (rule.selectorText && rule.selectorText.includes('desktop-only')) {
            foundDesktopOnly++
            lines.push(`  [直接規則] ${rule.selectorText} { ${rule.style.cssText} }`)
          }
          if (rule.selectorText && rule.selectorText.includes('mobile-header')) {
            foundMobileHeader++
            lines.push(`  [直接規則] ${rule.selectorText} { ${rule.style.cssText} }`)
          }
        }
      }
    }

    lines.push(`--- 總結 ---`)
    lines.push(`共找到 ${foundMediaBlocks} 個 @media 區塊`)
    lines.push(`.desktop-only 在 media 區塊外被直接找到: ${foundDesktopOnly} 次`)
    lines.push(`.mobile-header 在 media 區塊外被直接找到: ${foundMobileHeader} 次`)

    debugInfo.value = lines.join('\n')
  }, 800)
})
</script>

<template>
  <div
    v-if="showDebug"
    style="position: fixed; top: 0; left: 0; right: 0; z-index: 999999; background: #000; color: #0f0; font-size: 10px; font-family: monospace; white-space: pre-wrap; padding: 8px; max-height: 70vh; overflow-y: auto; word-break: break-all;"
  >
    <button style="position: absolute; top: 4px; right: 4px; background: red; color: white; border: none; padding: 2px 8px;" @click="showDebug = false">關閉</button>
    {{ debugInfo || '偵測中...' }}
  </div>
  <router-view />
</template>
