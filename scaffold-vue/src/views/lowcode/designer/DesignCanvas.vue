<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import IconButton from '@yuanda-lowcode-ai/scaffold-ui/components/ui/IconButton.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import {
  DateInput, DateRangePicker, NumberInput, Select, Textarea, TextInput,
} from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { DictSelect } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { useSession } from './session.ts'
import { BLOCK_LABELS, iconOf, type UINode } from './design.ts'

/* 画布：这一块按发布后长什么样来画，不是示意图。
   每个区块用它自己在生成物里对应的那个物料渲染（查询区就是真的输入行、表格区就是真的表头），
   内容整体 pointer-events: none —— 点画布上的任何东西都是「选中这个区块」，
   而不是在一个还没接后端的设计稿里填一个填不出结果的表单。 */
const session = useSession()
const blocks = session.blocks
const selectedKey = session.selectedKey
// 顶层的 ref 绑定才会在模板里自动解包；写成 session.root.props 拿到的是 ComputedRef 本身
const root = session.root

const device = ref<'desktop' | 'tablet' | 'mobile'>('desktop')
const width = ref<'auto' | number>('auto')
const menuOpen = ref(false)

const widths: Record<string, Array<{ label: string, value: number }>> = {
  desktop: [{ label: '1440', value: 1440 }, { label: '1280', value: 1280 }, { label: '1024', value: 1024 }],
  tablet: [{ label: '834', value: 834 }, { label: '768', value: 768 }],
  mobile: [{ label: '414', value: 414 }, { label: '375', value: 375 }],
}

const canvasStyle = computed(() => ({
  maxWidth: width.value === 'auto' ? '100%' : `${width.value}px`,
}))

const deviceLabel = computed(() => ({ desktop: '桌面端', tablet: '平板端', mobile: '手机端' })[device.value])

const hot = ref<string | null>(null)

function onScroll(event: Event) {
  session.scrolled.value = (event.target as HTMLElement).scrollTop > 8
}

function onDrop(target: string, index?: number) {
  const item = session.drag.value
  hot.value = null
  session.drag.value = null
  if (item) session.place(item, target, index)
}

/** 拖过去又拖走时高亮必须退掉：只有 dragover/drop 没有 dragleave 的话，蓝槽会一直亮在人已经离开的那个位置 */
function onDragLeave(hotKey: string, event: DragEvent) {
  if (event.relatedTarget instanceof Node && (event.currentTarget as Node).contains(event.relatedTarget)) return
  if (hot.value === hotKey) hot.value = null
}

function onArmedClick(target: string, index?: number) {
  const type = session.armed.value
  if (!type) return
  session.dropArmed()
  session.place({ kind: 'new', type }, target, index)
}

function pick(node: UINode) {
  session.pick(node.key)
}

/**
 * 区块工具条上的三颗状态件。
 *
 * <p>锁定与显示都只改画布上的会话状态，不进 design_json，所以翻动它们不该把这一页标成「有未保存改动」；
 * 但它们在撤销栈里，按错了一下能撤回来。
 */
function toggleFlag(node: UINode, flag: 'visible' | 'locked') {
  // visible 缺省就是「显示」，所以只有 === false 才算藏着；写 `!node.visible` 会把 undefined 当成隐藏
  const current = flag === 'visible' ? node.visible !== false : node.locked === true
  session.act(() => session.store.setFlag(node.key, flag, !current))
}

/** 拖拽中的那颗物料长什么样：落点槽要回答「松手会在这里插什么」，只亮一条蓝线不够。 */
const dragging = computed(() => {
  const item = session.drag.value
  if (!item) return null
  return { type: item.type, label: BLOCK_LABELS[item.type] ?? item.type, icon: iconOf(item.type) }
})

function titleOf(block: UINode) {
  return String(block.props.title ?? BLOCK_LABELS[block.type] ?? block.type)
}

/** 查询区与表单里的控件按绑定类型换成真的物料，值一律留空：设计稿里填进去的东西不会进生成物 */
const controlProps = (node: UINode) => ({
  modelValue: null as string | number | null,
  placeholder: String(node.props.placeholder ?? ''),
})

const fieldLabel = (node: UINode) => String(node.props.label ?? node.type)

/**
 * 字典下拉的字典挂在模型字段上（lc_field.dict_type），设计里不写第二份。
 * 没解析出字典就退回普通下拉的样子：画布要显示「这里会是一个字典选择」，
 * 而不是拿一个空字典去发一次必然为空的请求。
 */
function dictTypeOf(node: UINode) {
  const code = String(node.props.field ?? '')
  return session.fields.find(field => field.fieldCode === code)?.dictType ?? ''
}
</script>

<template>
  <main class="dz-canvas m-surface" @scroll="onScroll" @click="onArmedClick('PageContainer')">
    <div class="dz-canvas-top">
      <div class="dz-device">
        <button type="button" class="dz-device-btn" :aria-expanded="menuOpen" @click.stop="menuOpen = !menuOpen">
          <Icon name="monitor" :size="14" />
          <span>{{ deviceLabel }}</span>
          <span class="dz-device-width">{{ width === 'auto' ? '自适应' : `${width}px` }}</span>
          <Icon name="chevronDown" :size="12" />
        </button>
        <div v-if="menuOpen" class="dz-device-menu m-float" @click.stop>
          <Segmented
            v-model="device"
            label="终端类型"
            :options="[{ label: '桌面端', value: 'desktop' }, { label: '平板端', value: 'tablet' }, { label: '手机端', value: 'mobile' }]"
          />
          <div class="dz-device-rows">
            <button
              type="button"
              :class="['dz-device-row', width === 'auto' && 'is-on']"
              @click="width = 'auto'; menuOpen = false"
            >自适应宽度</button>
            <button
              v-for="option in widths[device]"
              :key="option.value"
              type="button"
              :class="['dz-device-row', width === option.value && 'is-on']"
              @click="width = option.value; menuOpen = false"
            >{{ option.label }} px</button>
          </div>
        </div>
      </div>
    </div>

    <div class="dz-canvas-dots" aria-hidden="true" />

    <div class="dz-canvas-list" :style="canvasStyle">
      <!-- 页面头：与生成物同一件 PageHeader，标题兜到页面名而不是模型编码 -->
      <section
        :class="['dz-node', 'is-page', selectedKey === null && 'is-selected']"
        @click.stop="session.pick(null)"
      >
        <div class="dz-node-body">
          <h1 class="dz-page-title">{{ root.props.title || session.page.pageName || '未命名页面' }}</h1>
          <p v-if="root.props.desc" class="dz-page-desc">{{ root.props.desc }}</p>
          <p v-else class="dz-page-desc is-hint">点这里在右侧写页面说明</p>
        </div>
      </section>

      <template v-for="(block, bi) in blocks" :key="block.key">
        <div
          :class="['dz-drop-slot', hot === `after-${bi}` && 'is-active', hot === `after-${bi}` && dragging && 'has-hint']"
          @dragover.prevent="hot = `after-${bi}`"
          @dragleave="onDragLeave(`after-${bi}`, $event)"
          @drop.stop="onDrop('PageContainer', bi + 1)"
          @click.stop="onArmedClick('PageContainer', bi + 1)"
        >
          <span v-if="hot === `after-${bi}` && dragging" class="dz-drop-hint">
            <Icon :name="dragging.icon" :size="12" />松手插到第 {{ bi + 2 }} 个 · {{ dragging.label }}
          </span>
        </div>

        <section
          :class="[
            'dz-node',
            `is-${block.type}`,
            selectedKey === block.key && 'is-selected',
            block.visible === false && 'is-hidden',
            block.locked && 'is-locked',
          ]"
          :data-type="block.type"
          @click.stop="pick(block)"
          @dragover.prevent="!block.locked && (hot = block.type)"
          @dragleave="onDragLeave(block.type, $event)"
          @drop.stop="!block.locked && onDrop(block.type)"
        >
          <div v-if="selectedKey === block.key && !session.previewMode.value" class="dz-node-bar">
            <span class="dz-node-bar-name"><Icon :name="iconOf(block.type)" :size="13" />{{ BLOCK_LABELS[block.type] ?? block.type }}</span>
            <IconButton name="arrowUp" label="上移区块" small :disabled="block.locked" @click.stop="session.act(() => session.store.move(block.key, 'PageContainer', Math.max(0, bi - 1)))" />
            <IconButton name="arrowDown" label="下移区块" small :disabled="block.locked" @click.stop="session.act(() => session.store.move(block.key, 'PageContainer', bi + 2))" />
            <IconButton
              :name="block.visible === false ? 'eyeOff' : 'eye'"
              :label="block.visible === false ? '在画布上显示这一格' : '在画布上藏起这一格'"
              hint="只影响画布上的专注度：藏起来的这一格照样在生成的页面里，存库也不会少它"
              small
              :active="block.visible === false"
              @click.stop="toggleFlag(block, 'visible')"
            />
            <IconButton
              :name="block.locked ? 'lock' : 'unlock'"
              :label="block.locked ? '解锁这一格' : '锁定这一格'"
              hint="锁定后这一格移动不了、删不掉、也拖不进东西 —— 防误操作，不是内容改动"
              small
              :active="block.locked"
              @click.stop="toggleFlag(block, 'locked')"
            />
            <IconButton name="x" label="删掉这一整块" small danger :disabled="block.locked" @click.stop="session.act(() => session.store.remove(block.key))" />
          </div>

          <div class="dz-node-body">
            <!-- 查询区：一行真的输入控件 + 固定的重置与查询 -->
            <template v-if="block.type === 'SearchForm'">
              <div class="dz-block-head"><b>查询条件</b><span class="dz-block-count">{{ block.children.length }} 项</span></div>
              <div class="dz-search-row" :style="{ gap: `${Number(block.props.gap ?? 12)}px` }">
                <label v-for="child in block.children" :key="child.key" class="dz-search-cell" :style="{ gridColumn: `span ${Math.max(1, Math.min(3, Number(block.props.columns ?? 3) - 2))}` }">
                  <span class="dz-cell-label">{{ fieldLabel(child) }}</span>
                  <DateRangePicker v-if="child.type === 'DateRangePicker'" :model-value="null" />
                  <DateInput v-else-if="child.type === 'DateInput'" :model-value="null" />
                  <NumberInput v-else-if="child.type === 'NumberInput'" :model-value="null" :placeholder="controlProps(child).placeholder" />
                  <DictSelect v-else-if="child.type === 'DictSelect' && dictTypeOf(child)" :model-value="null" :type="dictTypeOf(child)" :placeholder="controlProps(child).placeholder" />
                  <Select v-else-if="child.type === 'DictSelect'" :model-value="null" :options="[]" :placeholder="`字典：${String(child.props.field ?? '未绑定字段')}`" />
                  <Select v-else-if="child.type === 'Select'" :model-value="null" :options="[]" :placeholder="controlProps(child).placeholder || '请选择'" />
                  <Textarea v-else-if="child.type === 'Textarea'" :model-value="null" :rows="2" />
                  <TextInput v-else :model-value="null" :placeholder="controlProps(child).placeholder" />
                </label>
                <p v-if="!block.children.length" class="dz-block-empty">把控件拖到这里 —— 查询条件从模型的 `is_query` 字段来</p>
              </div>
            </template>

            <!-- 工具栏：整页动作 -->
            <template v-else-if="block.type === 'Toolbar'">
              <div class="dz-block-head"><b>工具栏</b><span class="dz-block-count">{{ block.children.length }} 个按钮</span></div>
              <div class="dz-btn-row">
                <span v-for="child in block.children" :key="child.key" :class="['dz-btn', child.props.variant === 'primary' ? 'is-primary' : '', child.props.danger ? 'is-danger' : '']">{{ child.props.label ?? '按钮' }}</span>
                <p v-if="!block.children.length" class="dz-block-empty">拖「按钮」进来：新增、导出这类整页动作</p>
              </div>
            </template>

            <!-- 表格区：真表头 + 三行占位，行高与密度跟着外观设置走而不是设计 -->
            <template v-else-if="block.type === 'DataTable'">
              <div class="dz-block-head">
                <b>{{ titleOf(block) === 'DataTable' ? '数据表格' : titleOf(block) }}</b>
                <span class="dz-block-count">{{ block.columns.length }} 列<template v-if="block.props.pagination !== false"> · 每页 {{ block.props.pageSize ?? 10 }}</template></span>
              </div>
              <div class="dz-table-wrap">
                <table class="table">
                  <thead>
                    <tr>
                      <th class="col-check" aria-label="勾选列" />
                      <th v-for="column in block.columns" :key="column.key" :style="column.width ? { width: `${column.width}px` } : undefined">{{ column.title || column.field }}</th>
                      <th class="col-actions">操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="row in 3" :key="row">
                      <td class="col-check"><span class="dz-check" aria-hidden="true" /></td>
                      <td v-for="column in block.columns" :key="column.field"><span class="dz-cell-skeleton" /></td>
                      <td class="col-actions"><span class="dz-link-skeleton" /></td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <p v-if="!block.columns.length" class="dz-block-empty">列在右侧「表格区」里加，不从物料库拖</p>
            </template>

            <!-- 行操作：平铺几个、其余进更多 -->
            <template v-else-if="block.type === 'RowActions'">
              <div class="dz-block-head"><b>行操作</b><span class="dz-block-count">直出 {{ block.props.priority ?? 2 }} 个</span></div>
              <div class="dz-btn-row is-inline">
                <span v-for="(child, ci) in block.children" :key="child.key" :class="['dz-link', child.props.danger ? 'is-danger' : '', ci >= Number(block.props.priority ?? 2) ? 'is-hidden' : '']">{{ child.props.label ?? '按钮' }}</span>
                <span v-if="block.children.length > Number(block.props.priority ?? 2)" class="dz-link is-more">更多 <Icon name="chevronDown" :size="11" /></span>
                <p v-if="!block.children.length" class="dz-block-empty">拖「按钮」进来：编辑、删除这类逐行动作</p>
              </div>
            </template>

            <!-- 表单弹窗：新增与编辑共用，两列 -->
            <template v-else-if="block.type === 'FormContainer'">
              <div class="dz-block-head"><b>表单弹窗</b><span class="dz-block-count">{{ block.children.length }} 格</span></div>
              <div class="dz-form-preview">
                <div class="dz-form-grid" :style="{ gridTemplateColumns: `repeat(${block.props.columns ?? 2}, minmax(0, 1fr))` }">
                  <label v-for="child in block.children" :key="child.key" class="dz-search-cell">
                    <span class="dz-cell-label">{{ fieldLabel(child) }}<em v-if="child.props.required" class="dz-req">*</em></span>
                    <DateInput v-if="child.type === 'DateInput'" :model-value="null" />
                    <NumberInput v-else-if="child.type === 'NumberInput'" :model-value="null" />
                    <DictSelect v-else-if="child.type === 'DictSelect' && dictTypeOf(child)" :model-value="null" :type="dictTypeOf(child)" />
                    <Select v-else-if="child.type === 'DictSelect'" :model-value="null" :options="[]" :placeholder="`字典：${String(child.props.field ?? '未绑定字段')}`" />
                    <Select v-else-if="child.type === 'Select'" :model-value="null" :options="[]" />
                    <Textarea v-else-if="child.type === 'Textarea'" :model-value="null" :rows="2" />
                    <TextInput v-else :model-value="null" />
                  </label>
                </div>
                <p v-if="!block.children.length" class="dz-block-empty">拖控件进来：表单收哪几格由模型的 `is_insert / is_edit` 决定</p>
              </div>
            </template>

            <template v-else>
              <div class="dz-block-head"><b>{{ BLOCK_LABELS[block.type] ?? block.type }}</b></div>
            </template>
          </div>
        </section>
      </template>

      <div
        :class="['dz-drop-slot', 'is-tail', hot === 'PageContainer:end' && 'is-active']"
        @dragover.prevent="hot = 'PageContainer:end'"
        @dragleave="onDragLeave('PageContainer:end', $event)"
        @drop.stop="onDrop('PageContainer')"
        @click.stop="onArmedClick('PageContainer')"
      >
        <span v-if="hot === 'PageContainer:end' && dragging" class="dz-drop-hint">
          <Icon :name="dragging.icon" :size="12" />松手插到末尾 · {{ dragging.label }}
        </span>
        <span v-else-if="!blocks.length" class="dz-tail-hint">把区块拖到这里开始：查询区 / 工具栏 / 表格区 / 行操作 / 表单弹窗</span>
      </div>
    </div>
  </main>
</template>

<style scoped>
.dz-canvas {
  position: relative;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 14px 16px 40px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--canvas);
}

/* 参考点阵：只是画布的对齐底纹，不进生成物 */
.dz-canvas-dots {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(var(--tick) 1px, transparent 1px);
  background-size: 22px 22px;
  opacity: .3;
}

.dz-canvas-top {
  position: sticky;
  top: 0;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  margin-bottom: 6px;
}

.dz-device {
  position: relative;
}

.dz-device-btn {
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

.dz-device-btn:hover {
  border-color: var(--line-strong);
  color: var(--ink);
}

.dz-device-width {
  color: var(--ink-faint);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
}

.dz-device-menu {
  position: absolute;
  top: 34px;
  right: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 210px;
  padding: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  box-shadow: var(--e3);
}

.dz-device-rows {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.dz-device-row {
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

.dz-device-row:hover {
  background: var(--hover-bg);
}

.dz-device-row.is-on {
  background: var(--blue-soft);
  color: var(--blue);
}

.dz-canvas-list {
  position: relative;
  z-index: 1;
  width: 100%;
  margin: 0 auto;
  transition: max-width .3s var(--ease-premium);
}

/* 节点卡：与原型同一套语汇（圆角 20、实色面、内高光 + e1；选中抬 2px 并描蓝环） */
.dz-node {
  position: relative;
  border: 1px solid var(--line);
  border-radius: 20px;
  background: var(--surface-solid);
  box-shadow: var(--edge), var(--e1);
  cursor: pointer;
  transition: transform .34s var(--ease-premium), box-shadow .34s var(--ease-premium), border-color .28s var(--ease-premium);
}

.dz-node:hover {
  border-color: var(--line-hover);
  box-shadow: var(--edge), var(--e2);
}

.dz-node.is-selected {
  border-color: var(--blue);
  box-shadow: var(--edge), var(--e2), 0 0 0 4px var(--blue-soft);
  transform: translateY(-2px);
}

/* 藏起来不是删掉：留一条淡出 + 虚线，让人知道这一格还在、只是现在不想看 */
.dz-node.is-hidden {
  opacity: .42;
  border-style: dashed;
}

.dz-node.is-locked {
  border-color: var(--line-strong);
}

.dz-node.is-locked::after {
  content: '已锁定';
  position: absolute;
  top: 8px;
  left: 12px;
  padding: 1px 7px;
  border-radius: 999px;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 10.5px;
  font-weight: 620;
}

.dz-node-body {
  padding: 14px 16px;
}

.dz-node.is-page {
  margin-bottom: 6px;
  background: none;
  border-color: transparent;
  box-shadow: none;
}

.dz-node.is-page.is-selected {
  background: var(--surface-solid);
  border-color: var(--blue);
}

.dz-page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 680;
  letter-spacing: -.02em;
  color: var(--ink);
}

.dz-page-desc {
  margin: 6px 0 0;
  max-width: 76ch;
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.7;
}

.dz-page-desc.is-hint {
  color: var(--ink-faint);
}

.dz-node-bar {
  position: absolute;
  top: -13px;
  right: 12px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 26px;
  padding: 0 4px 0 10px;
  border-radius: 999px;
  background: var(--blue);
  box-shadow: var(--e-accent);
  color: #fff;
}

.dz-node-bar-name {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin-right: 4px;
  font-size: 11.5px;
  font-weight: 620;
  white-space: nowrap;
}

.dz-node-bar :deep(.icon-btn) {
  color: rgba(255, 255, 255, .86);
}

.dz-node-bar :deep(.icon-btn:hover) {
  background: rgba(255, 255, 255, .16);
  color: #fff;
}

.dz-block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 12.5px;
}

.dz-block-head b {
  color: var(--ink-muted);
  font-weight: 620;
}

.dz-block-count {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 11.5px;
  font-variant-numeric: tabular-nums;
}

.dz-search-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: end;
}

.dz-search-cell {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}

.dz-cell-label {
  color: var(--ink-soft);
  font-size: 11.5px;
}

.dz-req {
  margin-left: 2px;
  color: var(--red);
  font-style: normal;
}

.dz-btn-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.dz-btn {
  display: inline-flex;
  align-items: center;
  height: var(--control-h);
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--pill-bg);
  color: var(--ink);
  font-size: 13px;
}

.dz-btn.is-primary {
  border-color: transparent;
  background: var(--blue);
  box-shadow: var(--e-accent);
  color: #fff;
}

.dz-btn.is-danger {
  border-color: var(--red);
  color: var(--red-text);
}

.dz-link {
  color: var(--blue);
  font-size: 12.5px;
}

.dz-link.is-danger {
  color: var(--red-text);
}

.dz-link.is-more {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  color: var(--ink-muted);
}

.dz-table-wrap {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
}

.dz-table-wrap .table {
  width: 100%;
}

.dz-check {
  display: inline-block;
  width: 15px;
  height: 15px;
  border: 1.5px solid var(--line-strong);
  border-radius: 4px;
  background: var(--input-bg);
}

.dz-cell-skeleton,
.dz-link-skeleton {
  display: inline-block;
  height: 10px;
  border-radius: 999px;
  background: var(--hover-bg);
}

.dz-cell-skeleton {
  width: 68%;
}

.dz-link-skeleton {
  width: 42px;
  background: var(--blue-soft);
}

.dz-form-preview {
  padding: 14px 16px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  background: var(--canvas);
}

.dz-form-grid {
  display: grid;
  gap: 12px;
}

.dz-block-empty {
  grid-column: 1 / -1;
  margin: 2px 0 0;
  color: var(--ink-faint);
  font-size: 12px;
}

.dz-drop-slot {
  height: 6px;
  margin: 4px 0;
  border-radius: 12px;
  background: transparent;
  transition: height .24s var(--ease-premium), background-color .24s var(--ease-premium), box-shadow .24s var(--ease-premium);
}

.dz-drop-slot.is-active {
  height: 10px;
  background: var(--blue-soft);
  box-shadow: inset 0 0 0 1.5px var(--blue);
}

/* 拖拽中：落点槽要回答「松手会在这里插什么、排在第几个」，只亮一条蓝线还得靠人去数 */
.dz-drop-slot.has-hint {
  height: auto;
  min-height: 26px;
  padding: 0 10px;
  display: flex;
  align-items: center;
}

.dz-drop-hint {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--blue);
  font-size: 11.5px;
  font-weight: 620;
  white-space: nowrap;
}

.dz-drop-slot.is-tail {
  display: flex;
  align-items: center;
  justify-content: center;
  height: auto;
  min-height: 46px;
  margin-top: 8px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-md);
  outline-offset: -3px;
  cursor: copy;
}

.dz-tail-hint {
  color: var(--ink-faint);
  font-size: 12px;
}
</style>
