<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import { usePrototype } from './context.ts'
import NodeRenderer from './NodeRenderer.vue'
import { KIND_ICON, lockIcon, visibleIcon, type CanvasNode } from './data.ts'

/* 画布：点阵底 + 悬浮胶囊 + 逐节点卡 + 按 clientY 实时算落点 + 1:1 幽灵预览。
   胶囊浮在舞台上是绝对定位、不跟着滚；滚的是里面那一层。
   编辑态下节点内容整体 pointer-events: none —— 点画布任何东西都是选中这个节点，
   而不是在一份静态稿里填一个填不出结果的表单。 */
const proto = usePrototype()

const listEl = ref<HTMLElement | null>(null)

const canvasClass = computed(() => ['pf-canvas', `tone-${proto.pageConfig.canvasBgTone}`])

/** 页面级内边距真的作用在滚动层上，改一格画布就跟着挪 */
const scrollStyle = computed(() => ({
  paddingLeft: `${proto.pageConfig.paddingX}px`,
  paddingRight: `${proto.pageConfig.paddingX}px`,
  paddingTop: `${proto.pageConfig.paddingY}px`,
}))

const listStyle = computed(() => ({
  maxWidth: proto.viewportWidth.value === 'auto' ? '100%' : `${proto.viewportWidth.value}px`,
  gap: `${proto.pageConfig.blockGap}px`,
}))

const listClass = computed(() => ({
  'is-mobile': proto.deviceMode.value === 'mobile',
  'is-tablet': proto.deviceMode.value === 'tablet',
}))

const DEVICE_ICON = { desktop: 'monitor', tablet: 'tablet', mobile: 'phone' } as const
const DEVICE_LABEL = { desktop: '桌面端', tablet: '平板端', mobile: '移动端' } as const

const WIDTH_PRESETS: Record<string, number[]> = {
  desktop: [1440, 1280, 1024],
  tablet: [834, 768],
  mobile: [414, 375],
}

/** 视口宽度只收 320–2560：越界就夹回来并写回输入框，不让画布被一个手滑的数字撑爆 */
function applyCustomWidth() {
  const width = Math.min(2560, Math.max(320, Number(proto.customWidth.value) || 1280))
  proto.viewportWidth.value = width
  proto.customWidth.value = String(width)
  proto.viewportMenuOpen.value = false
}

function pickWidth(width: 'auto' | number) {
  proto.viewportWidth.value = width
  if (typeof width === 'number') proto.customWidth.value = String(width)
  proto.viewportMenuOpen.value = false
}

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
  const target = proto.activeContainerTarget.value
  if (target) {
    proto.dropInsideContainer(target.id, target.colIndex)
    return
  }
  // 没算出落点索引就是没落到位置上，dropAtTopIndex 自己会什么都不做
  proto.dropAtTopIndex()
}

/** 可放置区域只有栅格列与插槽腔体，它们从 NodeRenderer 里冒出来 */
function zoneOver(id: string, colIndex: number | undefined, event: DragEvent) {
  if (!proto.draggingMaterial.value && !proto.draggingNodeId.value) return
  if (proto.draggingNodeId.value === id) return
  event.preventDefault()
  event.stopPropagation()
  proto.activeContainerTarget.value = { id, colIndex }
  proto.activeDropIndex.value = null
}

function zoneLeave(id: string, colIndex: number | undefined, event: DragEvent) {
  if (event.relatedTarget instanceof Node && (event.currentTarget as Node).contains(event.relatedTarget)) return
  const target = proto.activeContainerTarget.value
  if (target?.id === id && target.colIndex === colIndex) proto.activeContainerTarget.value = null
}

function zoneDrop(id: string, colIndex: number | undefined, event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  proto.dropInsideContainer(id, colIndex)
}

const targetsNode = (id: string) => proto.activeContainerTarget.value?.id === id

function styleOf(node: CanvasNode) {
  return {
    borderRadius: `${node.radiusPx}px`,
    padding: `${node.paddingPx}px`,
    marginBottom: node.marginBottomPx ? `${node.marginBottomPx}px` : undefined,
  }
}

/** 预览帧由状态层在拖拽起点建好，这里只读；每帧重建会让预览一直抖 */
const previewNode = computed(() => proto.dragPreview.value)

const ghostAt = (index: number) =>
  !proto.previewMode.value &&
  !proto.activeContainerTarget.value &&
  previewNode.value !== null &&
  proto.activeDropIndex.value === index
</script>

<template>
  <main :class="canvasClass">
    <div v-if="proto.pageConfig.showDots" class="pf-dots" aria-hidden="true" />

    <button
      v-if="proto.leftCollapsed.value && !proto.previewMode.value"
      type="button"
      class="pf-capsule m-pill is-left"
      title="展开左侧组件物料与页面层级面板"
      @click.stop="proto.openPane('left')"
    >
      <Icon name="panelLeftOpen" :size="14" class="is-blue" />
      <span>展开物料</span>
    </button>

    <div class="pf-capsules">
      <div class="pf-device">
        <button type="button" class="pf-capsule m-pill" :aria-expanded="proto.viewportMenuOpen.value" @click.stop="proto.viewportMenuOpen.value = !proto.viewportMenuOpen.value">
          <Icon :name="DEVICE_ICON[proto.deviceMode.value]" :size="14" class="is-blue" />
          <span>{{ DEVICE_LABEL[proto.deviceMode.value] }}</span>
          <span class="w">{{ proto.viewportWidth.value === 'auto' ? '自适应' : `${proto.viewportWidth.value}px` }}</span>
          <Icon name="chevronDown" :size="12" :class="proto.viewportMenuOpen.value && 'is-flip'" />
        </button>
        <div v-if="proto.viewportMenuOpen.value" class="pf-device-menu" @click.stop>
          <div class="pf-menu-label">终端设备</div>
          <Segmented
            v-model="proto.deviceMode.value"
            class="pf-fluid"
            label="终端设备"
            :options="[{ label: '桌面', value: 'desktop' }, { label: '平板', value: 'tablet' }, { label: '移动', value: 'mobile' }]"
          />
          <p class="pf-menu-label">视口宽度</p>
          <div class="pf-menu-rows">
            <button type="button" :class="['pf-menu-row', proto.viewportWidth.value === 'auto' && 'is-on']" @click="pickWidth('auto')">自适应</button>
            <button
              v-for="px in WIDTH_PRESETS[proto.deviceMode.value]"
              :key="px"
              type="button"
              :class="['pf-menu-row', proto.viewportWidth.value === px && 'is-on']"
              @click="pickWidth(px)"
            >{{ px }} px</button>
            <span class="pf-menu-custom">
              <span class="cap">自定义宽度</span>
              <span class="field">
                <input
                  v-model="proto.customWidth.value"
                  class="pf-input is-mono"
                  type="number"
                  min="320"
                  max="2560"
                  aria-label="自定义画布像素宽度"
                  @keydown.enter="applyCustomWidth"
                >
                <sup>px</sup>
              </span>
              <button type="button" class="pf-btn is-sm is-secondary" @click="applyCustomWidth">应用</button>
            </span>
          </div>
        </div>
      </div>

      <button
        v-if="proto.rightCollapsed.value && !proto.previewMode.value"
        type="button"
        class="pf-capsule m-pill"
        title="展开右侧属性配置面板"
        @click.stop="proto.openPane('right')"
      >
        <span>展开配置</span>
        <Icon name="panelRightOpen" :size="14" class="is-blue" />
      </button>
    </div>

    <div
      class="pf-canvas-scroll"
      :style="scrollStyle"
      @scroll="proto.canvasScrolled.value = ($event.target as HTMLElement).scrollTop > 8"
      @click="proto.selectedNodeId.value = null; proto.viewportMenuOpen.value = false"
    >
      <div ref="listEl" :class="['pf-canvas-list', listClass]" :style="listStyle" @dragover="onCanvasDragOver" @drop="onCanvasDrop" @dragleave="proto.canvasDragOver.value = false" @click.stop>
        <template v-for="(node, index) in proto.nodes.value" :key="node.id">
          <!-- 落点：拖拽中就地画出被拖组件本体，静止时是一条 6px 待插槽 -->
          <div v-if="ghostAt(index)" class="pf-ghost" data-ghost="true">
            <span class="pf-ghost-badge"><Icon :name="KIND_ICON[previewNode!.kind]" :size="12" />预览落位 · {{ previewNode!.name }}（松手即放置）</span>
            <section class="pf-node" :style="styleOf(previewNode!)">
              <div class="pf-node-body is-static">
                <NodeRenderer :node="previewNode!" :records="proto.tableRecords.value" :preview="true" />
              </div>
            </section>
          </div>
          <div v-else :class="['pf-slot', proto.activeDropIndex.value === index && 'is-active']" />

          <div class="pf-node-wrap" :data-top-index="index" draggable="true" @dragstart="proto.startNodeDrag(node.id)" @dragend="proto.resetDrag()">
            <section
              :class="['pf-node', proto.selectedNodeId.value === node.id && 'is-selected', !node.visible && 'is-hidden', node.locked && 'is-locked', proto.draggingNodeId.value === node.id && 'is-dragging-source', node.surfaceStyle === 'glass' && 'surface-glass', node.surfaceStyle === 'subtle' && 'surface-subtle']"
              :style="styleOf(node)"
              @click="proto.selectedNodeId.value = node.id"
            >
              <!-- 选中态工具条：一颗组件的所有画布动作都收在这条 28px 胶囊里 -->
              <div v-if="proto.selectedNodeId.value === node.id && !proto.previewMode.value" class="pf-node-bar" @click.stop>
                <span class="nm"><Icon :name="KIND_ICON[node.kind]" :size="12" />{{ node.name }}</span>
                <span class="sep" />
                <button type="button" class="act" :disabled="index === 0 || node.locked" aria-label="上移组件" title="上移组件" @click.stop="proto.moveNode(node.id, 'up')"><Icon name="arrowUp" :size="12" /></button>
                <button type="button" class="act" :disabled="index === proto.nodes.value.length - 1 || node.locked" aria-label="下移组件" title="下移组件" @click.stop="proto.moveNode(node.id, 'down')"><Icon name="arrowDown" :size="12" /></button>
                <button type="button" class="act" aria-label="复制组件" title="复制组件" @click.stop="proto.duplicateNode(node.id)"><Icon name="copy" :size="12" /></button>
                <button
                  type="button"
                  :class="['act', !node.visible && 'is-on']"
                  :aria-label="node.visible ? '隐藏组件' : '显示组件'"
                  :title="node.visible ? '隐藏组件' : '组件已隐藏（点击显示）'"
                  @click.stop="proto.toggleVisible(node.id)"
                ><Icon :name="visibleIcon(node.visible)" :size="12" /></button>
                <button
                  type="button"
                  :class="['act', node.locked && 'is-on']"
                  :aria-label="node.locked ? '解锁组件' : '锁定组件'"
                  :title="node.locked ? '组件已锁定（点击解锁）' : '锁定组件'"
                  @click.stop="proto.toggleLock(node.id)"
                ><Icon :name="lockIcon(node.locked)" :size="12" /></button>
                <button
                  type="button"
                  class="act is-danger"
                  aria-label="删除组件"
                  :title="node.locked ? '组件已锁定' : '删除组件'"
                  :disabled="node.locked"
                  @click.stop="proto.deleteNode(node.id)"
                ><Icon name="trash" :size="12" /></button>
              </div>

              <div :class="['pf-node-body', !proto.previewMode.value && 'is-static']">
                <NodeRenderer
                  :node="node"
                  :records="proto.tableRecords.value"
                  :preview="proto.previewMode.value"
                  :drop-target="proto.activeContainerTarget.value"
                  @select="proto.selectedNodeId.value = $event"
                  @density="(id, d) => proto.updateNode(id, item => ({ ...item, props: { ...item.props, density: d } }))"
                  @toggle-row="proto.toggleRow"
                  @toggle-all="proto.toggleAllRows"
                  @zone-over="zoneOver"
                  @zone-leave="zoneLeave"
                  @zone-drop="zoneDrop"
                />
              </div>
            </section>

            <div v-if="targetsNode(node.id) && previewNode" class="pf-ghost" data-ghost="true">
              <span class="pf-ghost-badge"><Icon :name="KIND_ICON[previewNode.kind]" :size="12" />预览落位 · {{ previewNode.name }}（松手即放置）</span>
              <section class="pf-node" :style="styleOf(previewNode)">
                <div class="pf-node-body is-static">
                  <NodeRenderer :node="previewNode" :records="proto.tableRecords.value" :preview="true" />
                </div>
              </section>
            </div>
          </div>
        </template>

        <div v-if="ghostAt(proto.nodes.value.length)" class="pf-ghost" data-ghost="true">
          <span class="pf-ghost-badge"><Icon :name="KIND_ICON[previewNode!.kind]" :size="12" />预览落位 · {{ previewNode!.name }}（松手即放置）</span>
          <section class="pf-node" :style="styleOf(previewNode!)">
            <div class="pf-node-body is-static">
              <NodeRenderer :node="previewNode!" :records="proto.tableRecords.value" :preview="true" />
            </div>
          </section>
        </div>

        <!-- 末尾待放置虚线槽 -->
        <div
          v-if="!proto.previewMode.value"
          :class="['pf-tail', proto.activeDropIndex.value === proto.nodes.value.length && proto.canvasDragOver.value && 'is-active']"
          @click="proto.leftTab.value = 'materials'; proto.pushToast('可从左侧组件物料拖拽或点击卡片插入组件', 'info')"
        >
          <span class="lead"><span class="plus m-pill"><Icon name="plus" :size="14" /></span><span>拖拽左侧组件物料到此处追加区块，或点击切换至组件物料</span></span>
          <span class="code-chip">drop-zone:tail</span>
        </div>
      </div>
    </div>
  </main>
</template>

<style scoped>
.pf-capsules { position: absolute; top: 10px; right: 12px; z-index: 6; display: flex; align-items: center; gap: 8px; }
.pf-device { position: relative; }
.is-blue { color: var(--blue); }
.is-flip { transform: rotate(180deg); }
.pf-capsule .w { color: var(--ink-soft); font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 11px; }

.pf-device-menu {
  position: absolute;
  top: 36px;
  right: 0;
  z-index: 8;
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 248px;
  padding: 10px;
  border: 1px solid var(--glass-line);
  border-radius: 16px;
  background: var(--float-bg);
  backdrop-filter: blur(28px) saturate(1.08);
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
.pf-menu-custom { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line); }
.pf-menu-custom .cap { flex: none; color: var(--ink-soft); font-size: 11.5px; }
.pf-menu-custom .field { position: relative; display: inline-flex; align-items: center; flex: none; }
.pf-menu-custom .field input { width: 92px; height: var(--control-h-sm); padding: 0 24px 0 8px; font-size: 12px; }
.pf-menu-custom .field sup { position: absolute; right: 8px; top: 50%; transform: translateY(-50%); color: var(--ink-faint); font-family: var(--font-mono); font-size: 10px; pointer-events: none; }
.pf-menu-custom .pf-btn { flex: none; padding: 0 10px; white-space: nowrap; }
.pf-node-wrap { position: relative; }

.pf-node-bar .nm { display: inline-flex; align-items: center; gap: 6px; padding: 0 8px; font-size: 11.5px; font-weight: 620; white-space: nowrap; }
.pf-node-bar .sep { width: 1px; height: 12px; margin: 0 2px; background: rgba(255, 255, 255, .25); }

.pf-node-bar .act {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 999px;
  background: none;
  color: #fff;
  cursor: pointer;
  opacity: .92;
  transition: background-color .16s var(--ease-premium), opacity .16s var(--ease-premium);
}

.pf-node-bar .act:hover:not(:disabled) { background: rgba(255, 255, 255, .2); opacity: 1; }
.pf-node-bar .act.is-on { background: rgba(255, 255, 255, .3); opacity: 1; }
.pf-node-bar .act.is-danger:hover:not(:disabled) { background: #ff5a50; }
.pf-node-bar .act:disabled { opacity: .35; cursor: not-allowed; }

/* 编辑态：内容不吃指针，点击一律落到「选中这个节点」 */
.pf-node-body.is-static :deep(*) { pointer-events: none; }
.pf-node-body.is-static :deep(.segmented),
.pf-node-body.is-static :deep(.pf-checkbox) { pointer-events: auto; }
</style>
