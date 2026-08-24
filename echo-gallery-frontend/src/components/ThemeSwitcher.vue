<script setup lang="ts">
import { computed } from 'vue'
import { ArrowDown, Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { useTheme, type ThemePreference } from '../utils/theme'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const { preference, setThemePreference } = useTheme()
const options = [
  { value: 'system' as const, label: '跟隨系統', icon: Monitor },
  { value: 'light' as const, label: '淺色', icon: Sunny },
  { value: 'dark' as const, label: '深色', icon: Moon },
]
const currentOption = computed(() =>
  options.find(option => option.value === preference.value) ?? options[0],
)

function selectTheme(command: ThemePreference) {
  setThemePreference(command)
}
</script>

<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="selectTheme">
    <button
      type="button"
      class="theme-trigger"
      :class="{ compact }"
      :aria-label="`外觀：${currentOption.label}`"
      :title="`外觀：${currentOption.label}`"
    >
      <el-icon><component :is="currentOption.icon" /></el-icon>
      <span v-if="!compact">{{ currentOption.label }}</span>
      <el-icon v-if="!compact" class="arrow"><ArrowDown /></el-icon>
    </button>

    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item
          v-for="option in options"
          :key="option.value"
          :command="option.value"
          :class="{ selected: option.value === preference }"
        >
          <el-icon><component :is="option.icon" /></el-icon>
          {{ option.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.theme-trigger {
  display: inline-flex;
  width: 100%;
  min-height: 36px;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  align-items: center;
  justify-content: flex-start;
  gap: 8px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-blank);
  cursor: pointer;
}

.theme-trigger:hover,
.theme-trigger:focus-visible {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  background: var(--el-fill-color-light);
}

.theme-trigger.compact {
  width: 36px;
  padding: 0;
  justify-content: center;
  border: 0;
  background: transparent;
  font-size: 20px;
}

.arrow {
  margin-left: auto;
}

:global(.el-dropdown-menu__item.selected) {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>
