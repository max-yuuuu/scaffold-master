<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import { usePrototype } from './context.ts'
import { AVAILABLE_TRIGGERS_BY_KIND, EVENT_ACTION_OPTIONS, KIND_ICON, type CanvasNode, type TableDensity } from './data.ts'

/* 右栏：组件级与页面级共用同三段 Tab，切换选中对象时面板头与格子一起换。
   每一格都直接写回节点树（commit → 撤销栈 + 脏计数），所以这里没有第二套状态。 */
const proto = usePrototype()

const node = proto.selectedNode
const isPage = computed(() => !node.value)

const headTitle = computed(() => (node.value ? node.value.name : proto.pageConfig.name))
const headCode = computed(() => (node.value ? node.value.code : proto.pageConfig.code))
const headIcon = computed(() => (node.value ? KIND_ICON[node.value.kind] : 'layers'))

const RADIUS_OPTIONS = [8, 12, 16, 20, 24].map(step => ({ label: `${step}`, value: step }))
const SURFACE_OPTIONS = [{ label: '实色阅读面', value: 'solid' }, { label: '珍珠玻璃面', value: 'glass' }, { label: '轻量浅底面', value: 'subtle' }]

const triggers = computed(() => (node.value ? AVAILABLE_TRIGGERS_BY_KIND[node.value.kind] : AVAILABLE_TRIGGERS_BY_KIND.page) || [])
const actionLabel = (value: string) => EVENT_ACTION_OPTIONS.find(option => option.value === value)?.label ?? value

function patch(updater: (item: CanvasNode) => CanvasNode) {
  if (node.value) proto.updateNode(node.value.id, updater)
}

function setName(value: string) {
  patch(item => ({ ...item, name: value }))
}

function setProp(key: string, value: unknown) {
  patch(item => ({ ...item, props: { ...item.props, [key]: value } }))
}

function setField(key: keyof CanvasNode, value: unknown) {
  patch(item => ({ ...item, [key]: value }))
}

function setPage(key: string, value: unknown) {
  (proto.pageConfig as Record<string, unknown>)[key] = value
}

function updateFilterField(index: number, key: string, value: unknown) {
  patch(item => {
    const fields = [...(item.props.filterFields || [])]
    fields[index] = { ...fields[index], [key]: value }
    return { ...item, props: { ...item.props, filterFields: fields } }
  })
}

function addFilterField() {
  patch(item => ({
    ...item,
    props: { ...item.props, filterFields: [...(item.props.filterFields || []), { label: '新筛选项', fieldKey: `field${(item.props.filterFields?.length ?? 0) + 1}`, placeholder: '', widthPx: 200 }] },
  }))
}

function removeFilterField(index: number) {
  patch(item => ({ ...item, props: { ...item.props, filterFields: (item.props.filterFields || []).filter((_, i) => i !== index) } }))
}

function toggleEventEnabled(id: string) {
  if (node.value) {
    patch(item => ({ ...item, events: (item.events || []).map(event => (event.id === id ? { ...event, enabled: !event.enabled } : event)) }))
  } else {
    proto.pageConfig.events = proto.pageConfig.events.map(event => (event.id === id ? { ...event, enabled: !event.enabled } : event))
  }
}

function jumpToPage() {
  proto.selectedNodeId.value = null
  proto.inspectorTab.value = 'props'
}
</script>

<template>
  <aside class="pf-pane" aria-label="组件与页面配置面板">
    <div class="pf-ins-head">
      <span class="pf-ins-mark"><Icon :name="headIcon" :size="15" /></span>
      <div class="pf-ins-title">
        <span class="pf-ins-name">
          <strong>{{ headTitle }}</strong>
          <span v-if="node && node.locked" class="lock" title="组件已锁定"><Icon name="lock" :size="12" /></span>
        </span>
        <span class="pf-ins-code">{{ headCode }}</span>
      </div>
      <span v-if="isPage" class="pf-tag tone-info"><i />页面级</span>
      <button v-else type="button" class="pf-mini" title="切换到页面级配置" @click="jumpToPage"><Icon name="layers" :size="12" />页面设置</button>
      <button type="button" class="pf-icon-btn" aria-label="收起右侧配置面板" title="收起右侧面板" @click="proto.rightCollapsed.value = true">
        <Icon name="panelRightClose" :size="14" />
      </button>
    </div>

    <div class="pf-ins-tabs">
      <Segmented
        v-model="proto.inspectorTab.value"
        class="pf-fluid"
        label="配置分类"
        :options="[{ label: isPage ? '页面属性' : '组件属性', value: 'props' }, { label: '样式属性', value: 'style' }, { label: '事件配置', value: 'events' }]"
      />
    </div>

    <div class="pf-pane-body pf-ins-body">
      <!-- ══ 组件级 ══ -->
      <template v-if="node">
        <template v-if="proto.inspectorTab.value === 'props'">
          <section class="pf-sec">
            <p class="pf-section">基础信息</p>
            <label class="pf-field"><span>节点名称</span><input class="pf-input" type="text" :value="node.name" @input="setName(($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>标题文案</span><input class="pf-input" type="text" :value="node.props.title" @input="setProp('title', ($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>辅助说明 / 占位提示</span><input class="pf-input" type="text" :value="node.props.subtitle" @input="setProp('subtitle', ($event.target as HTMLInputElement).value)"></label>
          </section>

          <section v-if="node.kind === 'header' || node.kind === 'button'" class="pf-sec">
            <p class="pf-section">按钮文案</p>
            <div class="pf-two">
              <label class="pf-field"><span>主按钮</span><input class="pf-input" type="text" :value="node.props.primaryActionText" @input="setProp('primaryActionText', ($event.target as HTMLInputElement).value)"></label>
              <label class="pf-field"><span>次级按钮</span><input class="pf-input" type="text" :value="node.props.secondaryActionText" @input="setProp('secondaryActionText', ($event.target as HTMLInputElement).value)"></label>
            </div>
          </section>

          <section v-if="node.kind === 'readout'" class="pf-sec">
            <p class="pf-section">读数与铭牌参数</p>
            <div class="pf-two">
              <label class="pf-field"><span>主角读数值</span><input class="pf-input mono" type="text" :value="node.props.heroMetricValue" @input="setProp('heroMetricValue', ($event.target as HTMLInputElement).value)"></label>
              <label class="pf-field"><span>环比读数</span><input class="pf-input mono" type="text" :value="node.props.heroMetricDelta" @input="setProp('heroMetricDelta', ($event.target as HTMLInputElement).value)"></label>
            </div>
            <div class="pf-switch-line">
              <span>显示慢操作斜线区间</span>
              <button type="button" class="pf-switch" role="switch" :aria-checked="node.props.showHatchBar !== false" @click="setProp('showHatchBar', !(node.props.showHatchBar !== false))"><i /></button>
            </div>
            <div class="pf-switch-line">
              <span>显示工位规格铭牌</span>
              <button type="button" class="pf-switch" role="switch" :aria-checked="node.props.showSpecPlate !== false" @click="setProp('showSpecPlate', !(node.props.showSpecPlate !== false))"><i /></button>
            </div>
          </section>

          <section v-if="node.kind === 'filter'" class="pf-sec">
            <p class="pf-section">
              筛选项
              <button type="button" class="pf-add" @click="addFilterField"><Icon name="plus" :size="11" />加一项</button>
            </p>
            <div v-for="(field, index) in node.props.filterFields || []" :key="index" class="pf-filter-edit">
              <div class="pf-filter-edit-head">
                <span class="mono">第 {{ index + 1 }} 项</span>
                <button type="button" class="pf-row-x is-shown" aria-label="删掉这一项" title="删掉这一项" @click="removeFilterField(index)">×</button>
              </div>
              <div class="pf-two">
                <label class="pf-field"><span>标签</span><input class="pf-input" type="text" :value="field.label" @input="updateFilterField(index, 'label', ($event.target as HTMLInputElement).value)"></label>
                <label class="pf-field"><span>字段键</span><input class="pf-input mono" type="text" :value="field.fieldKey" @input="updateFilterField(index, 'fieldKey', ($event.target as HTMLInputElement).value)"></label>
              </div>
              <label class="pf-field"><span>占位提示</span><input class="pf-input" type="text" :value="field.placeholder" @input="updateFilterField(index, 'placeholder', ($event.target as HTMLInputElement).value)"></label>
              <label class="pf-field"><span>宽度 px</span><input class="pf-input mono" type="number" :value="field.widthPx" @input="updateFilterField(index, 'widthPx', Number(($event.target as HTMLInputElement).value))"></label>
            </div>
          </section>

          <section v-if="node.kind === 'table'" class="pf-sec">
            <p class="pf-section">表格默认档位</p>
            <Segmented
              :model-value="(node.props.density || 'standard') as TableDensity"
              class="pf-fluid"
              label="表格行高密度"
              :options="[{ label: '紧凑 40', value: 'compact' }, { label: '标准 48', value: 'standard' }, { label: '宽松 56', value: 'relaxed' }]"
              @update:model-value="setProp('density', $event)"
            />
          </section>

          <section class="pf-sec">
            <p class="pf-section">数据与权限绑定</p>
            <label class="pf-field"><span>绑定数据源标识</span><input class="pf-input mono" type="text" :value="node.dataSource" @input="setField('dataSource', ($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>刷新周期</span><input class="pf-input mono" type="text" :value="node.refreshInterval" @input="setField('refreshInterval', ($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>权限校验标识（RBAC）</span><input class="pf-input mono" type="text" :value="node.permissionKey" @input="setField('permissionKey', ($event.target as HTMLInputElement).value)"></label>
          </section>
        </template>

        <!-- 样式属性 -->
        <template v-else-if="proto.inspectorTab.value === 'style'">
          <section class="pf-sec">
            <p class="pf-section">容器圆角与间距</p>
            <div class="pf-label-line"><span>容器圆角阶梯</span><span class="mono">{{ node.radiusPx }}<em>px</em></span></div>
            <Segmented
              :model-value="node.radiusPx"
              class="pf-fluid"
              label="容器圆角阶梯"
              :options="RADIUS_OPTIONS"
              @update:model-value="setField('radiusPx', $event)"
            />
            <div class="pf-two">
              <label class="pf-field"><span>容器内边距</span><input class="pf-input mono" type="number" :value="node.paddingPx" @input="setField('paddingPx', Number(($event.target as HTMLInputElement).value))"></label>
              <label class="pf-field"><span>底部外边距</span><input class="pf-input mono" type="number" :value="node.marginBottomPx ?? 0" @input="setField('marginBottomPx', Number(($event.target as HTMLInputElement).value))"></label>
            </div>
          </section>

          <section class="pf-sec">
            <p class="pf-section">卡片表面材质</p>
            <Segmented
              :model-value="(node.surfaceStyle || 'solid') as string"
              class="pf-fluid"
              label="卡片表面材质"
              :options="SURFACE_OPTIONS"
              @update:model-value="setField('surfaceStyle', $event)"
            />
          </section>
        </template>

        <!-- 事件配置 -->
        <template v-else>
          <section class="pf-sec">
            <p class="pf-section">
              组件交互事件
              <button type="button" class="pf-add" @click="proto.openEventDrawer('node')"><Icon name="plus" :size="11" />新增事件</button>
            </p>
            <div v-for="event in node.events || []" :key="event.id" class="pf-event-card" @click="proto.openEventDrawer('node', event)">
              <div class="row1">
                <Icon name="activity" :size="13" />
                <b>{{ event.triggerName }}</b>
                <span class="pf-tag" :class="event.enabled ? 'tone-ok' : 'tone-neutral'" style="margin-left:auto"><i />{{ event.enabled ? '已启用' : '已停用' }}</span>
              </div>
              <div class="row2">
                <span>{{ actionLabel(event.actionType) }} · {{ event.targetDesc }}</span>
                <span class="pf-chip" @click.stop="toggleEventEnabled(event.id)">{{ event.triggerCode }}</span>
              </div>
            </div>
            <p v-if="!(node.events || []).length" class="pf-sec-empty">还没有事件绑定。</p>
          </section>
        </template>
      </template>

      <!-- ══ 页面级 ══ -->
      <template v-else>
        <template v-if="proto.inspectorTab.value === 'props'">
          <section class="pf-sec">
            <p class="pf-section">页面信息</p>
            <label class="pf-field"><span>页面名称</span><input class="pf-input" type="text" :value="proto.pageConfig.name" @input="setPage('name', ($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>页面编码</span><input class="pf-input mono" type="text" :value="proto.pageConfig.code" @input="setPage('code', ($event.target as HTMLInputElement).value)"></label>
            <label class="pf-field"><span>页面说明</span><textarea class="pf-input is-textarea" rows="3" :value="proto.pageConfig.description" @input="setPage('description', ($event.target as HTMLInputElement).value)" /></label>
          </section>
          <section class="pf-sec">
            <p class="pf-section">访问控制</p>
            <label class="pf-field"><span>页面权限标识（RBAC）</span><input class="pf-input mono" type="text" :value="proto.pageConfig.permissionKey" @input="setPage('permissionKey', ($event.target as HTMLInputElement).value)"></label>
          </section>
        </template>

        <template v-else-if="proto.inspectorTab.value === 'style'">
          <section class="pf-sec">
            <p class="pf-section">画布留白与节奏</p>
            <div class="pf-two">
              <label class="pf-field"><span>左右留白 px</span><input class="pf-input mono" type="number" :value="proto.pageConfig.paddingX" @input="setPage('paddingX', Number($event))"></label>
              <label class="pf-field"><span>上下留白 px</span><input class="pf-input mono" type="number" :value="proto.pageConfig.paddingY" @input="setPage('paddingY', Number($event))"></label>
            </div>
            <label class="pf-field"><span>区块间距 px</span><input class="pf-input mono" type="number" :value="proto.pageConfig.blockGap" @input="setPage('blockGap', Number($event))"></label>
            <div class="pf-switch-line">
              <span>显示参考点阵</span>
              <button type="button" class="pf-switch" role="switch" :aria-checked="proto.pageConfig.showDots" @click="proto.pageConfig.showDots = !proto.pageConfig.showDots"><i /></button>
            </div>
          </section>
          <section class="pf-sec">
            <p class="pf-section">画布底色</p>
            <div class="pf-segmented is-fluid">
              <button
                v-for="option in [{ v: 'default', l: '仪器纸面' }, { v: 'pure', l: '纯白' }, { v: 'warm', l: '暖调' }] as const"
                :key="option.v"
                type="button"
                :class="['pf-seg-btn', proto.pageConfig.canvasBgTone === option.v && 'is-on']"
                @click="proto.pageConfig.canvasBgTone = option.v"
              >{{ option.l }}</button>
            </div>
          </section>
        </template>

        <template v-else>
          <section class="pf-sec">
            <p class="pf-section">
              页面级事件
              <button type="button" class="pf-add" @click="proto.openEventDrawer('page')"><Icon name="plus" :size="11" />新增事件</button>
            </p>
            <div v-for="event in proto.pageConfig.events" :key="event.id" class="pf-event-card" @click="proto.openEventDrawer('page', event)">
              <div class="row1">
                <Icon name="activity" :size="13" />
                <b>{{ event.triggerName }}</b>
                <span class="pf-tag" :class="event.enabled ? 'tone-ok' : 'tone-neutral'" style="margin-left:auto"><i />{{ event.enabled ? '已启用' : '已停用' }}</span>
              </div>
              <div class="row2">
                <span>{{ actionLabel(event.actionType) }} · {{ event.targetDesc }}</span>
                <span class="pf-chip">{{ event.triggerCode }}</span>
              </div>
            </div>
          </section>
        </template>
      </template>

      <p class="pf-ins-note">
        这一版是 1:1 视觉稿：面板里的每一格都会即时改画布，但<span class="mono">design_json</span> 还产不出这些 kind 与事件，保存与发布走的是「旧版（已接数据）」那条路。
      </p>
    </div>
  </aside>
</template>

<style scoped>
.pf-ins-head { display: flex; align-items: center; gap: 8px; padding: 14px 14px 0; }

.pf-ins-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex: none;
  border-radius: var(--r-xs);
  background: var(--blue-soft);
  color: var(--blue);
}

.pf-ins-title { display: flex; flex-direction: column; gap: 1px; flex: 1; min-width: 0; }
.pf-ins-name { display: flex; align-items: center; gap: 6px; min-width: 0; }
.pf-ins-name strong { font-size: 13.5px; font-weight: 650; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pf-ins-name .lock { display: inline-flex; flex: none; color: var(--blue); }
.pf-ins-code { color: var(--ink-soft); font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.pf-ins-tabs { padding: 12px 14px 14px; border-bottom: 1px solid var(--line); }
.pf-ins-body { display: flex; flex-direction: column; gap: 20px; }
.pf-sec { display: flex; flex-direction: column; gap: 12px; }
.pf-sec + .pf-sec { padding-top: 16px; border-top: 1px solid var(--line); }
.pf-two { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.pf-sec-empty { margin: 0; color: var(--ink-faint); font-size: 12px; }

.pf-label-line { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: var(--ink-muted); }
.pf-label-line .mono { font-size: 12.5px; font-weight: 640; color: var(--ink); }
.pf-label-line em { margin-left: 2px; font-size: 10.5px; font-style: normal; color: var(--ink-faint); }

.pf-switch-line { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 4px 0; font-size: 12.5px; color: var(--ink-muted); }

.pf-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 22px;
  margin-left: auto;
  padding: 0 8px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--surface-solid);
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 11.5px;
}

.pf-add:hover { border-color: var(--blue); color: var(--blue); }

.pf-filter-edit { display: flex; flex-direction: column; gap: 8px; padding: 10px; border: 1px solid var(--line); border-radius: var(--r-sm); background: var(--float-bg); }
.pf-filter-edit-head { display: flex; align-items: center; justify-content: space-between; font-size: 11.5px; color: var(--ink-faint); }
.pf-row-x { width: 18px; height: 18px; border: 0; border-radius: 5px; background: none; color: var(--ink-faint); cursor: pointer; font-size: 14px; line-height: 1; }
.pf-row-x:hover { color: var(--red); }
.pf-row-x.is-shown { opacity: 1; }

.pf-event-card .row1 { display: flex; align-items: center; gap: 8px; }
.pf-event-card .row1 b { font-size: 12.5px; font-weight: 620; }
.pf-event-card .row2 { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--ink-faint); font-size: 11.5px; }

.pf-mini {
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

.pf-mini:hover { border-color: var(--line-strong); color: var(--ink); }

.pf-ins-note {
  padding: 9px 11px;
  border-left: 2px solid var(--line-strong);
  border-radius: 0 var(--r-xs) var(--r-xs) 0;
  background: var(--hover-bg);
  color: var(--ink-soft);
  font-size: 11.5px;
  line-height: 1.75;
}

.pf-ins-note .mono { font-family: var(--font-mono); }
.pf-input.is-textarea { height: auto; padding: 9px 11px; line-height: 1.6; }
</style>
