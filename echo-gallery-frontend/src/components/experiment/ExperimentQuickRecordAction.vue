<script setup lang="ts">
type RecordMode = 'TRY' | 'DISCOVERY'

const emit = defineEmits<{
  select: [mode: RecordMode]
}>()
</script>

<template>
  <el-dropdown trigger="click" @command="(command: RecordMode) => emit('select', command)">
    <button type="button" class="quick-record-trigger" aria-label="新增探索紀錄" @click.stop>＋ 記錄</button>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="TRY">＋ 試法</el-dropdown-item>
        <el-dropdown-item command="DISCOVERY">＋ 發現</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.quick-record-trigger { padding: 0; border: 0; background: transparent; color: var(--el-color-primary); cursor: pointer; font: inherit; font-size: var(--type-caption); line-height: var(--leading-ui); white-space: nowrap; }
.quick-record-trigger:hover, .quick-record-trigger:focus-visible { color: var(--el-color-primary-light-3); text-decoration: underline; }
</style>
