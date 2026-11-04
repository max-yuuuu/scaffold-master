/* ============================================================
   @yuanda-lowcode-ai/scaffold-ui 对外出口。
   组件与物料可按目录（./components/ui）、按 barrel（本文件）或按单文件（./components/ui/Button.vue）引用；
   珍珠玻璃主题走独立入口 '@yuanda-lowcode-ai/scaffold-ui/theme.css'。
   ============================================================ */

export * from './components/ui'
export * from './components/business'
export { default as Icon } from './components/Icon.vue'
export { default as BarChart } from './components/charts/BarChart.vue'
export { default as HeatGrid } from './components/charts/HeatGrid.vue'
export { default as RelationGraph } from './components/charts/RelationGraph.vue'
export { default as Swimlane } from './components/charts/Swimlane.vue'
export { default as TickRing } from './components/charts/TickRing.vue'
export { default as TrendChart } from './components/charts/TrendChart.vue'

export type * from './types'

/* 注入点与物料自带的数据入口：宿主 main.ts 只需要 installScaffoldUi */
export * from './providers'
export { materialApi } from './api'
export * from './data'

export * from './composables/appearance'
export * from './composables/ask'
export * from './composables/confirm'
export * from './composables/dateValue'
export * from './composables/dept'
export * from './composables/dict'
export * from './composables/focus'
export * from './composables/list'
export * from './composables/motion'
export * from './composables/neural'
export * from './composables/permissionGraph'
export * from './composables/popover'
export * from './composables/toast'
export * from './composables/upload'
export * from './composables/view'
export * from './composables/viewport'
