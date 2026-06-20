<script setup lang="ts">
import CardTextMode from './CardTextMode.vue';

defineProps({
    data: {
        type: Object,
        required: true
    },
    viewMode: {
        type: String,
        required: true
    }
})


</script>

<template>
    <CardTextMode
        v-if="viewMode === 'text'"
        :data="data"> 
    </CardTextMode>
</template>