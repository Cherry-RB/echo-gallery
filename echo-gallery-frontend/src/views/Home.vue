<!-- <template>
  <h2>Home Page</h2>
</template> -->

<script setup>
import { useCounterStore } from '../stores/counter.ts'
import Card from '../components/Card.vue'
import { ref } from 'vue'
import { generateCards } from '../mock-data/card-mock-data.ts'; 


// 模擬從後端取得的資料，符合 DTO 結構
const cardList = generateCards(5);

const store = useCounterStore()
</script>

<template>
  <h2>Home</h2>
  <p>{{ store.count }}</p>
  <el-button @click="store.increment()">+1</el-button>
  <Card v-for="item in cardList"
  :key="item.id"
  :data="item"></Card>
</template>