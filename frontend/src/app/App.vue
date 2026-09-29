<script setup lang="ts">
import AppNavbar from './AppNavbar.vue'
import MeetingView from '@/features/meeting/MeetingView.vue'
</script>

<template>
  <div class="app-shell">
    <AppNavbar />
    <MeetingView />
  </div>
</template>

<style scoped>
.app-shell { min-height: 100dvh; display: grid; grid-template-rows: auto minmax(0, 1fr); }
</style>
