<script setup lang="ts">
import { isNetworkError } from './stores/networkStatus';

</script>

<template>
  <div v-if="isNetworkError" class="network-banner">
    ⚠️ 目前無法連線到伺服器，部分功能可能無法使用
  </div>
  <router-view />
</template>
