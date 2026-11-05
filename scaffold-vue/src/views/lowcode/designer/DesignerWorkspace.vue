<script setup lang="ts">
import { useSession } from './session.ts'
import MaterialPanel from './MaterialPanel.vue'
import DesignCanvas from './DesignCanvas.vue'
import InspectorPanel from './InspectorPanel.vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'

/* 三栏工作台：左物料与层级、中画布、右配置。
   收起的那一栏整列归零、面板本身从 DOM 摘掉（宽度归零但留在 Tab 序列里，键盘要连按好几次才跳过一栏看不见的东西）。 */
const session = useSession()
const collapsedLeft = () => session.leftCollapsed.value || session.previewMode.value
const collapsedRight = () => session.rightCollapsed.value || session.previewMode.value
</script>

<template>
  <div
    :class="['dz-body', collapsedLeft() && 'is-left-collapsed', collapsedRight() && 'is-right-collapsed']"
  >
    <MaterialPanel v-if="!collapsedLeft()" />

    <DesignCanvas />

    <InspectorPanel v-if="!collapsedRight()" />

    <!-- 收起后画布两上角留一个展开胶囊：只留一个图标的话，人得先猜刚才收的是哪一栏 -->
    <button v-if="session.leftCollapsed.value && !session.previewMode.value" class="dz-expand is-left" type="button" @click="session.leftCollapsed.value = false">
      <Icon name="sidebar" :size="15" />展开组件面板
    </button>
    <button v-if="session.rightCollapsed.value && !session.previewMode.value" class="dz-expand is-right" type="button" @click="session.rightCollapsed.value = false">
      <Icon name="sliders" :size="15" />展开配置面板
    </button>
  </div>
</template>

<style scoped>
.dz-body {
  /* 设计器自己的两栏宽度：--rail-w 是外壳导航轨的，改它会连带改掉所有页面的侧栏 */
  --dz-rail-w: 236px;
  --dz-inspector-w: 312px;

  position: relative;
  display: grid;
  grid-template-columns: var(--dz-rail-w) minmax(0, 1fr) var(--dz-inspector-w);
  gap: 12px;
  flex: 1;
  min-height: 0;
  padding: 12px;
}

.dz-body.is-left-collapsed {
  grid-template-columns: 0px minmax(0, 1fr) var(--dz-inspector-w);
}

.dz-body.is-right-collapsed {
  grid-template-columns: var(--dz-rail-w) minmax(0, 1fr) 0px;
}

.dz-body.is-left-collapsed.is-right-collapsed {
  grid-template-columns: 0px minmax(0, 1fr) 0px;
}

.dz-expand {
  position: absolute;
  top: 24px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--float-bg);
  box-shadow: var(--e1);
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 11.5px;
}

.dz-expand:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}

.dz-expand.is-left {
  left: 24px;
}

.dz-expand.is-right {
  right: 24px;
}
</style>
