<script setup lang="ts">
import { computed, watch } from 'vue'
import { useSession } from './session.ts'
import { useNarrowScreen } from '@yuanda-lowcode-ai/scaffold-ui/composables/narrow'
import MaterialPanel from './MaterialPanel.vue'
import DesignCanvas from './DesignCanvas.vue'
import InspectorPanel from './InspectorPanel.vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'

/* 三栏工作台：左物料与层级、中画布、右配置。
   收起的那一栏整列从 grid 里去掉、面板本身从 DOM 摘掉
   （宽度归零但留在 Tab 序列里，键盘要连按好几次才跳过一栏看不见的东西）。
   窄屏下两栏改成盖在画布上的抽屉，一次只开一扇。 */
const session = useSession()
const narrow = useNarrowScreen()
watch(narrow, value => session.setNarrow(value), { immediate: true })

const collapsedLeft = () => session.leftCollapsed.value || session.previewMode.value
const collapsedRight = () => session.rightCollapsed.value || session.previewMode.value
const paneDrawerOpen = computed(() => session.narrow.value && !session.previewMode.value
  && (!collapsedLeft() || !collapsedRight()))
</script>

<template>
  <div
    :class="[
      'dz-body',
      session.narrow.value && 'is-narrow',
      collapsedLeft() && 'is-left-collapsed',
      collapsedRight() && 'is-right-collapsed',
    ]"
  >
    <MaterialPanel v-if="!collapsedLeft()" />

    <DesignCanvas />

    <InspectorPanel v-if="!collapsedRight()" />

    <!-- 收起后画布两上角留一个展开胶囊：只留一个图标的话，人得先猜刚才收的是哪一栏 -->
    <button v-if="session.leftCollapsed.value && !session.previewMode.value" class="dz-expand is-left m-pill" type="button" @click="session.openPane('left')">
      <Icon name="panelLeftOpen" :size="15" />展开组件面板
    </button>
    <button v-if="session.rightCollapsed.value && !session.previewMode.value" class="dz-expand is-right m-pill" type="button" @click="session.openPane('right')">
      <Icon name="panelRightOpen" :size="15" />展开配置面板
    </button>

    <div v-if="paneDrawerOpen" class="dz-pane-scrim" aria-hidden="true" @click="session.closePanes()" />
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

/* 收起时整列从 grid 里去掉，而不是压成 0px：
   压成 0px 的话 column-gap 还在，画布左边永远留一条 12px 死带，看着像没塌干净 */
.dz-body.is-left-collapsed {
  grid-template-columns: minmax(0, 1fr) var(--dz-inspector-w);
}

.dz-body.is-right-collapsed {
  grid-template-columns: var(--dz-rail-w) minmax(0, 1fr);
}

.dz-body.is-left-collapsed.is-right-collapsed,
.dz-body.is-narrow {
  grid-template-columns: minmax(0, 1fr);
}

/* 窄屏：两栏盖在画布上，画布独占整幅（三栏一起挤的话画布会归零） */
.dz-body.is-narrow > .dz-rail,
.dz-body.is-narrow > .dz-inspector {
  position: absolute;
  top: 12px;
  bottom: 12px;
  z-index: 30;
  width: min(320px, 86vw);
  box-shadow: var(--e3);
}

.dz-body.is-narrow > .dz-rail { left: 12px; }
.dz-body.is-narrow > .dz-inspector { right: 12px; }
.dz-pane-scrim { position: absolute; inset: 0; z-index: 25; background: var(--overlay); backdrop-filter: blur(2px); -webkit-backdrop-filter: blur(2px); }

.dz-expand {
  position: absolute;
  top: 24px;
  z-index: 5;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 11px;
  border-radius: 999px;
  color: var(--ink);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.dz-expand:hover {
  border-color: var(--line-hover);
  color: var(--blue);
}

.dz-expand.is-left {
  left: 24px;
}

.dz-expand.is-right {
  right: 24px;
}
</style>
