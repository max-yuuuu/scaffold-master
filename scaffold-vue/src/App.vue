<script setup lang="ts">
import ToastHost from '@yuanda-lowcode-ai/scaffold-ui/components/ui/ToastHost.vue'
import ConfirmHost from '@yuanda-lowcode-ai/scaffold-ui/components/ui/ConfirmHost.vue'
</script>

<template>
  <router-view />
  <ToastHost />
  <ConfirmHost />
  <div class="grain-layer" aria-hidden="true" />
</template>
