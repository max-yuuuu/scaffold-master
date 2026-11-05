<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { usePrototype } from './context.ts'
import NodeRenderer from './NodeRenderer.vue'
import { MATERIAL_LIBRARY, TONE_VAR, type CanvasNode } from './data.ts'

/* 画布：点阵底 + 视口胶囊 + 逐节点卡 + 按 clientY 实时算落点 + 1:1 幽灵预览。
   编辑态下节点内容整体 pointer-events: none —— 点画布任何东西都是选中这个节点，
   而不是在一份静态稿里填一个填不出结果的表单。 */
const proto = usePrototype()

const listEl = ref<HTMLElement | null>(null)
const tabsIndex = ref(0)

const canvasClass = computed(() => ['pf-canvas', `tone-${proto.pageConfig.canvasBgTone}`])

const canvasStyle = computed(() => ({
  maxWidth: proto.viewportWidth.value === 'auto' ? '100%' : `${proto.viewportWidth.value}px`,
}))

const WIDTH_PRESETS: Record<string, number[]> = {
  desktop: [1440, 1280, 1024],
  tablet: [834, 768],
  mobile: [414, 375],
}

const DEVICE_LABEL = { desktop: '桌面端', tablet: '平板端', mobile: '手机端' } as const

/** 鼠标落在哪个节点的上半区/下半区，就插到它前面/后面；落在幽灵预览里则保持不动，避免抖动 */
function computeDropIndex(clientY: number) {
  const container = listEl.value
  if (!container) return proto.nodes.value.length
  const ghost = container.querySelector<HTMLElement>('[data-ghost="true"]')
  if (ghost && proto.activeDropIndex.value !== null) {
    const rect = ghost.getBoundingClientRect()
    if (clientY >= rect.top - 10 && clientY <= rect.bottom + 10) return proto.activeDropIndex.value
  }
  const wrappers = Array.from(container.querySelectorAll<HTMLElement>('[data-top-index]'))
  for (let index = 0; index < wrappers.length; index += 1) {
    const rect = wrappers[index].getBoundingClientRect()
    if (clientY < rect.top + rect.height / 2) return index
  }
  return proto.nodes.value.length
}

function onCanvasDragOver(event: DragEvent) {
  if (proto.draggingMaterial.value || proto.draggingNodeId.value) {
    event.preventDefault()
    proto.canvasDragOver.value = true
    proto.activeDropIndex.value = computeDropIndex(event.clientY)
    proto.activeContainerTarget.value = null
  }
}

function onCanvasDrop(event: DragEvent) {
  event.preventDefault()
  if (proto.activeContainerTarget.value) {
    const { id, colIndex } = proto.activeContainerTarget.value
    proto.dropInsideContainer(id, colIndex)
    return
  }
  proto.dropAtTopIndex(proto.activeDropIndex.value ?? proto.nodes.value.length)
}

function containerOver(id: string, event: DragEvent, colIndex?: number) {
  if (!proto.draggingMaterial.value && !proto.draggingNodeId.value) return
  if (proto.draggingNodeId.value === id) return
  event.preventDefault()
  event.stopPropagation()
  proto.activeContainerTarget.value = { id, colIndex }
}

function containerLeave(id: string, event: DragEvent, colIndex?: number) {
  if (event.relatedTarget instanceof Node && (event.currentTarget as Node).contains(event.relatedTarget)) return
  if (proto.activeContainerTarget.value?.id === id && proto.activeContainerTarget.value.colIndex === colIndex) {
    proto.activeContainerTarget.value = null
  }
}

function containerDrop(id: string, event: DragEvent, colIndex?: number) {
  event.preventDefault()
  event.stopPropagation()
  proto.dropInsideContainer(id, colIndex)
}

const isTargeting = (id: string, colIndex?: number) =>
  proto.activeContainerTarget.value?.id === id && proto.activeContainerTarget.value.colIndex === colIndex

function barFor(node: CanvasNode, index: number, total: number) {
  return { up: index > 0, down: index < total - 1 }
}

function styleOf(node: CanvasNode) {
  return {
    borderRadius: `${node.radiusPx}px`,
    padding: `${node.paddingPx}px`,
    marginBottom: node.marginBottomPx ? `${node.marginBottomPx}px` : undefined,
  }
}

const dragPreview = computed(() => (proto.draggingMaterial.value
  ? { kind: 'material', label: proto.draggingMaterial.value.name }
  : proto.draggingNodeId.value
    ? { kind: 'node', label: proto.nodes.value.find(node => node.id === proto.draggingNodeId.value)?.name ?? '' }
    : null))
</script>

<template>
  <main :class="canvasClass" @scroll="proto.canvasScrolled.value = ($event.target as HTMLElement).scrollTop > 8">
    <div v-if="proto.pageConfig.showDots" class="pf-dots" aria-hidden="true" />

    <div class="pf-canvas-top">
      <div class="pf-device">
        <button type="button" class="pf-device-btn" :aria-expanded="proto.viewportMenuOpen.value" @click.stop="proto.viewportMenuOpen.value = !proto.viewportMenuOpen.value">
          <Icon name="monitor" :size="14" />
          <span>{{ DEVICE_LABEL[proto.deviceMode.value] }}</span>
          <span class="w">{{ proto.viewportWidth.value === 'auto' ? '自适应' : `${proto.viewportWidth.value}px` }}</span>
          <Icon name="chevronDown" :size="12" />
        </button>
        <div v-if="proto.viewportMenuOpen.value" class="pf-device-menu" @click.stop>
          <p class="pf-menu-label">终端设备</p>
          <div class="pf-segmented is-fluid">
            <button
              v-for="option in [{ v: 'desktop', l: '桌面端' }, { v: 'tablet', l: '平板端' }, { v: 'mobile', l: '手机端' }] as const"
              :key="option.v"
              type="button"
              :class="['pf-seg-btn', proto.deviceMode.value === option.v && 'is-on']"
              @click="proto.deviceMode.value = option.v"
            >{{ option.l }}</button>
          </div>
          <p class="pf-menu-label">视口宽度</p>
          <div class="pf-menu-rows">
            <button type="button" :class="['pf-menu-row', proto.viewportWidth.value === 'auto' && 'is-on']" @click="proto.viewportWidth.value = 'auto'; proto.viewportMenuOpen.value = false">自适应</button>
            <button
              v-for="px in WIDTH_PRESETS[proto.deviceMode.value]"
              :key="px"
              type="button"
              :class="['pf-menu-row', proto.viewportWidth.value === px && 'is-on']"
              @click="proto.viewportWidth.value = px; proto.viewportMenuOpen.value = false"
            >{{ px }} px</button>
            <span class="pf-menu-custom">
              <input v-model="proto.customWidth.value" class="pf-input mono" type="number" placeholder="自定义 px">
              <button type="button" class="pf-btn is-sm is-secondary" @click="proto.viewportWidth.value = Number(proto.customWidth.value) || 'auto'; proto.viewportMenuOpen.value = false">应用</button>
            </span>
          </div>
        </div>
      </div>
    </div>

    <div ref="listEl" class="pf-canvas-list" :style="canvasStyle" @dragover="onCanvasDragOver" @drop="onCanvasDrop" @dragleave="proto.canvasDragOver.value = false">
      <template v-for="(node, index) in proto.nodes.value" :key="node.id">
        <div :class="['pf-slot', proto.activeDropIndex.value === index && 'is-active']" />

        <div class="pf-node-wrap" :data-top-index="index" draggable="true" @dragstart="proto.draggingNodeId.value = node.id" @dragend="proto.resetDrag()">
          <section
            :class="['pf-node', proto.selectedNodeId.value === node.id && 'is-selected', !node.visible && 'is-hidden', node.locked && 'is-locked', proto.draggingNodeId.value === node.id && 'is-dragging-source', node.surfaceStyle === 'glass' && 'surface-glass', node.surfaceStyle === 'subtle' && 'surface-subtle']"
            :style="styleOf(node)"
            @click="proto.selectedNodeId.value = node.id"
            @dragover="containerOver(node.id, $event)"
            @dragleave="containerLeave(node.id, $event)"
            @drop="containerDrop(node.id, $event)"
          >
            <div v-if="proto.selectedNodeId.value === node.id && !proto.previewMode.value" class="pf-node-bar">
              <span class="nm"><Icon name="layers" :size="13" />{{ node.name }}</span>
              <span class="sep" />
              <button type="button" class="pf-icon-btn" :disabled="!barFor(node, index, proto.nodes.value.length).up" aria-label="上移组件" title="上移组件" @click.stop="proto.moveNode(node.id, 'up')"><Icon name="arrowUp" :size="13" /></button>
              <button type="button" class="pf-icon-btn" :disabled="!barFor(node, index, proto.nodes.value.length).down" aria-label="下移组件" title="下移组件" @click.stop="proto.moveNode(node.id, 'down')"><Icon name="arrowDown" :size="13" /></button>
              <button type="button" class="pf-icon-btn" aria-label="复制组件" title="复制组件" @click.stop="proto.duplicateNode(node.id)"><Icon name="copy" :size="13" /></button>
              <button type="button" class="pf-icon-btn" :aria-label="node.visible ? '隐藏组件' : '显示组件'" :title="node.visible ? '隐藏组件' : '显示组件'" @click.stop="proto.toggleVisible(node.id)"><Icon :name="node.visible ? 'eye' : 'ban'" :size="13" /></button>
              <button type="button" class="pf-icon-btn" :aria-label="node.locked ? '解锁组件' : '锁定组件'" :title="node.locked ? '解锁组件' : '锁定组件'" @click.stop="proto.toggleLock(node.id)"><Icon :name="node.locked ? 'lock' : 'sidebar'" :size="13" /></button>
              <button type="button" class="pf-icon-btn" aria-label="删除组件" title="删除组件" @click.stop="proto.deleteNode(node.id)"><Icon name="trash" :size="13" /></button>
            </div>

            <div :class="['pf-node-body', !proto.previewMode.value && 'is-static']" :style="{ padding: '0' }">
              <NodeRenderer
                :node="node"
                :records="proto.tableRecords.value"
                :preview="proto.previewMode.value"
                @select="proto.selectedNodeId.value = $event"
                @density="(id, d) => proto.updateNode(id, item => ({ ...item, props: { ...item.props, density: d } }))"
                @toggle-row="proto.toggleRow"
                @toggle-all="proto.toggleAllRows"
              />
            </div>
          </section>

          <div v-if="isTargeting(node.id) && dragPreview" class="pf-ghost" data-ghost="true">
            <span class="badge">{{ dragPreview.label }}</span>
          </div>
        </div>
      </template>

      <div :class="['pf-slot', proto.activeDropIndex.value === proto.nodes.value.length && 'is-active']" />

      <!-- 末尾待放置虚线槽 -->
      <div
        :class="['pf-tail', proto.canvasDragOver.value && 'is-active']"
        @click="proto.leftTab.value = 'materials'"
      >
        {{ proto.canvasDragOver.value ? '松手即插入到此位置' : '拖拽左侧组件物料到此处追加区块，或点击切换至组件物料' }}
      </div>

      <div v-if="proto.activeDropIndex.value === proto.nodes.value.length && dragPreview" class="pf-ghost" data-ghost="true">
        <span class="badge">{{ dragPreview.label }}</span>
      </div>
    </div>
  </main>
</template>

<style scoped>
.pf-device { position: relative; }

.pf-device-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--float-bg);
  box-shadow: var(--e1);
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 11.5px;
}

.pf-device-btn:hover { border-color: var(--line-strong); color: var(--ink); }
.pf-device-btn .w { color: var(--ink-faint); font-family: var(--font-mono); font-variant-numeric: tabular-nums; }

.pf-device-menu {
  position: absolute;
  top: 34px;
  right: 0;
  z-index: 8;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 232px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--float-bg);
  backdrop-filter: blur(20px);
  box-shadow: var(--e3);
}

.pf-menu-label { margin: 0; color: var(--ink-faint); font-size: 11px; letter-spacing: .08em; }
.pf-menu-rows { display: flex; flex-direction: column; gap: 1px; }

.pf-menu-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 28px;
  padding: 0 9px;
  border: 0;
  border-radius: var(--r-xs);
  background: none;
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 12.5px;
  text-align: left;
}

.pf-menu-row:hover { background: var(--hover-bg); }
.pf-menu-row.is-on { background: var(--blue-soft); color: var(--blue); }
.pf-menu-custom { display: flex; align-items: center; gap: 6px; margin-top: 6px; }
.pf-node-wrap { position: relative; }

/* 编辑态：内容不吃指针，点击一律落到「选中这个节点」 */
.pf-node-body.is-static :deep(*) { pointer-events: none; }
.pf-node-body.is-static :deep(.pf-segmented),
.pf-node-body.is-static :deep(.pf-checkbox) { pointer-events: auto; }
</style>
