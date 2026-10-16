<script setup lang="ts">
import { getTextLength } from '../../utils/textLength'
defineProps<{ value?: string | null; limit: number }>()
</script>
<template><p class="app-field-counter">總字數：{{ getTextLength(value) }} / {{ limit }}</p></template>
