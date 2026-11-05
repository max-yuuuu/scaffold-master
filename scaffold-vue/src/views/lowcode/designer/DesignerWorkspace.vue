<script setup lang="ts">
import { computed, ref } from 'vue'
import { Field, NumberInput, Select, Switch, TextInput, Textarea } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { platformApi, type FieldRow, type PageDetail } from '@/api'
import {
  ACTION_LABELS,
  BLOCK_LABELS,
  BLOCKS,
  PALETTE,
  PROP_LABELS,
  createDesigner,
  propsOf,
  type DesignJson,
  type ModelField,
  type UINode,
} from './design.ts'

/* ============================================================
   页面设计器（方案 §3.1 的三栏工作台，PR 6C 第一期）。
   树的操作、撤销重做、序列化全在 ./design.ts 这份纯 TS 里，这里只做两件事：
   把手势（拖到哪、改了哪一格）翻译成功令，以及把树画出来。
   落点合法性前端也拦一份是为了手感，权威判定在后端 PageDesign —— 保存走的是真接口。
   ============================================================ */

const props = defineProps<{ page: PageDetail, fields: FieldRow[] }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const store = createDesigner(props.page.design as unknown as DesignJson)
store.setModel(props.page.modelCode)

// store 里的树是普通可变对象，Vue 看不见它的 splice；每个动作后 bump 一下重算
const tick = ref(0)
const act = (run: () => void) => {
  run()
  tick.value += 1
}

const root = computed(() => {
  void tick.value
  return store.root
})
const blocks = computed(() => root.value.children)
// 这几个读数都来自 store 里的普通变量，Vue 看不见：每个都手动挂上 tick，否则点了方块右侧还是页面属性
const selected = computed(() => (tick.value, store.selected ? store.find(store.selected) ?? null : null))
const selectedParent = computed(() => (tick.value, store.selected ? store.parentOf(store.selected) : null))
// 没选中任何方块 = 编辑页面本身（标题与说明）
const selectedKey = computed(() => (tick.value, store.selected))
const isPage = computed(() => (tick.value, store.selected === null))

const modelFields = computed<ModelField[]>(() => props.fields as unknown as ModelField[])
const fieldOptions = computed(() => modelFields.value.map(field => ({ label: `${field.fieldName}（${field.fieldCode}）`, value: field.fieldCode })))
const referenced = computed(() => (tick.value, store.referencedFields()))

/* ── 拖拽 ── */
type Drag = { kind: 'new', type: string } | { kind: 'move', type: string, key: string }
const drag = ref<Drag | null>(null)
const hot = ref<string | null>(null)
/**
 * 点选放置：先点组件库里的一个，再点画布上的容器放下去。
 * 原生拖拽在辅助技术（键盘、读屏）下走不通，这条路径不是备胎，是同一件事的另一种输入。
 */
const armed = ref<string | null>(null)

function startDrag(item: Drag) {
  drag.value = item
}

function arm(type: string) {
  armed.value = type
  pushToast(`已拿起「${type}」，点画布上的容器放下去`)
}

/** 选中不改树，所以它不在 act() 里；但视图照样要重算，不然点了方块右侧还是页面属性 */
function pick(key: string | null) {
  store.select(key)
  tick.value += 1
}

function onArmedClick(target: string) {
  if (!armed.value) return
  const type = armed.value
  armed.value = null
  place({ kind: 'new', type }, target)
}

/** 拖放与点选共用同一段落点逻辑，两条输入不可能漂开 */
function place(item: Drag, target: string, index?: number) {
  hot.value = null
  try {
    if (item.kind === 'new') {
      act(() => store.insert(target, { type: item.type, props: defaultProps(item.type) }, index))
    } else {
      act(() => store.move(item.key, target, index))
    }
  } catch (error) {
    pushToast((error as Error).message, 'danger')
  } finally {
    drag.value = null
  }
}

function onDrop(target: string, index?: number) {
  if (!drag.value) return
  place(drag.value, target, index)
}

function defaultProps(type: string) {
  if (type === 'Button') return { label: '按钮', action: 'refresh' }
  if ((BLOCKS as readonly string[]).includes(type)) return {}
  return { label: PALETTE[1]!.items.find(item => item.type === type)?.label ?? type }
}

/* ── 属性面板 ── */
const visibleProps = computed(() => {
  const node = selected.value
  if (!node) return []
  const parent = selectedParent.value?.type
  return propsOf(node.type).filter(name => {
    if (name === 'queryType') return parent === 'SearchForm'
    if (name === 'required') return parent === 'FormContainer'
    // placeholder 对日期与字典下拉没意义（值不是人打的自由文本）
    if (name === 'placeholder') return ['TextInput', 'Textarea', 'NumberInput', 'Select'].includes(node.type)
    return true
  })
})

function propValue(name: string) {
  return selected.value?.props[name] ?? ''
}

function setProp(name: string, value: unknown) {
  const node = selected.value
  if (!node) return
  act(() => store.setProp(node.key, name, value))
}

const queryTypeOptions = ['=', '!=', '>', '>=', '<', '<=', 'LIKE', 'BETWEEN', 'IN'].map(value => ({ label: value, value }))
const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ label: `${label}（${value}）`, value }))
const variantOptions = [{ label: '主要', value: 'primary' }, { label: '普通', value: '' }]
const alignOptions = ['left', 'right', 'center'].map(value => ({ label: value, value }))
const renderOptions = ['text', 'money', 'tag', 'datetime'].map(value => ({ label: value, value }))

const columns = computed(() => {
  void tick.value
  return store.columns()
})

function addColumn() {
  act(() => store.addColumn({ field: modelFields.value[0]?.fieldCode ?? '', title: '新列' }))
}

function updateColumn(key: string, patch: Record<string, unknown>) {
  act(() => store.updateColumn(key, patch))
}

function removeColumn(index: number) {
  act(() => store.removeColumn(index))
}

/* ── 保存 ── */
const saving = ref(false)

async function save() {
  saving.value = true
  try {
    await platformApi.pages.update(props.page.id, {
      pageName: props.page.pageName,
      routePath: props.page.routePath,
      menuIcon: props.page.menuIcon,
      menuSort: props.page.menuSort,
      design: store.json() as unknown as Record<string, unknown>,
    })
    pushToast('设计已保存，下次发布会生成这一页')
  } catch (error) {
    // 后端那句 400 带着 layout.children[x] 的路径，这里原样抛给人，不翻译成第二套话术
    toastError(error, '保存失败')
  } finally {
    saving.value = false
  }
}

function cellLabel(node: UINode) {
  const field = node.props.field
  return String(node.props.label ?? (field ? `${field}` : node.type))
}

function blockSummary(block: UINode) {
  if (block.type === 'DataTable') return `${block.columns.length} 列`
  return `${block.children.length} 项`
}
</script>

<template>
  <div class="designer">
    <header class="dz-head">
      <button class="dz-back" type="button" @click="emit('close')">← 返回页面列表</button>
      <div class="dz-title">
        <strong>{{ page.pageName }}</strong>
        <span class="dz-sub">{{ page.modelCode }} · {{ page.pageCode }}</span>
      </div>
      <div class="dz-head-actions">
        <PermissionButton perm="lowcode:page:edit" variant="primary" :saving="saving" @click="save">保存设计</PermissionButton>
      </div>
    </header>

    <div class="dz-body">
      <aside class="dz-pane dz-left">
        <div v-for="group in PALETTE" :key="group.group" class="dz-group">
          <p class="dz-group-head">{{ group.group }}</p>
          <button
            v-for="item in group.items"
            :key="item.type"
            class="dz-palette"
            type="button"
            draggable="true"
            @dragstart="startDrag({ kind: 'new', type: item.type })"
            @click="arm(item.type)"
          >{{ item.label }}<em v-if="armed === item.type"> · 已拿起</em></button>
        </div>
        <p class="dz-hint">拖到中间的方块上，或先点组件再点落点。</p>
      </aside>

      <main class="dz-pane dz-canvas" @dragover.prevent="hot = 'PageContainer:end'" @drop="onDrop('PageContainer')" @click="onArmedClick('PageContainer')">
        <button
          class="dz-pagehead"
          :class="{ 'is-active': isPage }"
          type="button"
          @click="pick(null)"
        >
          <strong>{{ root.props.title || page.pageName || '未命名页面' }}</strong>
          <em>{{ root.props.desc || '点这里写页面说明' }}</em>
        </button>

        <section v-for="(block, bi) in blocks" :key="block.key" class="dz-block" :data-type="block.type">
          <div
            class="dz-block-head"
            draggable="true"
            @dragstart.stop="startDrag({ kind: 'move', type: block.type, key: block.key })"
            @click="pick(block.key)"
          >
            <b>{{ BLOCK_LABELS[block.type] ?? block.type }}</b>
            <span class="dz-count">{{ blockSummary(block) }}</span>
            <button class="dz-x" type="button" title="删掉这一整块" @click.stop="act(() => store.remove(block.key))">×</button>
          </div>

          <div
            class="dz-cells"
            :class="{ 'is-hot': hot === block.type, 'is-armed': armed }"
            @dragover.prevent="hot = block.type"
            @drop.stop="onDrop(block.type)"
            @click.stop="onArmedClick(block.type)"
          >
            <template v-for="(child, ci) in block.children" :key="child.key">
              <div
                class="dz-cell"
                :class="{ 'is-active': selectedKey === child.key }"
                draggable="true"
                @dragstart.stop="startDrag({ kind: 'move', type: child.type, key: child.key })"
                @click.stop="pick(child.key)"
                @drop.stop="onDrop(block.type, ci)"
              >
                <span>{{ cellLabel(child) }}</span>
                <button class="dz-x" type="button" title="移出去" @click.stop="act(() => store.remove(child.key))">×</button>
              </div>
              <span v-if="ci === block.children.length - 1" class="dz-tail">＋</span>
            </template>
            <span v-if="!block.children.length && block.type !== 'DataTable'" class="dz-empty">把控件拖到这里</span>
            <span v-if="!block.children.length && block.type === 'DataTable' && !block.columns.length" class="dz-empty">列在右边加，不在这里拖</span>

            <template v-if="block.type === 'DataTable'">
              <div v-for="(column, index) in block.columns" :key="column.key" class="dz-col">
                <b>{{ column.title || column.field }}</b>
                <em>{{ column.field }}<template v-if="column.render"> · {{ column.render }}</template></em>
                <button class="dz-x" type="button" @click.stop="removeColumn(index)">×</button>
              </div>
            </template>
          </div>

          <div class="dz-gap" :class="{ 'is-hot': hot === `after-${bi}` }" @dragover.prevent="hot = `after-${bi}`" @drop.stop="onDrop('PageContainer', bi + 1)" @click.stop="onArmedClick('PageContainer')" />
        </section>

        <p v-if="!blocks.length" class="dz-canvas-empty">从左边把区块拖进来开始：查询区 / 工具栏 / 表格区 / 行操作 / 表单弹窗。</p>
      </main>

      <aside class="dz-pane dz-props">
        <p class="dz-group-head">{{ selected ? `${BLOCK_LABELS[selected.type] ?? selected.type}` : '页面' }}</p>

        <template v-if="selected && selected.type === 'DataTable'">
          <Field label="每页条数">
            <NumberInput :model-value="(selected.props.pageSize as number | null) ?? null" @update:model-value="setProp('pageSize', $event)" />
          </Field>
          <Field label="分页">
            <Switch :model-value="Boolean(selected.props.pagination)" @update:model-value="setProp('pagination', $event)" />
          </Field>
          <p class="dz-group-head">列</p>
          <button class="dz-palette" type="button" @click="addColumn">＋ 加一列</button>
          <div v-for="column in columns" :key="column.key" class="dz-col-edit">
            <Field label="字段">
              <Select :model-value="column.field" :options="fieldOptions" @update:model-value="updateColumn(column.key, { field: String($event) })" />
            </Field>
            <Field label="表头">
              <TextInput :model-value="column.title ?? ''" @update:model-value="updateColumn(column.key, { title: String($event) })" />
            </Field>
            <div class="dz-two">
              <Field label="宽度">
                <NumberInput :model-value="column.width ?? null" @update:model-value="updateColumn(column.key, { width: $event as number | null })" />
              </Field>
              <Field label="渲染">
                <Select :model-value="column.render ?? 'text'" :options="renderOptions" @update:model-value="updateColumn(column.key, { render: String($event) })" />
              </Field>
            </div>
          </div>
        </template>

        <template v-else-if="selected">
          <Field v-for="name in visibleProps" :key="name" :label="PROP_LABELS[name] ?? name">
            <Switch
              v-if="name === 'required' || name === 'danger' || name === 'pagination' || name === 'sortable'"
              :model-value="Boolean(selected.props[name])"
              @update:model-value="setProp(name, $event)"
            />
            <Select
              v-else-if="name === 'field'"
              :model-value="String(selected.props.field ?? '')"
              :options="fieldOptions"
              placeholder="选一个模型字段"
              @update:model-value="setProp('field', $event)"
            />
            <Select
              v-else-if="name === 'queryType'"
              :model-value="String(selected.props.queryType ?? '=')"
              :options="queryTypeOptions"
              @update:model-value="setProp('queryType', $event)"
            />
            <Select
              v-else-if="name === 'action'"
              :model-value="String(selected.props.action ?? '')"
              :options="actionOptions"
              placeholder="选一个动作"
              @update:model-value="setProp('action', $event)"
            />
            <Select
              v-else-if="name === 'variant'"
              :model-value="String(selected.props.variant ?? '')"
              :options="variantOptions"
              @update:model-value="setProp('variant', $event)"
            />
            <Select
              v-else-if="name === 'align'"
              :model-value="String(selected.props.align ?? '')"
              :options="alignOptions"
              @update:model-value="setProp('align', $event)"
            />
            <NumberInput
              v-else-if="name === 'columns' || name === 'gap' || name === 'priority' || name === 'pageSize'"
              :model-value="(selected.props[name] as number | null) ?? null"
              @update:model-value="setProp(name, $event)"
            />
            <TextInput v-else :model-value="String(selected.props[name] ?? '')" @update:model-value="setProp(name, String($event))" />
          </Field>
        </template>

        <div v-else-if="isPage" class="dz-page-edit">
          <Field label="页面标题">
            <TextInput :model-value="String(root.props.title ?? '')" @update:model-value="act(() => store.setTitle(String($event)))" />
          </Field>
          <Field label="页面说明">
            <Textarea :model-value="String(root.props.desc ?? '')" :rows="3" @update:model-value="act(() => store.setDesc(String($event)))" />
          </Field>
        </div>

        <p v-if="armed" class="dz-armed">已拿起「{{ armed }}」，点画布上的容器放下去。放错地方会当场说明为什么。</p>

        <div class="dz-foot">
          <button class="dz-mini" type="button" :disabled="!store.canUndo" @click="act(() => store.undo())">撤销</button>
          <button class="dz-mini" type="button" :disabled="!store.canRedo" @click="act(() => store.redo())">重做</button>
        </div>
        <p class="dz-hint">这一页用到 {{ referenced.length }} 个字段：{{ referenced.join('、') || '（还没有）' }}</p>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.designer {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 12px;
}

.dz-head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  background: var(--surface-solid);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.dz-back {
  background: none;
  border: 0;
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 13px;
}

.dz-back:hover {
  color: var(--ink);
}

.dz-title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-right: auto;
}

.dz-sub {
  color: var(--ink-faint);
  font-size: 12px;
}

.dz-body {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr) 300px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.dz-pane {
  overflow: auto;
  padding: 12px;
  background: var(--surface-solid);
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.dz-group-head {
  margin: 0 0 8px;
  color: var(--ink-muted);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.dz-palette {
  display: block;
  width: 100%;
  margin-bottom: 6px;
  padding: 7px 9px;
  text-align: left;
  cursor: grab;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--float-bg);
  color: var(--ink);
  font-size: 13px;
}

.dz-palette:hover {
  border-color: var(--line-strong);
  background: var(--hover-bg);
}

.dz-canvas {
  background: var(--canvas);
}

.dz-pagehead {
  display: block;
  width: 100%;
  margin-bottom: 10px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  border: 1px dashed var(--line);
  border-radius: var(--r-md);
  background: none;
  color: var(--ink);
}

.dz-pagehead em {
  display: block;
  color: var(--ink-faint);
  font-size: 12px;
  font-style: normal;
}

.dz-block {
  margin-bottom: 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: var(--surface-solid);
}

.dz-block-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  cursor: pointer;
  border-bottom: 1px solid var(--line);
  font-size: 13px;
}

.dz-count {
  margin-left: auto;
  color: var(--ink-faint);
  font-size: 12px;
}

.dz-x {
  border: 0;
  background: none;
  color: var(--ink-faint);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.dz-x:hover {
  color: var(--red);
}

.dz-cells {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 42px;
  padding: 8px 10px;
}

.dz-cells.is-hot {
  background: var(--blue-soft);
}

.dz-cell,
.dz-col {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 8px;
  cursor: pointer;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--float-bg);
  font-size: 12px;
}

.dz-cell.is-active,
.dz-pagehead.is-active,
.dz-block-head:hover {
  border-color: var(--blue);
}

.dz-col em {
  color: var(--ink-faint);
  font-style: normal;
}

.dz-empty,
.dz-tail {
  color: var(--ink-faint);
  font-size: 12px;
}

.dz-gap {
  height: 6px;
  border-radius: var(--r-sm);
}

.dz-gap.is-hot {
  background: var(--blue-soft);
}

.dz-canvas-empty {
  padding: 30px 0;
  text-align: center;
  color: var(--ink-faint);
  font-size: 13px;
}

.dz-page-edit {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.dz-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.dz-col-edit {
  margin-bottom: 10px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
}

.dz-foot {
  display: flex;
  gap: 6px;
  margin-top: 12px;
}

.dz-mini {
  padding: 5px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--float-bg);
  color: var(--ink);
  cursor: pointer;
  font-size: 12px;
}

.dz-mini:disabled {
  color: var(--ink-faint);
  cursor: not-allowed;
}

.dz-armed {
  margin: 12px 0 0;
  padding: 6px 8px;
  border-left: 2px solid var(--blue);
  color: var(--ink-soft);
  font-size: 12px;
}

.dz-palette em {
  color: var(--blue);
  font-style: normal;
}

.dz-cells.is-armed {
  outline: 1px dashed var(--line-strong);
  outline-offset: -3px;
  cursor: copy;
}

.dz-hint {
  margin: 10px 0 0;
  color: var(--ink-faint);
  font-size: 12px;
}
</style>
