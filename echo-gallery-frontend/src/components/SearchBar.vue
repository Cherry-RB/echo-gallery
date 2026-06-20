<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
const input2 = ref('')
</script>

<template>
<div class="input-container">
    <el-input
        v-model="input2"
        placeholder="找尋卡片"
        :prefix-icon="Search"
    />
    <el-button>搜尋</el-button>
    </div>
</template>

<style scoped>
.input-container {
  display: flex;
  gap: 0.25rem;
  margin: 1rem;
}
</style>