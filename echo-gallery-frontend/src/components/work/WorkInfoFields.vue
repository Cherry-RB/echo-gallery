<script setup lang="ts">
import type { ExperimentOption, WorkInfo, WorkMode } from '../../types/work'
const form = defineModel<WorkInfo>({ required: true })
defineProps<{ experiments?: ExperimentOption[]; originalMode?: WorkMode }>()
</script>
<template>
  <el-form-item label="作品名稱" required>
    <el-input v-model="form.title" maxlength="255" />
    <p class="app-field-counter">總字數：{{ form.title.length }} / 255</p>
  </el-form-item>
  <el-form-item label="主要編輯位置" required>
    <el-radio-group v-model="form.editorMode"><el-radio value="INTERNAL">內建 Markdown</el-radio><el-radio value="EXTERNAL">外部工具</el-radio></el-radio-group>
    <p v-if="originalMode && form.editorMode !== originalMode" class="app-field-help">更換後原正文與網址仍會保留；內外部內容不會同步。</p>
  </el-form-item>
  <el-form-item v-if="form.editorMode === 'EXTERNAL'" label="外部文件網址（選填）">
    <el-input v-model="form.externalUrl" maxlength="2048" placeholder="https://" />
    <p class="app-field-help">可稍後補上；有網址後即可開啟原始文件。</p>
  </el-form-item>
  <el-form-item label="簡短說明（選填）">
    <el-input v-model="form.description" type="textarea" :rows="3" maxlength="50000" />
    <p class="app-field-counter">總字數：{{ form.description?.length ?? 0 }} / 50,000</p>
  </el-form-item>
  <el-form-item label="主要實驗（選填）">
    <el-select v-model="form.experimentId" clearable placeholder="不指定實驗"><el-option v-for="e in experiments" :key="e.id" :value="e.id" :label="e.title + (e.archived ? '（已封存）' : '')" /></el-select>
    <p class="app-field-help">作品可以獨立存在，也能引用不同實驗的素材。</p>
  </el-form-item>
</template>
