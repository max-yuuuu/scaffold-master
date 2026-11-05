<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { usePrototype } from './context.ts'
import { CATEGORY_GROUPS, GROUP_TONE, MATERIAL_LIBRARY, TONE_VAR, type CanvasNode } from './data.ts'

/* 左栏：组件物料 ↔ 页面层级。物料卡 66px 见方、上层 37px 微缩展台；
   树的手柄与删除只在悬浮或键盘聚焦时显形，手柄本身可聚焦，键盘到得了。 */
const proto = usePrototype()
const searchEl = ref<HTMLInputElement | null>(null)

const groups = computed(() => CATEGORY_GROUPS.map(category => ({
  category,
  tone: GROUP_TONE[category],
  items: MATERIAL_LIBRARY.filter(item => item.category === category),
})))

const filtered = computed(() => {
  const key = proto.materialQuery.value.trim().toLowerCase()
  if (!key) return groups.value
  return groups.value
    .map(group => ({
      ...group,
      items: group.items.filter(item =>
        item.name.toLowerCase().includes(key) || item.category.includes(key) || item.code.toLowerCase().includes(key)),
    }))
    .filter(group => group.items.length)
})

const allCollapsed = computed(() => groups.value.every(group => proto.groupCollapsed.value[group.category]))

function toggleAll() {
  const next = !allCollapsed.value
  proto.groupCollapsed.value = Object.fromEntries(CATEGORY_GROUPS.map(category => [category, next]))
}

function startDragMaterial(item: (typeof MATERIAL_LIBRARY)[number]) {
  proto.draggingMaterial.value = item
}

/** 树里只铺两层（顶层 + 容器子层），与原型 renderTree 的递归深度一致 */
function kidsOf(node: CanvasNode): CanvasNode[] {
  if (node.children) return node.children
  if (node.columns) return node.columns.flat()
  return []
}

function onTreeDrop(targetId: string, position: 'before' | 'after' | 'inside', event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  if (proto.treeDragNodeId.value) proto.treeDrop(proto.treeDragNodeId.value, targetId, position)
  proto.treeDragNodeId.value = null
  proto.treeDropIndicator.value = null
}

function markDrop(targetId: string, position: 'before' | 'after' | 'inside', event: DragEvent) {
  if (!proto.treeDragNodeId.value || proto.treeDragNodeId.value === targetId) return
  event.preventDefault()
  proto.treeDropIndicator.value = { id: targetId, position }
}

function treeDropIndicatorIs(id: string, position: 'before' | 'after' | 'inside') {
  return proto.treeDropIndicator.value?.id === id && proto.treeDropIndicator.value.position === position
}

/** 点根节点就是切到「页面级」配置：右栏的三段 Tab 语义跟着换成页面的那三格 */
function selectPage() {
  proto.selectedNodeId.value = null
  proto.inspectorTab.value = 'props'
}
</script>

<template>
  <aside class="pf-pane" aria-label="组件物料与页面层级面板">
    <div class="pf-pane-head">
      <div class="pf-segmented is-fluid">
        <button type="button" :class="['pf-seg-btn', proto.leftTab.value === 'materials' && 'is-on']" @click="proto.leftTab.value = 'materials'">组件物料</button>
        <button type="button" :class="['pf-seg-btn', proto.leftTab.value === 'tree' && 'is-on']" @click="proto.leftTab.value = 'tree'">页面层级</button>
      </div>
      <button type="button" class="pf-icon-btn" aria-label="收起左侧组件物料面板" title="收起左侧面板" @click="proto.leftCollapsed.value = true">
        <Icon name="sidebar" :size="14" />
      </button>
    </div>

    <div v-if="proto.leftTab.value === 'materials'" class="pf-rail-search">
      <div class="pf-combo" @click="searchEl?.focus()">
        <Icon name="search" :size="14" />
        <input ref="searchEl" v-model="proto.materialQuery.value" type="text" aria-label="检索组件物料" placeholder="搜索组件物料...">
        <Icon name="chevronDown" :size="12" />
      </div>
    </div>

    <div class="pf-pane-body">
      <template v-if="proto.leftTab.value === 'materials'">
        <div v-if="!filtered.length" class="pf-rail-empty">没有匹配的组件物料。</div>
        <section v-for="group in filtered" :key="group.category" class="pf-group">
          <button type="button" class="pf-group-head" :aria-expanded="!proto.groupCollapsed.value[group.category]" @click="proto.groupCollapsed.value[group.category] = !proto.groupCollapsed.value[group.category]">
            <span class="title">
              <i class="dot" :style="{ background: TONE_VAR[group.tone].fg }" />
              {{ group.category }}
            </span>
            <span class="meta">
              <span class="pf-count">{{ group.items.length }}</span>
              <Icon :name="proto.groupCollapsed.value[group.category] ? 'chevronRight' : 'chevronDown'" :size="13" />
            </span>
          </button>

          <div v-show="!proto.groupCollapsed.value[group.category]" class="pf-tiles">
            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              :class="['pf-tile', proto.draggingMaterial.value?.id === item.id && 'is-armed']"
              :title="`${item.name} · ${item.desc}`"
              :aria-label="item.name"
              draggable="true"
              @dragstart="startDragMaterial(item)"
              @dragend="proto.resetDrag()"
              @click="proto.insertMaterial(item, proto.nodes.value.length)"
            >
              <span class="box"><Icon :name="item.kind === 'table' ? 'table' : item.kind === 'readout' ? 'chart' : item.kind === 'grid' ? 'grid' : item.kind === 'tabs' ? 'layers' : 'file'" :size="19" /></span>
              <span class="name">{{ item.name }}</span>
            </button>
          </div>
        </section>
      </template>

      <!-- 页面层级 -->
      <div v-else class="pf-tree">
        <div class="pf-tree-row">
          <button type="button" class="pf-tree-line" :class="{ 'is-active': proto.selectedNodeId.value === null }" @click="selectPage()">
            <Icon name="chevronDown" :size="12" />
            <Icon name="layers" :size="14" />
            <span class="nm">{{ proto.pageConfig.name }}</span>
            <span class="mt">页面</span>
          </button>
        </div>

        <div class="pf-thread">
          <div v-for="(node, index) in proto.nodes.value" :key="node.id" class="pf-tree-branch">
            <div class="pf-tree-row">
              <span
                class="pf-grip"
                role="button"
                tabindex="0"
                aria-label="拖动调整层级"
                draggable="true"
                @dragstart="proto.treeDragNodeId.value = node.id"
                @dragend="proto.treeDragNodeId.value = null; proto.treeDropIndicator.value = null"
              ><Icon name="more" :size="13" /></span>
              <button
                type="button"
                :class="['pf-tree-line', proto.selectedNodeId.value === node.id && 'is-active']"
                @dragover="markDrop(node.id, 'inside', $event)"
                @drop="onTreeDrop(node.id, 'inside', $event)"
                @click="proto.selectedNodeId.value = node.id"
              >
                <Icon name="file" :size="14" />
                <span class="nm">{{ node.name }}</span>
                <span v-if="node.locked" class="mt"><Icon name="lock" :size="11" /></span>
                <span v-if="!node.visible" class="mt"><Icon name="ban" :size="11" /></span>
              </button>
              <span v-if="node.locked" class="pf-row-x" />
              <button v-else type="button" class="pf-row-x" aria-label="删除组件" title="删除组件" @click.stop="proto.deleteNode(node.id)">×</button>
            </div>

            <div v-if="treeDropIndicatorIs(node.id, 'before')" class="pf-drop-line is-active" />
            <div v-if="kidsOf(node).length" class="pf-thread is-nested">
              <div v-for="(child, ci) in kidsOf(node)" :key="child.id" class="pf-tree-row">
                <span class="pf-grip-slot" />
                <button
                  type="button"
                  :class="['pf-tree-line', 'is-child', proto.selectedNodeId.value === child.id && 'is-active']"
                  @dragover="markDrop(child.id, 'after', $event)"
                  @drop="onTreeDrop(child.id, 'after', $event)"
                  @click="proto.selectedNodeId.value = child.id"
                >
                  <Icon name="file" :size="13" />
                  <span class="nm">{{ child.name }}</span>
                  <span class="mt">{{ child.code }}</span>
                </button>
                <button type="button" class="pf-row-x" aria-label="删除组件" title="删除组件" @click.stop="proto.deleteNode(child.id)">×</button>
                <div v-if="treeDropIndicatorIs(child.id, 'after') && ci === kidsOf(node).length - 1" class="pf-drop-line is-active" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <footer v-if="proto.leftTab.value === 'materials'" class="pf-pane-foot">
      <span class="pf-foot-text">画布节点 <b class="mono">{{ proto.nodes.value.length }}</b> 个</span>
      <button type="button" class="pf-mini" @click="toggleAll"><Icon name="plus" :size="12" />{{ allCollapsed ? '全部展开' : '新建扩展槽' }}</button>
    </footer>
    <footer v-else class="pf-pane-foot">
      <span class="pf-foot-text">共 <b class="mono">{{ proto.nodes.value.length }}</b> 个根节点</span>
      <button type="button" class="pf-mini" @click="proto.leftTab.value = 'materials'">回到物料</button>
    </footer>
  </aside>
</template>

<style scoped>
.pf-rail-search { padding: 10px 12px 0; }

.pf-combo {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--input-bg);
  box-shadow: var(--edge);
  color: var(--ink-faint);
  cursor: text;
}

.pf-combo:focus-within { border-color: var(--blue); outline: 2px solid var(--focus); outline-offset: 1px; }
.pf-combo input { flex: 1; min-width: 0; border: 0; background: none; color: var(--ink); font: inherit; font-size: 12.5px; }
.pf-combo input::placeholder { color: var(--ink-faint); }

.pf-rail-empty { padding: 22px 4px; color: var(--ink-faint); font-size: 12px; text-align: center; }
.pf-foot-text { color: var(--ink-faint); font-size: 11.5px; }
.pf-foot-text b { color: var(--ink); font-weight: 700; }

.pf-mini {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  background: var(--float-bg);
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 11.5px;
}

.pf-mini:hover { border-color: var(--line-strong); color: var(--ink); }
.pf-tree-branch { display: flex; flex-direction: column; }
</style>
