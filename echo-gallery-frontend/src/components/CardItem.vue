<script setup lang="ts">
import CardTextMode from './CardTextMode.vue';
import { type BoardType } from '../types/board.ts';
import type { CardDto } from '../types/card.ts';

const props = defineProps<{
  data: CardDto;
  viewMode: string;
  boardType: BoardType;
}>();

const emit = defineEmits<{
  (e: "open-detail", card: CardDto): void;
}>();

function openDetail() {
  emit("open-detail", props.data);
}

</script>

<template>
    <CardTextMode
        v-if="viewMode === 'text'"
        :data="data"
        :board-type="boardType"
        @open-detail="openDetail">
    </CardTextMode>
</template>
