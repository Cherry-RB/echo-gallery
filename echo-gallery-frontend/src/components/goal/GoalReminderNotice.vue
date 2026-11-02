<script setup lang="ts">
import { computed } from 'vue'
import { useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { goalApi } from '../../utils/api/goalApi'

const client = useQueryClient()
const reminders = useQuery({ queryKey: ['goal-reminders'], queryFn: goalApi.reminders })
const due = computed(() => reminders.data.value || [])
function after(hours: number) { return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString() }
async function snooze(goalId: number, version: number, hours: number) {
  try { await goalApi.snoozeReminder(goalId, after(hours), version); await client.invalidateQueries({ queryKey: ['goal-reminders'] }); ElMessage.success(hours === 24 ? '明天再提醒' : '稍後再提醒') }
  catch { ElMessage.error('設定稍後提醒失敗，請重新整理後再試。') }
}
</script>

<template>
  <el-alert v-if="due.length" type="info" :closable="false" class="goal-reminder-notice">
    <template #title>有 {{ due.length }} 個目標等待復盤</template>
    <div class="goal-reminder-list"><div v-for="reminder in due" :key="reminder.goalId"><router-link :to="`/goals/${reminder.goalId}`">{{ reminder.goalName }}</router-link><span>應於 {{ reminder.dueOn }} 回顧</span><el-button link type="primary" size="small" @click="snooze(reminder.goalId, reminder.version, 1)">1 小時後</el-button><el-button link type="primary" size="small" @click="snooze(reminder.goalId, reminder.version, 24)">明天</el-button></div></div>
  </el-alert>
</template>

<style scoped>
.goal-reminder-notice { margin-bottom: var(--space-sm); }.goal-reminder-list { display: grid; gap: var(--space-xs); margin-top: var(--space-xs); }.goal-reminder-list > div { display: flex; flex-wrap: wrap; align-items: baseline; gap: var(--space-xs); }.goal-reminder-list span { color: var(--el-text-color-secondary); font-size: var(--type-metadata); }.goal-reminder-list a { color: var(--el-color-primary); font-size: var(--type-secondary); text-decoration: none; }.goal-reminder-list a:hover { text-decoration: underline; }.goal-reminder-list .el-button { margin-left: 0; }
</style>
