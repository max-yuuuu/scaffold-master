<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ProtoDesigner from './proto/ProtoDesigner.vue'
import LiveDesigner from './LiveDesigner.vue'

/**
 * 设计器路由入口：`/lowcode/designer/:页面 ID`。
 *
 * 默认渲染 1:1 视觉原型（`proto/`，独占标签页、不接数据）；带 `?mode=live` 才渲染
 * 已接数据那一版（真 design_json + 真保存 + 真发布）。两条共用同一条路由与同一个登录守卫，
 * 等 design_json 升到 1.1、原型那份形状能产出生成物时，这个开关就该消失。
 */
const route = useRoute()
const live = computed(() => route.query.mode === 'live')
</script>

<template>
  <LiveDesigner v-if="live" />
  <ProtoDesigner v-else />
</template>
