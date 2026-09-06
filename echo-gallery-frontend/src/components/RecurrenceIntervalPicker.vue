<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: number | null
  loading?: boolean
  showPause?: boolean
}>(), {
  loading: false,
  showPause: false,
})

const emit = defineEmits<{
  (event: 'select', intervalDays: number): void
  (event: 'pause'): void
}>()

const fixedIntervals = [7, 14, 30, 60, 90, 180]
const intervalIncrements = [10, 30, 100]
const customInterval = ref<number | undefined>(props.modelValue ?? 10)

watch(() => props.modelValue, (value) => {
  customInterval.value = value ?? 10
})

const selectInterval = (intervalDays: number) => {
  if (intervalDays < 1 || intervalDays > 365 || props.loading) return
  emit('select', intervalDays)
}

const increaseInterval = (increment: number) => {
  if (props.modelValue == null) return
  selectInterval(props.modelValue + increment)
}

const applyCustomInterval = () => {
  if (customInterval.value == null) return
  selectInterval(customInterval.value)
}
</script>

<template>
  <div class="recurrence-picker">
    <section class="picker-section">
      <span class="section-label">固定週期</span>
      <div class="interval-grid">
        <button
          v-for="days in fixedIntervals"
          :key="days"
          type="button"
          class="interval-option"
          :class="{ 'is-current': modelValue === days }"
          :disabled="loading"
          @click="selectInterval(days)"
        >
          {{ days }} 天
        </button>
      </div>
    </section>

    <section class="picker-section">
      <span class="section-label">延長目前週期</span>
      <div class="increment-grid">
        <button
          v-for="days in intervalIncrements"
          :key="days"
          type="button"
          class="interval-option"
          :disabled="loading || modelValue == null || modelValue + days > 365"
          @click="increaseInterval(days)"
        >
          +{{ days }} 天
        </button>
      </div>
    </section>

    <section class="custom-row">
      <el-input-number
        v-model="customInterval"
        :min="1"
        :max="365"
        :controls="false"
        :disabled="loading"
        aria-label="自訂回流天數"
        placeholder="自訂天數"
      />
      <el-button :disabled="loading || customInterval == null" @click="applyCustomInterval">
        套用
      </el-button>
    </section>

    <button
      v-if="showPause"
      type="button"
      class="pause-action"
      :disabled="loading"
      @click="emit('pause')"
    >
      暫停回流
    </button>
  </div>
</template>

<style scoped>
.recurrence-picker {
  display: flex;
  flex-direction: column;
  gap: 14px;
  font-family: var(--el-font-family);
  font-size: var(--type-ui);
}

.picker-section {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.section-label {
  color: var(--el-text-color-secondary);
  font-size: var(--type-meta);
}

.interval-grid,
.increment-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
}

.interval-option {
  min-height: 32px;
  padding: 5px 8px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
  color: var(--el-text-color-regular);
  font-family: var(--el-font-family);
  font-size: var(--type-ui);
  line-height: 1.4;
  cursor: pointer;
  transition: border-color 0.15s ease, color 0.15s ease, background-color 0.15s ease;
}

.interval-option:hover:not(:disabled) {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
}

.interval-option.is-current {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.interval-option:disabled,
.pause-action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.custom-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.custom-row :deep(.el-input-number) {
  width: 100%;
}
.custom-row :deep(.el-input__inner) {
  font-size: var(--type-ui);
}

.pause-action {
  padding: 10px 0 0;
  border: 0;
  border-top: 1px solid var(--el-border-color-lighter);
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.pause-action:hover:not(:disabled) {
  color: var(--el-color-danger);
}
</style>
