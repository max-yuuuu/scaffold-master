<script setup lang="ts">
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { useSession } from './session.ts'
import { BLOCK_LABELS, iconOf, type UINode } from './design.ts'

/* 页面层级：整棵树从上往下读，选中与画布共用同一个 key。
   拖拽手柄只在悬浮时显形（常驻会让一屏全是灰点，反而看不出哪条能动），
   但键盘不靠悬浮 —— 每个手柄本身就是一个可聚焦的按钮，Tab 到位后能直接拖。 */
const session = useSession()
const blocks = session.blocks
const selectedKey = session.selectedKey

function label(node: UINode) {
  if (node.type === 'PageContainer') return session.page.pageName || '未命名页面'
  const named = String(node.props.label ?? '')
  return named || (BLOCK_LABELS[node.type] ?? node.type)
}

function meta(block: UINode) {
  if (block.type === 'DataTable') return `${block.columns.length} 列`
  if (block.type === 'PageContainer') return '页面'
  return `${block.children.length} 项`
}
</script>

<template>
  <div class="dz-tree">
    <button
      type="button"
      :class="['dz-tree-line', 'is-root', selectedKey === null && 'is-active']"
      @click="session.pick(null)"
    >
      <Icon name="layers" :size="14" />
      <span class="dz-tree-name">{{ session.page.pageName || '未命名页面' }}</span>
      <span class="dz-tree-meta">页面</span>
    </button>

    <div class="dz-tree-thread">
      <div v-for="block in blocks" :key="block.key" class="dz-tree-branch">
        <div class="dz-tree-row">
          <span
            class="dz-grip"
            role="button"
            tabindex="0"
            :aria-label="block.locked ? '区块已锁定，不可拖拽' : '拖动调整区块顺序'"
            :title="block.locked ? '已锁定，不可拖拽' : '拖动调整区块顺序'"
            :class="{ 'is-off': block.locked }"
            :draggable="!block.locked"
            @dragstart="session.drag.value = { kind: 'move', type: block.type, key: block.key }"
            @dragend="session.drag.value = null"
          ><Icon name="more" :size="13" /></span>
          <button
            type="button"
            :class="['dz-tree-line', selectedKey === block.key && 'is-active']"
            @click="session.pick(block.key)"
          >
            <Icon :name="iconOf(block.type)" :size="14" />
            <span class="dz-tree-name">{{ BLOCK_LABELS[block.type] ?? block.type }}</span>
            <span v-if="block.locked" class="dz-tree-flag" title="已锁定"><Icon name="lock" :size="11" /></span>
            <span v-if="block.visible === false" class="dz-tree-flag" title="在画布上已藏起（不影响生成的页面）"><Icon name="eyeOff" :size="11" /></span>
            <span class="dz-tree-meta">{{ meta(block) }}</span>
          </button>
          <button
            type="button"
            class="dz-tree-x"
            aria-label="删掉这一整块"
            :title="block.locked ? '已锁定，先解锁再删' : '删掉这一整块'"
            :disabled="block.locked"
            @click.stop="session.act(() => session.store.remove(block.key))"
          >×</button>
        </div>

        <div v-if="block.children.length" class="dz-tree-thread is-nested">
          <div v-for="child in block.children" :key="child.key" class="dz-tree-row">
            <span class="dz-grip-placeholder" aria-hidden="true" />
            <button
              type="button"
              :class="['dz-tree-line', 'is-child', selectedKey === child.key && 'is-active']"
              @click="session.pick(child.key)"
            >
              <Icon :name="iconOf(child.type)" :size="13" />
              <span class="dz-tree-name">{{ label(child) }}</span>
              <span v-if="child.props.field" class="dz-tree-code">{{ child.props.field }}</span>
            </button>
            <button
              type="button"
              class="dz-tree-x"
              aria-label="复制这一个控件"
              title="复制控件：副本紧跟在后面，属性照抄。区块不能复制——一个页面每类区块只有一个"
              @click.stop="session.act(() => session.store.duplicate(child.key))"
            >⧉</button>
            <button type="button" class="dz-tree-x" aria-label="移出去" title="移出去" @click.stop="session.act(() => session.store.remove(child.key))">×</button>
          </div>
        </div>
      </div>
    </div>

    <p v-if="!blocks.length" class="dz-tree-empty">画布还是空的：切回「组件物料」把区块拖进来。</p>
  </div>
</template>

<style scoped>
.dz-tree {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-height: 0;
  flex: 1;
  overflow-y: auto;
}

.dz-tree-line {
  display: flex;
  align-items: center;
  gap: 7px;
  flex: 1;
  min-width: 0;
  height: 28px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: var(--r-xs);
  background: none;
  color: var(--ink);
  cursor: pointer;
  font-size: 12.5px;
  text-align: left;
}

.dz-tree-line:hover {
  background: var(--hover-bg);
}

.dz-tree-line.is-active {
  border-color: var(--blue);
  background: var(--blue-soft);
  color: var(--blue);
}

.dz-tree-line.is-root {
  font-weight: 620;
}

.dz-tree-line.is-child {
  height: 26px;
  font-size: 12px;
  color: var(--ink-muted);
}

.dz-tree-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dz-tree-meta {
  color: var(--ink-faint);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  flex: none;
}

.dz-tree-code {
  flex: none;
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 6px;
  border-radius: 6px;
  background: rgba(46, 58, 62, .06);
  color: var(--ink-muted);
  font-family: var(--font-mono);
  font-size: 10.5px;
  line-height: 17px;
}

:root[data-theme="dark"] .dz-tree-code {
  background: rgba(255, 255, 255, .08);
}

.dz-tree-row {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* 层级丝线：只在有子层时画，视觉上说明「这几条属于上面那一块」 */
.dz-tree-thread {
  margin-left: 14px;
  padding-left: 8px;
  border-left: 1px solid var(--thread);
}

.dz-tree-thread.is-nested {
  margin-left: 20px;
}

.dz-grip,
.dz-grip-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 24px;
  flex: none;
  border-radius: 5px;
  color: var(--ink-faint);
  cursor: grab;
  opacity: 0;
}

.dz-grip:focus-visible {
  opacity: 1;
  outline: 2px solid var(--focus);
}

.dz-tree-row:hover .dz-grip {
  opacity: 1;
}

/* 锁定的区块不该亮出一个拖不动的手柄 */
.dz-grip.is-off,
.dz-tree-row:hover .dz-grip.is-off {
  opacity: 0;
  cursor: not-allowed;
}

.dz-tree-flag {
  display: inline-flex;
  align-items: center;
  flex: none;
  color: var(--ink-faint);
}

.dz-tree-flag:first-of-type {
  color: var(--blue);
}

.dz-grip:active {
  cursor: grabbing;
}

.dz-tree-x {
  width: 18px;
  height: 24px;
  flex: none;
  border: 0;
  border-radius: 5px;
  background: none;
  color: var(--ink-faint);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  opacity: 0;
}

.dz-tree-row:hover .dz-tree-x,
.dz-tree-x:focus-visible {
  opacity: 1;
}

.dz-tree-x:hover {
  color: var(--red);
}

.dz-tree-empty {
  margin: 18px 0 0;
  color: var(--ink-faint);
  font-size: 12px;
  text-align: center;
}
</style>
