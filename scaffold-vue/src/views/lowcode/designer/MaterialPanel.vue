<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import IconButton from '@yuanda-lowcode-ai/scaffold-ui/components/ui/IconButton.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import { useSession } from './session.ts'
import { PALETTE } from './design.ts'
import LayerTree from './LayerTree.vue'

/* 左栏：组件物料与页面层级两个视图共用一条面板。
   物料卡只列 design.ts 的 PALETTE（与后端 ComponentCatalog 同一份），
   原型里那 19 种 kind 中生成器产不出来的（栅格容器、插槽容器、数值指标、状态标签……）
   不进来 —— 放进来就是让人搭一个保存时才报 400 的页面。 */
const session = useSession()
const tab = ref<'materials' | 'tree'>('materials')
const query = ref('')
const collapsed = ref<Record<string, boolean>>({})
const armed = session.armed

const groups = computed(() => {
  const key = query.value.trim().toLowerCase()
  return PALETTE.map(group => ({
    ...group,
    items: key
      ? group.items.filter(item => item.label.toLowerCase().includes(key) || item.type.toLowerCase().includes(key) || item.desc.includes(key))
      : group.items,
  })).filter(group => group.items.length)
})

const total = computed(() => groups.value.reduce((sum, group) => sum + group.items.length, 0))
const nodeCount = computed(() => session.blocks.value.length)

function toggleAll() {
  const anyOpen = PALETTE.some(group => !collapsed.value[group.group])
  collapsed.value = Object.fromEntries(PALETTE.map(group => [group.group, anyOpen]))
}
</script>

<template>
  <aside class="dz-rail m-solid" aria-label="组件物料与页面层级面板">
    <div class="dz-rail-head">
      <Segmented
        v-model="tab"
        label="左栏视图"
        :options="[{ label: '组件物料', value: 'materials' }, { label: '页面层级', value: 'tree' }]"
      />
      <IconButton name="sidebar" label="收起左侧面板" small @click="session.leftCollapsed.value = true" />
    </div>

    <template v-if="tab === 'materials'">
      <label class="dz-search">
        <Icon name="search" :size="14" />
        <input v-model="query" type="text" placeholder="搜索组件物料" aria-label="检索组件物料">
      </label>

      <div class="dz-rail-scroll no-scrollbar">
        <div v-if="!groups.length" class="dz-rail-empty">没有匹配的组件。物料只列生成器产得出来的那几类。</div>
        <section v-for="group in groups" :key="group.group" class="dz-group">
          <button type="button" class="dz-group-head" :aria-expanded="!collapsed[group.group]" @click="collapsed[group.group] = !collapsed[group.group]">
            <span class="dz-group-title">
              <i :class="['dz-group-dot', `tone-${group.tone}`]" aria-hidden="true" />
              {{ group.group }}
            </span>
            <span class="dz-group-meta">
              <span class="dz-group-count">{{ group.items.length }}</span>
              <Icon :name="collapsed[group.group] ? 'chevronRight' : 'chevronDown'" :size="13" />
            </span>
          </button>

          <div v-show="!collapsed[group.group]" class="dz-tiles">
            <button
              v-for="item in group.items"
              :key="item.type"
              type="button"
              :class="['dz-tile', armed === item.type && 'is-armed']"
              :title="`${item.label} · ${item.desc}`"
              :aria-label="item.label"
              draggable="true"
              @dragstart="session.drag.value = { kind: 'new', type: item.type }"
              @dragend="session.drag.value = null"
              @click="session.arm(item.type)"
            >
              <span class="dz-tile-box" aria-hidden="true"><Icon :name="item.icon" :size="19" /></span>
              <span class="dz-tile-label">{{ item.label }}</span>
            </button>
          </div>
        </section>
      </div>

      <footer class="dz-rail-foot">
        <span class="dz-rail-foot-text">画布区块 <b class="tnum">{{ nodeCount }}</b> 个 · 物料 <b class="tnum">{{ total }}</b> 项</span>
        <button type="button" class="dz-mini" @click="toggleAll">{{ PALETTE.some(g => !collapsed[g.group]) ? '全部收起' : '全部展开' }}</button>
      </footer>
    </template>

    <LayerTree v-else />
  </aside>
</template>

<style scoped>
.dz-rail {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.dz-rail-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dz-rail-head :deep(.segmented) {
  flex: 1;
}

.dz-search {
  display: flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--input-bg);
  box-shadow: var(--edge);
  color: var(--ink-faint);
}

.dz-search:focus-within {
  border-color: var(--blue);
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}

.dz-search input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: none;
  color: var(--ink);
  font: inherit;
  font-size: 12.5px;
}

.dz-search input::placeholder {
  color: var(--ink-faint);
}

.dz-rail-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-right: -4px;
  padding-right: 4px;
}

.dz-rail-empty {
  padding: 22px 4px;
  color: var(--ink-faint);
  font-size: 12px;
  text-align: center;
}

.dz-group + .dz-group {
  margin-top: 12px;
}

.dz-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  height: 26px;
  padding: 0 4px;
  border: 0;
  background: none;
  color: var(--ink);
  cursor: pointer;
  font-size: 12.5px;
  font-weight: 600;
}

.dz-group-title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

/* 分组点只区分「这是哪一类物料」，不表示状态 —— 与 DESIGN.md 的分类色口径一致 */
.dz-group-dot {
  width: 6px;
  height: 6px;
  border-radius: 999px;
  flex: none;
}

.dz-group-dot.tone-sky { background: var(--cat-2-ink); }
.dz-group-dot.tone-sand { background: var(--cat-1-ink); }
.dz-group-dot.tone-rose { background: var(--cat-4-ink); }

.dz-group-meta {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--ink-faint);
}

.dz-group-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 20px;
  height: 17px;
  padding: 0 5.5px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--canvas);
  box-shadow: var(--edge);
  color: var(--ink-soft);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 10.5px;
  font-weight: 600;
  line-height: 1;
}

.dz-tiles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin-top: 4px;
}

.dz-tile {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
  height: 66px;
  padding: 3.5px 3.5px 5px;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--surface-solid);
  box-shadow: var(--edge), var(--e1);
  color: var(--ink);
  cursor: grab;
  transition: transform .24s var(--ease-premium), box-shadow .24s var(--ease-premium), border-color .24s var(--ease-premium);
}

.dz-tile:hover {
  border-color: rgba(35, 109, 233, .28);
  box-shadow: var(--edge), var(--e2);
  transform: translateY(-1.5px);
}

.dz-tile:active {
  cursor: grabbing;
  transform: scale(.96);
}

.dz-tile.is-armed {
  border-color: var(--blue);
  box-shadow: var(--edge), var(--e2), 0 0 0 3px var(--blue-soft);
}

/* 上层是微缩展台：满宽 37px 凹槽，图标 19px，内外同心圆角 */
.dz-tile-box {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 37px;
  border: 1px solid var(--line);
  border-radius: 7.5px;
  background: var(--canvas);
  box-shadow: inset 0 1px 2px rgba(46, 58, 62, .035);
  color: var(--ink-muted);
  flex: none;
  transition: color .24s var(--ease-premium), background-color .24s var(--ease-premium), border-color .24s var(--ease-premium);
}

.dz-tile:hover .dz-tile-box {
  border-color: rgba(35, 109, 233, .16);
  background: var(--blue-soft);
  color: var(--blue);
}

.dz-tile-box :deep(svg) {
  transition: transform .24s var(--ease-premium);
}

.dz-tile:hover .dz-tile-box :deep(svg) {
  transform: scale(1.07);
}

.dz-tile-label {
  color: var(--ink-soft);
  font-size: 11px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
}

.dz-rail-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 9px;
  border-top: 1px solid var(--line);
}

.dz-rail-foot-text {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.dz-rail-foot-text b {
  color: var(--ink);
  font-weight: 700;
}

.dz-mini {
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  background: var(--float-bg);
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 11.5px;
}

.dz-mini:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}
</style>
