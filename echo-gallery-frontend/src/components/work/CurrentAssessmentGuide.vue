<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const assessmentTemplate = `目前局勢：

這可能意味著：

可行方針：

限制、風險與退路：

目前傾向：`

const expanded = ref(false)
const hasContent = computed(() => Boolean(props.modelValue?.trim()))

const applyTemplate = () => {
  if (hasContent.value) return
  emit('update:modelValue', assessmentTemplate)
  expanded.value = false
}
</script>

<template>
  <div class="assessment-guide">
    <button
      type="button"
      class="guide-trigger"
      :aria-expanded="expanded"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起研判框架' : '使用研判框架' }}
    </button>

    <aside v-if="expanded" class="guide-panel" aria-label="整體研判填寫提示">
      <p>需要整理思緒時，可從這些角度開始；不必逐項回答。</p>
      <ul>
        <li>目前局勢與新訊號</li>
        <li>可能的解讀與關鍵未知</li>
        <li>可用資源、限制與風險</li>
        <li>可行方針、退路與目前傾向</li>
      </ul>
      <button
        type="button"
        class="apply-template-button"
        :disabled="hasContent"
        @click="applyTemplate"
      >
        套用空白框架
      </button>
      <p v-if="hasContent" class="existing-content-hint">
        欄位已有內容，為避免覆蓋，請先自行整理或清空後再套用。
      </p>
    </aside>
  </div>
</template>

<style scoped>
.assessment-guide {
  width: 100%;
  margin-bottom: 8px;
}

.guide-trigger,
.apply-template-button {
  border: 0;
  background: transparent;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}

.guide-trigger {
  padding: 0;
  font-size: var(--type-caption);
}

.guide-trigger:hover,
.guide-trigger:focus-visible,
.apply-template-button:hover:not(:disabled),
.apply-template-button:focus-visible:not(:disabled) {
  color: var(--el-color-primary-light-3);
  text-decoration: underline;
}

.guide-panel {
  margin-top: 8px;
  padding: 12px 14px;
  box-sizing: border-box;
  border-left: 3px solid var(--el-color-primary-light-5);
  border-radius: 0 6px 6px 0;
  background: var(--el-color-primary-light-9);
  color: var(--el-text-color-secondary);
  font-size: var(--type-caption);
  line-height: var(--leading-ui);
}

.guide-panel p {
  margin-bottom: 6px;
}

.guide-panel ul {
  display: flex;
  margin: 0;
  padding-left: 18px;
  flex-wrap: wrap;
  gap: 4px 18px;
}

.guide-panel li {
  min-width: 220px;
}

.apply-template-button {
  margin-top: 10px;
  padding: 5px 0;
  font-size: var(--type-caption);
  font-weight: 600;
}

.apply-template-button:disabled {
  color: var(--el-text-color-placeholder);
  cursor: not-allowed;
}

.guide-panel .existing-content-hint {
  margin: 4px 0 0;
  color: var(--el-text-color-placeholder);
  font-size: var(--type-meta);
}

@media (max-width: 600px) {
  .guide-panel ul {
    display: block;
  }

  .guide-panel li + li {
    margin-top: 2px;
  }
}
</style>
