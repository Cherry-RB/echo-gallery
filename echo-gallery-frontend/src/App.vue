<script setup lang="ts">
import { ref, onMounted } from 'vue'

const debugInfo = ref('')
const showDebug = ref(true)

onMounted(() => {
  // 等排版完全穩定後再抓，避免抓到還沒 render 完成的中間值
  setTimeout(() => {
    const html = document.documentElement
    const body = document.body

    const lines: string[] = []
    lines.push(`window.innerWidth: ${window.innerWidth}`)
    lines.push(`document.documentElement.clientWidth: ${html.clientWidth}`)
    lines.push(`document.documentElement.scrollWidth: ${html.scrollWidth}`)
    lines.push(`document.body.scrollWidth: ${body.scrollWidth}`)
    lines.push(`devicePixelRatio: ${window.devicePixelRatio}`)
    lines.push(`matchMedia(max-width:1200px): ${window.matchMedia('(max-width: 1200px)').matches}`)
    lines.push('---撐寬版面的可疑元素---')

    // 找出所有「實際右邊界超過目前 viewport 寬度」的元素
    const all = document.querySelectorAll('*')
    const offenders: string[] = []
    all.forEach((el) => {
      const rect = (el as HTMLElement).getBoundingClientRect()
      if (rect.right > window.innerWidth + 1) {
        const tag = el.tagName.toLowerCase()
        const cls = (el as HTMLElement).className
        const clsStr = typeof cls === 'string' ? cls : ''
        offenders.push(`<${tag} class="${clsStr}"> right=${Math.round(rect.right)} width=${Math.round(rect.width)}`)
      }
    })

    if (offenders.length === 0) {
      lines.push('(沒有找到超出邊界的元素)')
    } else {
      // 只顯示前 15 個，避免畫面爆版
      offenders.slice(0, 15).forEach((o) => lines.push(o))
    }

    debugInfo.value = lines.join('\n')
  }, 800)
})
</script>

<template>
  <div
    v-if="showDebug"
    style="position: fixed; top: 0; left: 0; right: 0; z-index: 999999; background: #000; color: #0f0; font-size: 11px; font-family: monospace; white-space: pre-wrap; padding: 8px; max-height: 50vh; overflow-y: auto; word-break: break-all;"
  >
    <button style="position: absolute; top: 4px; right: 4px; background: red; color: white; border: none; padding: 2px 8px;" @click="showDebug = false">關閉</button>
    {{ debugInfo || '偵測中...' }}
  </div>
  <router-view />
</template>
