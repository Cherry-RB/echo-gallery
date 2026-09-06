<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  content: string
  lines?: number
}>(), {
  lines: 6,
})

const contentElement = ref<HTMLElement>()
const expanded = ref(false)
const canExpand = ref(false)

const measureOverflow = () => {
  if (!contentElement.value || expanded.value) return
  canExpand.value = contentElement.value.scrollHeight > contentElement.value.clientHeight + 1
}

const toggleExpanded = async () => {
  expanded.value = !expanded.value
  if (!expanded.value) {
    await nextTick()
    measureOverflow()
  }
}

const handleResize = () => {
  if (!expanded.value) measureOverflow()
}

watch(
  () => [props.content, props.lines],
  async () => {
    expanded.value = false
    await nextTick()
    measureOverflow()
  },
)

onMounted(async () => {
  await nextTick()
  measureOverflow()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
})
</script>

<template>
  <div class="expandable-text">
    <p
      ref="contentElement"
      class="expandable-content"
      :class="{ expanded }"
      :style="expanded ? undefined : { WebkitLineClamp: String(lines) }"
    >
      {{ content }}
    </p>
    <button
      v-if="canExpand || expanded"
      type="button"
      class="expand-button"
      :aria-expanded="expanded"
      @click.stop="toggleExpanded"
    >
      {{ expanded ? '收起' : '展開全文' }}
    </button>
  </div>
</template>

<style scoped>
.expandable-content {
  display: -webkit-box;
  overflow: hidden;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  -webkit-box-orient: vertical;
}

.expandable-content.expanded {
  display: block;
  overflow: visible;
}

.expand-button {
  margin-top: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  font-size: var(--type-caption);
  cursor: pointer;
}

.expand-button:hover,
.expand-button:focus-visible {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}
</style>
