<script setup lang="ts">
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { usePrototype } from './context.ts'
import { lockIcon, visibleIcon, type CanvasNode } from './data.ts'

/**
 * 层级树行尾的悬浮操作栏：锁定、显隐、删除三颗，只在鼠标停到这一行时显形。
 *
 * 图标画的是「当前状态」而不是「点下去会变成什么」——锁着就画闭合的锁，
 * 解锁后画开锁；隐藏后画斜线眼。否则同一行永远显示同一颗图标，
 * 用户看不出自己刚才那一下到底按没按上。
 */
const proto = usePrototype()
const props = defineProps<{ node: CanvasNode }>()
</script>

<template>
  <span class="pf-row-acts">
    <button
      type="button"
      :class="['pf-row-btn', node.locked && 'is-on']"
      :aria-label="node.locked ? '解锁节点' : '锁定节点'"
      :title="node.locked ? '已锁定（点击解锁）' : '锁定节点'"
      @click.stop="proto.toggleLock(props.node.id)"
    ><Icon :name="lockIcon(node.locked)" :size="12" /></button>
    <button
      type="button"
      :class="['pf-row-btn', !node.visible && 'is-on']"
      :aria-label="node.visible ? '隐藏节点' : '显示节点'"
      :title="node.visible ? '隐藏节点' : '已隐藏（点击显示）'"
      @click.stop="proto.toggleVisible(props.node.id)"
    ><Icon :name="visibleIcon(node.visible)" :size="12" /></button>
    <button
      type="button"
      class="pf-row-btn is-danger"
      aria-label="删除节点"
      :title="node.locked ? '节点已锁定，无法删除' : '删除节点'"
      :disabled="node.locked"
      @click.stop="proto.deleteNode(props.node.id)"
    ><Icon name="trash" :size="12" /></button>
  </span>
</template>

<style scoped>
/* 显隐规则住在 proto.css：这一行的:hover 属于父组件 .pf-tree-row，scoped 选择器够不到它 */
.pf-row-btn {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: none;
  color: var(--ink-soft);
  cursor: pointer;
  transition: background-color .16s var(--ease-premium), color .16s var(--ease-premium);
}

.pf-row-btn:hover:not(:disabled) { background: var(--hover-bg); color: var(--ink); }
.pf-row-btn.is-on { color: var(--blue); background: var(--blue-soft); }
.pf-row-btn.is-danger:hover:not(:disabled) { color: var(--red); background: var(--red-soft); }
.pf-row-btn:disabled { opacity: .35; cursor: not-allowed; }
</style>
