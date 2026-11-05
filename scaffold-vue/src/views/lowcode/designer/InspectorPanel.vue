<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import IconButton from '@yuanda-lowcode-ai/scaffold-ui/components/ui/IconButton.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import SectionLabel from '@yuanda-lowcode-ai/scaffold-ui/components/ui/SectionLabel.vue'
import {
  Field, NumberInput, Select, Switch, TextInput, Textarea,
} from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { useSession } from './session.ts'
import {
  ACTION_LABELS, BLOCK_LABELS, PROP_LABELS, iconOf, propsOf, type UINode,
} from './design.ts'

/* 右栏：三段 Tab。
   面板只渲染 design.ts 的 PROPS 白名单里、且当前容器允许的那几格 ——
   多出来的一格在画布上完全能用，但生成器不认，那才是真的难查。 */
const session = useSession()
const tab = ref<'props' | 'style' | 'events'>('props')
// 顶层的 ref 绑定才会在模板里自动解包
const root = session.root

const node = session.selected
const isPage = session.isPage
const parent = session.selectedParent

const headTitle = computed(() => {
  const current = node.value
  if (!current) return '页面'
  return String(current.props.title ?? current.props.label ?? BLOCK_LABELS[current.type] ?? current.type)
})
const headCode = computed(() => node.value?.type ?? 'PageContainer')

/** 白名单按「这一格在哪个容器里」再收一遍：required 在查询区没意义，queryType 只在查询区成立 */
const visible = computed<string[]>(() => {
  const current = node.value
  if (!current) return []
  return propsOf(current.type).filter(name => {
    if (name === 'queryType') return parent.value?.type === 'SearchForm'
    if (name === 'required') return parent.value?.type === 'FormContainer'
    if (name === 'placeholder') return ['TextInput', 'Textarea', 'NumberInput', 'Select', 'DictSelect'].includes(current.type)
    return true
  })
})

const STYLE_PROPS = ['columns', 'gap', 'pagination', 'pageSize', 'priority']
const propTab = computed(() => visible.value.filter(name => !STYLE_PROPS.includes(name)))
const styleTab = computed(() => visible.value.filter(name => STYLE_PROPS.includes(name)))

const fields = session.fields
const fieldOptions = computed(() => fields.map(field => ({ label: `${field.fieldName}（${field.fieldCode}）`, value: field.fieldCode })))
const queryTypeOptions = ['=', '!=', '>', '>=', '<', '<=', 'LIKE', 'BETWEEN', 'IN'].map(value => ({ label: value, value }))
const actionOptions = Object.entries(ACTION_LABELS).map(([value, label]) => ({ label: `${label}（${value}）`, value }))
const variantOptions = [{ label: '主要', value: 'primary' }, { label: '普通', value: '' }]

function valueOf(name: string) {
  return node.value?.props[name] ?? ''
}

function setProp(name: string, value: unknown) {
  const current = node.value
  if (!current) return
  session.act(() => session.store.setProp(current.key, name, value))
}

/** 权限点由动作推导，只读展示：手写的那串与生成的菜单种子对不上时，表现是按钮永远灰 */
const derivedPermission = computed(() => {
  const current = node.value
  if (current?.type !== 'Button') return ''
  const action = String(current.props.action ?? '')
  if (!action) return ''
  const suffix = { create: 'add', edit: 'edit', delete: 'remove' }[action] ?? 'list'
  return `${session.page.modelCode}:${suffix}`
})

function jumpToPage() {
  session.pick(null)
  tab.value = 'props'
}

const columns = session.columns

function updateColumn(key: string, patch: Record<string, unknown>) {
  session.act(() => session.store.updateColumn(key, patch))
}

function addColumn() {
  session.act(() => session.store.addColumn({ field: fields[0]?.fieldCode ?? '', title: '新列' }))
}

function removeColumn(index: number) {
  session.act(() => session.store.removeColumn(index))
}

const renderOptions = ['text', 'money', 'tag', 'datetime'].map(value => ({ label: value, value }))
const isTable = computed(() => node.value?.type === 'DataTable')
</script>

<template>
  <aside class="dz-inspector m-solid" aria-label="组件与页面配置面板">
    <div class="dz-ins-head">
      <span class="dz-ins-mark" aria-hidden="true"><Icon :name="iconOf(headCode)" :size="16" /></span>
      <div class="dz-ins-title">
        <strong>{{ headTitle }}</strong>
        <span class="dz-ins-code">{{ headCode }}</span>
      </div>
      <button v-if="!isPage" type="button" class="dz-mini" @click="jumpToPage"><Icon name="sliders" :size="12" />页面设置</button>
      <IconButton name="sidebar" label="收起右侧面板" small @click="session.rightCollapsed.value = true" />
    </div>

    <Segmented
      v-model="tab"
      label="配置分类"
      :options="[{ label: '组件属性', value: 'props' }, { label: '样式属性', value: 'style' }, { label: '事件配置', value: 'events' }]"
    />

    <div class="dz-ins-body no-scrollbar">
      <!-- ── 组件属性 ── -->
      <template v-if="tab === 'props'">
        <template v-if="isPage">
          <section class="dz-sec">
            <SectionLabel title="基础信息" />
            <Field label="页面标题">
              <TextInput :model-value="String(root.props.title ?? '')" @update:model-value="session.act(() => session.store.setTitle(String($event)))" />
            </Field>
            <Field label="页面说明">
              <Textarea :model-value="String(root.props.desc ?? '')" :rows="3" @update:model-value="session.act(() => session.store.setDesc(String($event)))" />
            </Field>
          </section>
          <section class="dz-sec">
            <SectionLabel title="模型绑定" />
            <p class="dz-readonly"><span class="dz-code-chip">{{ session.page.modelCode }}</span>{{ session.page.modelName }}</p>
            <p class="dz-note">字段类型、字典与小数位都由模型给，设计里不再写第二份。</p>
          </section>
        </template>

        <template v-else-if="node">
          <section class="dz-sec">
            <SectionLabel :title="isTable ? '表格' : '基础信息'" />
            <Field v-for="name in propTab" :key="name" :label="PROP_LABELS[name] ?? name">
              <Switch v-if="name === 'required' || name === 'danger'" :model-value="Boolean(valueOf(name))" @update:model-value="setProp(name, $event)" />
              <Select
                v-else-if="name === 'field'"
                :model-value="String(valueOf('field'))"
                :options="fieldOptions"
                placeholder="选一个模型字段"
                @update:model-value="setProp('field', $event)"
              />
              <Select
                v-else-if="name === 'queryType'"
                :model-value="String(valueOf('queryType') || '=')"
                :options="queryTypeOptions"
                @update:model-value="setProp('queryType', $event)"
              />
              <Select
                v-else-if="name === 'action'"
                :model-value="String(valueOf('action'))"
                :options="actionOptions"
                placeholder="选一个动作"
                @update:model-value="setProp('action', $event)"
              />
              <Select
                v-else-if="name === 'variant'"
                :model-value="String(valueOf('variant'))"
                :options="variantOptions"
                @update:model-value="setProp('variant', $event)"
              />
              <NumberInput
                v-else-if="name === 'priority' || name === 'pageSize'"
                :model-value="(valueOf(name) as number | null) ?? null"
                @update:model-value="setProp(name, $event)"
              />
              <TextInput v-else :model-value="String(valueOf(name))" @update:model-value="setProp(name, String($event))" />
            </Field>
            <p v-if="!propTab.length" class="dz-note">{{ BLOCK_LABELS[node.type] ?? node.type }} 没有需要填的属性，容器结构本身就在画布上。</p>
          </section>

          <section v-if="derivedPermission" class="dz-sec">
            <SectionLabel title="权限点" />
            <p class="dz-readonly"><span class="dz-code-chip">{{ derivedPermission }}</span></p>
            <p class="dz-note">由动作推导，不接受手写：手写的那串与生成的菜单种子对不上时，表现是菜单里权限点齐着、页面上按钮永远灰。</p>
          </section>

          <section v-if="isTable" class="dz-sec">
            <SectionLabel title="列" />
            <button type="button" class="dz-add" @click="addColumn"><Icon name="plus" :size="13" />加一列</button>
            <div v-for="(column, index) in columns" :key="column.key" class="dz-col-card">
              <div class="dz-col-card-head">
                <b>{{ column.title || column.field }}</b>
                <button type="button" class="dz-tree-x" aria-label="移掉这一列" title="移掉这一列" @click="removeColumn(index)">×</button>
              </div>
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
          </section>
        </template>
      </template>

      <!-- ── 样式属性 ── -->
      <template v-else-if="tab === 'style'">
        <section class="dz-sec">
          <SectionLabel title="布局" />
          <Field v-for="name in styleTab" :key="name" :label="PROP_LABELS[name] ?? name">
            <Switch v-if="name === 'pagination'" :model-value="Boolean(valueOf(name))" @update:model-value="setProp(name, $event)" />
            <NumberInput v-else :model-value="(valueOf(name) as number | null) ?? null" @update:model-value="setProp(name, $event)" />
          </Field>
          <p v-if="!styleTab.length" class="dz-note">{{ isPage ? '页面' : (BLOCK_LABELS[node!.type] ?? node!.type) }} 没有可调的布局属性。</p>
        </section>
        <p class="dz-note is-foot">
          圆角、内边距与表面材质不在这格里 —— 生成器产不出这些属性，放进来只会让画布与产物各说一套。
          表格行高密度是看的人的外观设置，也不是这份设计的内容。
        </p>
      </template>

      <!-- ── 事件配置 ── -->
      <template v-else>
        <section class="dz-sec">
          <SectionLabel title="组件交互事件" />
          <p class="dz-note">
            第一期不在这份设计里存事件：按钮的行为由「动作」推导（新增 / 编辑 / 删除 / 导出 / 刷新各对应生成物里一处确定的接线），
            查询与分页由 <span class="dz-code-chip">SearchForm</span> 与 <span class="dz-code-chip">DataTable</span> 自带。
            自定义事件要等 <span class="dz-code-chip">design_json</span> 升到 1.1、后端校验与生成器模板同时接得住的那一天。
          </p>
        </section>
      </template>

      <p v-if="session.armed.value" class="dz-armed">已拿起「{{ session.armed.value }}」，点画布上的容器放下去。放错地方会当场说明为什么。</p>

      <footer class="dz-ins-foot">
        <span class="dz-ins-foot-text">这一页用到 <b class="tnum">{{ session.referenced.value.length }}</b> 个字段</span>
        <span class="dz-ins-fields">{{ session.referenced.value.join('、') || '（还没有）' }}</span>
      </footer>
    </div>
  </aside>
</template>

<style scoped>
.dz-inspector {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
}

.dz-ins-head {
  display: flex;
  align-items: center;
  gap: 9px;
}

.dz-ins-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: none;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--canvas);
  color: var(--ink-muted);
}

.dz-ins-title {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.dz-ins-title strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 660;
}

.dz-ins-code {
  color: var(--ink-faint);
  font-family: var(--font-mono);
  font-size: 11px;
}

.dz-ins-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin-right: -4px;
  padding-right: 4px;
}

.dz-sec {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 原型的小节标题前置一颗带光环的青柠点；平台 SectionLabel 本身不带，这里按设计器语汇补上 */
.dz-sec :deep(.section-mark-title)::before {
  content: "";
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 8px;
  vertical-align: 1px;
  border-radius: 999px;
  background: var(--lime);
  box-shadow: 0 0 0 5px var(--lime-soft);
}

.dz-sec :deep(.section-mark-title) {
  font-size: 14px;
}

.dz-note {
  margin: 0;
  color: var(--ink-faint);
  font-size: 11.5px;
  line-height: 1.75;
}

.dz-note.is-foot {
  padding: 9px 11px;
  border-left: 2px solid var(--line-strong);
  border-radius: 0 var(--r-xs) var(--r-xs) 0;
  background: var(--hover-bg);
  color: var(--ink-soft);
}

.dz-readonly {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  color: var(--ink-muted);
  font-size: 12.5px;
}

.dz-code-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 7px;
  border-radius: 6px;
  background: rgba(46, 58, 62, .06);
  color: var(--ink-muted);
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  font-size: 11.5px;
}

:root[data-theme="dark"] .dz-code-chip {
  background: rgba(255, 255, 255, .08);
}

.dz-two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.dz-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  padding: 0 10px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--r-sm);
  background: none;
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 12px;
}

.dz-add:hover {
  border-color: var(--blue);
  color: var(--blue);
}

.dz-col-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid var(--line);
  border-radius: var(--r-sm);
  background: var(--float-bg);
}

.dz-col-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12.5px;
}

.dz-tree-x {
  width: 18px;
  height: 18px;
  border: 0;
  border-radius: 5px;
  background: none;
  color: var(--ink-faint);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
}

.dz-tree-x:hover {
  color: var(--red);
}

.dz-mini {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 8px;
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

.dz-armed {
  margin: 0;
  padding: 6px 8px;
  border-left: 2px solid var(--blue);
  color: var(--ink-soft);
  font-size: 12px;
}

.dz-ins-foot {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 9px;
  border-top: 1px solid var(--line);
}

.dz-ins-foot-text {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.dz-ins-foot-text b {
  color: var(--ink);
  font-weight: 700;
}

.dz-ins-fields {
  color: var(--ink-faint);
  font-family: var(--font-mono);
  font-size: 10.5px;
  line-height: 1.6;
  word-break: break-all;
}
</style>
