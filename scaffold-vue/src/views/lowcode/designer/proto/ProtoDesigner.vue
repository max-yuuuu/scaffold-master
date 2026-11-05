<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
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

const collapsedLeft = () => proto.leftCollapsed.value || proto.previewMode.value
const collapsedRight = () => proto.rightCollapsed.value || proto.previewMode.value

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
  } else if (event.key === 'Escape' && proto.overlays.value.length) {
    event.preventDefault()
    proto.closeOverlay()
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

      <div :class="['pf-body', collapsedLeft() && 'is-left-collapsed', collapsedRight() && 'is-right-collapsed']">
        <ProtoRail v-if="!collapsedLeft()" />
        <ProtoCanvas />
        <ProtoInspector v-if="!collapsedRight()" />

        <button v-if="proto.leftCollapsed.value && !proto.previewMode.value" class="pf-expand is-left" type="button" @click="proto.leftCollapsed.value = false">
          <Icon name="sidebar" :size="15" />展开组件物料
        </button>
        <button v-if="proto.rightCollapsed.value && !proto.previewMode.value" class="pf-expand is-right" type="button" @click="proto.rightCollapsed.value = false">
          <Icon name="sliders" :size="15" />展开配置
        </button>
      </div>

      <!-- 未保存改动浮条：静态稿里它改的是本地计数，刷新即失 -->
      <Transition name="pf-fade">
        <div v-if="proto.unsavedCount.value > 0 && !proto.toasts.value.length" class="pf-float" role="region" aria-label="未保存改动">
          <span class="pf-float-dot" aria-hidden="true" />
          <span>有 {{ proto.unsavedCount.value }} 项画布改动尚未保存</span>
          <span class="sep" />
          <button type="button" class="pf-icon-btn" :disabled="!proto.canUndo.value" aria-label="撤销改动" title="撤销改动" @click="proto.undo()"><Icon name="undo" :size="14" /></button>
          <button type="button" class="pf-icon-btn" :disabled="!proto.canRedo.value" aria-label="重做改动" title="重做改动" @click="proto.redo()"><Icon name="redo" :size="14" /></button>
          <button type="button" class="pf-btn is-text is-danger" @click="proto.discardChanges()">放弃改动</button>
          <button type="button" class="pf-btn is-text" @click="proto.saveDraft()">立即保存草稿</button>
        </div>
      </Transition>

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
.pf-float-dot { width: 8px; height: 8px; border-radius: 999px; background: var(--lime); box-shadow: 0 0 0 3px rgba(130, 221, 45, .24); flex: none; }
.pf-float .pf-icon-btn { width: 28px; height: 28px; color: rgba(255, 255, 255, .78); }
.pf-float .pf-icon-btn:hover { background: rgba(255, 255, 255, .1); color: #fff; }
.pf-fade-enter-active, .pf-fade-leave-active { transition: opacity .24s var(--ease-premium), transform .24s var(--ease-premium); }
.pf-fade-enter-from, .pf-fade-leave-to { opacity: 0; transform: translate(-50%, 12px); }
</style>
