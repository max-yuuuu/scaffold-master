<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import { usePrototype } from './context.ts'
import TreeRowActions from './ProtoTreeRowActions.vue'
import { CATEGORY_GROUPS, GROUP_TONE, KIND_ICON, MATERIAL_LIBRARY, TONE_VAR, dropZones, type CanvasNode } from './data.ts'

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

/** 折叠态默认全开：`treeExpanded` 里没记的按展开处理，root 也走同一条 */
function treeOpen(id: string) {
  return proto.treeExpanded.value[id] !== false
}

function toggleNode(id: string) {
  proto.treeExpanded.value = { ...proto.treeExpanded.value, [id]: !treeOpen(id) }
}

/** 树里只铺两层（顶层 + 容器子层），与原型 renderTree 的递归深度一致 */
function kidsOf(node: CanvasNode): CanvasNode[] {
  if (node.children) return node.children
  if (node.columns) return node.columns.flat()
  return []
}

/**
 * 树上这一行落在哪一格，由指针在这一行里的相对高度决定（原型同一套阈值）：
 * 上三分之一是插到它前面，中间一段只有容器才吃「放进去」，其余插到它后面。
 * 写死 inside / after 是不行的 —— 把组件塞进一颗表格，或者只能往最后一格里放，都不是人想做的动作。
 */
function treeDropPosition(node: CanvasNode, event: DragEvent): 'before' | 'after' | 'inside' {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = (event.clientY - rect.top) / rect.height
  if (ratio < 0.32) return 'before'
  if (dropZones(node).length && ratio <= 0.68) return 'inside'
  return 'after'
}

function onRowDragOver(node: CanvasNode, event: DragEvent) {
  const dragged = proto.treeDragNodeId.value || proto.draggingNodeId.value
  if (!dragged || dragged === node.id) return
  event.preventDefault()
  event.stopPropagation()
  proto.treeDropIndicator.value = { id: node.id, position: treeDropPosition(node, event) }
}

function onRowDragLeave(node: CanvasNode, event: DragEvent) {
  if (event.relatedTarget instanceof Node && (event.currentTarget as Node).contains(event.relatedTarget)) return
  if (proto.treeDropIndicator.value?.id === node.id) proto.treeDropIndicator.value = null
}

function onRowDrop(node: CanvasNode, event: DragEvent) {
  event.preventDefault()
  event.stopPropagation()
  const dragged = proto.treeDragNodeId.value || proto.draggingNodeId.value
  const indicator = proto.treeDropIndicator.value
  if (dragged && indicator?.id === node.id) proto.treeDrop(dragged, node.id, indicator.position)
  proto.resetDrag()
}

function onRowDragStart(node: CanvasNode, event: DragEvent) {
  // 锁定的组件根本不该被拖起来：不 preventDefault 的话浏览器照样开始一次拖拽，松手才报错
  if (node.locked) {
    event.preventDefault()
    return
  }
  proto.treeDragNodeId.value = node.id
  proto.draggingNodeId.value = node.id
  proto.dragPreview.value = node
  event.stopPropagation()
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
  <aside class="pf-pane is-rail" aria-label="组件物料与页面层级面板">
    <div class="pf-pane-head">
      <Segmented
        v-model="proto.leftTab.value"
        class="pf-fluid"
        label="左栏视图"
        :options="[{ label: '组件物料', value: 'materials' }, { label: '页面层级', value: 'tree' }]"
      />
      <button type="button" class="pf-icon-btn" aria-label="收起左侧组件物料面板" title="收起左侧面板" @click="proto.leftCollapsed.value = true">
        <Icon name="panelLeftClose" :size="14" />
      </button>
    </div>

    <div v-if="proto.leftTab.value === 'materials'" class="pf-rail-search">
      <div class="pf-combo" @click="searchEl?.focus()">
        <Icon name="search" :size="13" />
        <input ref="searchEl" v-model="proto.materialQuery.value" type="text" aria-label="检索组件物料" placeholder="搜索组件物料...">
        <button v-if="proto.materialQuery.value" type="button" class="pf-clear" aria-label="清空搜索" title="清空搜索" @click.stop="proto.materialQuery.value = ''">×</button>
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
              @dragstart="proto.startMaterialDrag(item)"
              @dragend="proto.resetDrag()"
              @click="proto.insertMaterial(item, proto.nodes.value.length)"
            >
              <span class="box"><Icon :name="KIND_ICON[item.kind]" :size="19" /></span>
              <span class="name">{{ item.name }}</span>
            </button>
          </div>
        </section>
      </template>

      <!-- 页面层级：整行是选中面，手柄/折叠箭头在最左，锁定·显隐·删除在最右 -->
      <div v-else class="pf-tree" role="tree" aria-label="页面层级">
        <div
          :class="['pf-tree-row', proto.selectedNodeId.value === null && 'is-active']"
          role="treeitem"
          tabindex="0"
          :aria-selected="proto.selectedNodeId.value === null"
          :aria-expanded="treeOpen('page')"
          @click="selectPage()"
          @keydown.enter.prevent="selectPage()"
          @keydown.space.prevent="selectPage()"
        >
          <button
            type="button"
            :class="['pf-tree-exp', treeOpen('page') && 'is-open']"
            aria-label="展开或收起页面子组件"
            @click.stop="toggleNode('page')"
          ><Icon name="chevronRight" :size="11" /></button>
          <Icon name="layers" :size="14" />
          <span class="nm">{{ proto.pageConfig.name }}</span>
          <span class="mt">页面</span>
        </div>

        <div v-if="treeOpen('page')" class="pf-thread">
          <div v-for="node in proto.nodes.value" :key="node.id" class="pf-tree-branch">
            <div
              :class="[
                'pf-tree-row',
                proto.selectedNodeId.value === node.id && 'is-active',
                treeDropIndicatorIs(node.id, 'inside') && 'is-inside',
              ]"
              role="treeitem"
              tabindex="0"
              :aria-selected="proto.selectedNodeId.value === node.id"
              :aria-expanded="kidsOf(node).length ? treeOpen(node.id) : undefined"
              :draggable="!node.locked"
              @click="proto.selectedNodeId.value = node.id"
              @keydown.enter.prevent="proto.selectedNodeId.value = node.id"
              @keydown.space.prevent="proto.selectedNodeId.value = node.id"
              @dragstart="onRowDragStart(node, $event)"
              @dragover="onRowDragOver(node, $event)"
              @dragleave="onRowDragLeave(node, $event)"
              @drop="onRowDrop(node, $event)"
              @dragend="proto.resetDrag()"
            >
              <span v-if="treeDropIndicatorIs(node.id, 'before')" class="pf-drop-line is-before" aria-hidden="true" />
              <span v-if="treeDropIndicatorIs(node.id, 'after')" class="pf-drop-line is-after" aria-hidden="true" />

              <button
                v-if="kidsOf(node).length"
                type="button"
                :class="['pf-tree-exp', treeOpen(node.id) && 'is-open']"
                aria-label="展开或收起子组件"
                @click.stop="toggleNode(node.id)"
              ><Icon name="chevronRight" :size="11" /></button>
              <span
                v-else
                class="pf-grip"
                :class="{ 'is-off': node.locked }"
                :aria-label="node.locked ? '组件已锁定，不可拖拽' : '拖动调整层级'"
                :title="node.locked ? '已锁定，不可拖拽' : '按住整行拖拽排序'"
                aria-hidden="true"
              ><Icon name="grip" :size="12" /></span>
              <Icon :name="KIND_ICON[node.kind]" :size="14" />
              <span class="nm">{{ node.name }}</span>
              <span class="marks">
                <span v-if="node.locked" class="mt" title="已锁定"><Icon name="lock" :size="12" /></span>
                <span v-if="!node.visible" class="mt" title="已隐藏"><Icon name="eyeOff" :size="12" /></span>
                <span v-if="kidsOf(node).length" class="pf-count">{{ kidsOf(node).length }}</span>
              </span>
              <TreeRowActions :node="node" />
            </div>

            <div v-if="kidsOf(node).length && treeOpen(node.id)" class="pf-thread is-nested">
              <div
                v-for="child in kidsOf(node)"
                :key="child.id"
                :class="[
                  'pf-tree-row',
                  'is-child',
                  proto.selectedNodeId.value === child.id && 'is-active',
                  treeDropIndicatorIs(child.id, 'inside') && 'is-inside',
                ]"
                role="treeitem"
                tabindex="0"
                :aria-selected="proto.selectedNodeId.value === child.id"
                :draggable="!child.locked"
                @click="proto.selectedNodeId.value = child.id"
                @keydown.enter.prevent="proto.selectedNodeId.value = child.id"
                @keydown.space.prevent="proto.selectedNodeId.value = child.id"
                @dragstart="onRowDragStart(child, $event)"
                @dragover="onRowDragOver(child, $event)"
                @dragleave="onRowDragLeave(child, $event)"
                @drop="onRowDrop(child, $event)"
                @dragend="proto.resetDrag()"
              >
                <span v-if="treeDropIndicatorIs(child.id, 'before')" class="pf-drop-line is-before" aria-hidden="true" />
                <span v-if="treeDropIndicatorIs(child.id, 'after')" class="pf-drop-line is-after" aria-hidden="true" />
                <span class="pf-grip" :class="{ 'is-off': child.locked }" aria-hidden="true"><Icon name="grip" :size="12" /></span>
                <Icon :name="KIND_ICON[child.kind]" :size="13" />
                <span class="nm">{{ child.name }}</span>
                <span class="marks">
                  <span v-if="child.locked" class="mt" title="已锁定"><Icon name="lock" :size="12" /></span>
                  <span v-if="!child.visible" class="mt" title="已隐藏"><Icon name="eyeOff" :size="12" /></span>
                </span>
                <TreeRowActions :node="child" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

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

/* 搜索框尾部的清空按钮：只在有关键字时出现，不占位、不伪装成分组开关 */
.pf-clear {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  flex: none;
  border: 0;
  border-radius: 5px;
  background: none;
  color: var(--ink-faint);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.pf-clear:hover { background: var(--hover-bg); color: var(--ink); }

.pf-tree-branch { display: flex; flex-direction: column; }
</style>
