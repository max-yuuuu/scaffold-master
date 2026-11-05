<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Button } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { floatBarEnter, floatBarLeave } from '@yuanda-lowcode-ai/scaffold-ui/composables/motion'
import { useNarrowScreen } from '@yuanda-lowcode-ai/scaffold-ui/composables/narrow'
import { useUnsavedGuard } from '@yuanda-lowcode-ai/scaffold-ui/composables/unsavedGuard'
import { providePrototype } from './context.ts'
import './proto.css'
import ProtoTopbar from './ProtoTopbar.vue'
import ProtoRail from './ProtoRail.vue'
import ProtoCanvas from './ProtoCanvas.vue'
import ProtoInspector from './ProtoInspector.vue'
import ProtoOverlays from './ProtoOverlays.vue'

/**
 * 1:1 视觉原型的设计器整页：独占浏览器标签页，没有导航轨也没有控制台顶栏。
 *
 * 这一版**不接数据**：画布、属性、事件、发布全在内存里改，保存与发布只动本地计数。
 * 已接数据那一版（真 design_json + 真保存 + 真发布）在同一个路由的 `?mode=live` 上，
 * 等 design_json 升到 1.1、这份形状能产出生成物时再合回来。
 */
const proto = providePrototype()

// 设计器独占整个标签页，没有返回按钮，离开就是关标签页 —— 脏了必须拦一道
useUnsavedGuard(() => proto.unsavedCount.value > 0)

// 窄屏判定走平台那一份 narrow.ts：这里的 JS 断点必须与 proto.css 那条 @media 同一个数，
// 错开一格就是抽屉展开着但版式还是三栏 —— 两边各写各的没人会报错，只有量得出来。
const narrow = useNarrowScreen()
watch(narrow, value => proto.setNarrow(value), { immediate: true })

const collapsedLeft = () => proto.leftCollapsed.value || proto.previewMode.value
const collapsedRight = () => proto.rightCollapsed.value || proto.previewMode.value
const paneDrawerOpen = computed(() => proto.narrow.value && !proto.previewMode.value
  && (!collapsedLeft() || !collapsedRight()))

/** 提示牌堆：最多 3 张，越旧越往上缩 5%、透明度减 .28 */
const toastStyle = (index: number) => ({
  transform: `translateY(${-index * 50}px) scale(${1 - index * 0.05})`,
  opacity: String(Math.max(0.2, 1 - index * 0.28)),
})

// 倒计时细环要跟着秒针走，不然它只是一个静止的圆
const clock = ref(Date.now())
let clockTimer: number | undefined

function ringOffset(bornAt: number) {
  const left = Math.max(0, 1 - (clock.value - bornAt) / 3600)
  return 50.3 * (1 - left)
}

function onKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    proto.openOverlay('command')
  } else if (event.key === 'Escape') {
    // 窄屏抽屉是盖在画布上的，Esc 先关它；浮层（命令面板/抽屉/弹窗）优先级更高
    if (proto.overlays.value.length) {
      event.preventDefault()
      proto.closeOverlay()
    } else if (paneDrawerOpen.value) {
      event.preventDefault()
      proto.closePanes()
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  clockTimer = window.setInterval(() => { clock.value = Date.now() }, 100)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  if (clockTimer !== undefined) window.clearInterval(clockTimer)
})
</script>

<template>
  <div class="pf-stage">
    <div class="pf-chassis">
      <ProtoTopbar />

      <div :class="['pf-body', proto.narrow.value && 'is-narrow', collapsedLeft() && 'is-left-collapsed', collapsedRight() && 'is-right-collapsed']">
        <ProtoRail v-if="!collapsedLeft()" />
        <ProtoCanvas />
        <ProtoInspector v-if="!collapsedRight()" />

        <div v-if="paneDrawerOpen" class="pf-pane-scrim" aria-hidden="true" @click="proto.closePanes()" />
      </div>

      <!-- 未保存改动浮条：与列表页那条批量操作浮条同一条（.float-bar 材质、同一套 plate 色与升起动画） -->
      <Teleport to="body">
        <Transition :css="false" @enter="floatBarEnter" @leave="floatBarLeave">
          <div v-if="proto.unsavedCount.value > 0 && !proto.toasts.value.length" class="float-bar" role="region" aria-label="未保存改动">
            <span class="unsaved-dot" aria-hidden="true" />
            <span class="float-bar-count">有 <b class="tnum">{{ proto.unsavedCount.value }}</b> 项画布改动尚未保存</span>
            <span class="float-bar-sep" aria-hidden="true" />
            <Button variant="text" size="sm" icon="undo" :disabled="!proto.canUndo.value" aria-label="撤销改动" title="撤销改动" @click="proto.undo()" />
            <Button variant="text" size="sm" icon="redo" :disabled="!proto.canRedo.value" aria-label="重做改动" title="重做改动" @click="proto.redo()" />
            <Button variant="text" size="sm" danger @click="proto.discardChanges()">放弃改动</Button>
            <Button variant="text" size="sm" @click="proto.saveDraft()">立即保存草稿</Button>
          </div>
        </Transition>
      </Teleport>

      <!-- 提示牌堆 -->
      <div class="pf-toasts" role="status" aria-live="polite">
        <div v-for="(toast, index) in [...proto.toasts.value].reverse()" :key="toast.id" :class="['pf-toast', `tone-${toast.tone}`]" :style="toastStyle(index)">
          <span class="dot" />
          <span>{{ toast.text }}</span>
          <span class="ring" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 22 22">
              <circle class="bg" cx="11" cy="11" r="8" />
              <circle class="fg" cx="11" cy="11" r="8" :stroke-dasharray="50.3" :stroke-dashoffset="ringOffset(toast.bornAt)" />
            </svg>
          </span>
        </div>
      </div>

      <ProtoOverlays />
    </div>
  </div>
</template>

<style scoped>
/* 脏状态那颗青柠点：平台那条批量浮条没有这层语义，是设计器自己加的。
   撤销/重做两颗直接用平台 Button（variant=text size=sm icon=…），浮条高度才由同一套控件尺寸决定 */
.unsaved-dot { width: 8px; height: 8px; border-radius: 999px; background: var(--lime); box-shadow: 0 0 0 3px rgba(130, 221, 45, .24); flex: none; }
</style>
