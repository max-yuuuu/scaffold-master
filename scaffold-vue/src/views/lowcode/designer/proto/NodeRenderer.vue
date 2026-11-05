<script setup lang="ts">
import { computed, ref } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import Segmented from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Segmented.vue'
import { TONE_VAR, gridColumnIndices, type CanvasNode, type TableDensity, type TableRecord } from './data.ts'

/**
 * 画布节点的所见即所得渲染：1:1 对应 CanvasNodeRenderer.tsx 的 20 种 kind。
 *
 * 值一律是演示数据 —— 这是静态稿，画布上填不进真东西；编辑态里输入框保持 readonly，
 * 只有「预览运行态」才放开，与原型同一口径。
 */
const props = defineProps<{
  node: CanvasNode
  selected?: boolean
  preview?: boolean
  dragging?: boolean
  depth?: number
  records: TableRecord[]
  /** 当前高亮中的可放置区域；只有栅格列与插槽腔体会被标出来 */
  dropTarget?: { id: string, colIndex?: number } | null
}>()

const emit = defineEmits<{
  (e: 'select', id: string): void
  (e: 'density', id: string, density: TableDensity): void
  (e: 'toggle-row', id: string): void
  (e: 'toggle-all'): void
  (e: 'zone-over', id: string, colIndex: number | undefined, event: DragEvent): void
  (e: 'zone-leave', id: string, colIndex: number | undefined, event: DragEvent): void
  (e: 'zone-drop', id: string, colIndex: number | undefined, event: DragEvent): void
}>()

/** 只有 dropZones() 认得的区域才挂拖放：在表格、标题这些身上松手不该有任何反应 */
const zoneActive = (id: string, colIndex?: number) =>
  props.dropTarget?.id === id && props.dropTarget.colIndex === colIndex

const tone = (kind: TableRecord['status']) => kind

/**
 * 密度与勾选态必须是 computed：写成 setup 时算一次的普通常量，改完 props.density
 * 行高不会跟着变 —— 面板上按下去有反馈（撤销栈确实多了一步），画布却一动不动。
 */
const table = computed(() => {
  const density = (props.node.props.density || 'standard') as TableDensity
  return {
    density,
    rowHeight: density === 'compact' ? 40 : density === 'relaxed' ? 56 : 48,
    cellPx: density === 'compact' ? 14 : density === 'relaxed' ? 18 : 16,
  }
})

const allChecked = computed(() => props.records.length > 0 && props.records.every(row => row.checked))

/** 单元格左右内距跟着密度档位走（紧凑 14 / 标准 16 / 宽松 18），与原型同一组数 */
const cellVars = computed(() => ({ '--pf-cell-px': `${table.value.cellPx}px` }))

const TOPOLOGY = [
  { step: '正负极合浆涂布', code: 'STEP-COAT-A', tone: 'sand' as const, metric: '99.81%', stateText: '节拍稳定' },
  { step: '注液与高温静置', code: 'STEP-INJ-B', tone: 'sky' as const, metric: '99.54%', stateText: '主工序激活', active: true },
  { step: '负压化成与分容', code: 'STEP-FORM-C', tone: 'mint' as const, metric: '99.42%', stateText: '实时采集中' },
]

const TAB_LABELS = ['批次明细', '电压曲线', '拦截记录']
const TAB_OPTIONS = TAB_LABELS.map(label => ({ label, value: label }))

const DENSITY_OPTIONS: { value: TableDensity, label: string }[] = [
  { value: 'compact', label: '紧凑 40' },
  { value: 'standard', label: '标准 48' },
  { value: 'relaxed', label: '宽松 56' },
]

/** 分段容器是画布上的一份预览，页签切换只活在这一格里，不写回节点树 */
const activeTab = ref(TAB_LABELS[0])
</script>

<template>
  <div class="pf-render">
    <!-- 1. 页面标题 -->
    <div v-if="node.kind === 'header'" class="pf-header">
      <div class="pf-header-main">
        <div class="pf-header-title">
          <h1>{{ node.props.title }}</h1>
          <span class="pf-tag tone-ok"><i />产线联机正常</span>
        </div>
        <p class="pf-header-desc">{{ node.props.subtitle }}</p>
      </div>
      <div class="pf-header-actions">
        <button type="button" class="pf-btn is-secondary">{{ node.props.secondaryActionText || '导出工艺留档' }}</button>
        <button type="button" class="pf-btn is-primary">{{ node.props.primaryActionText || '批量核准放行' }}</button>
      </div>
    </div>

    <!-- 2. 数值指标：主角大读数 + 刻度条 + 深色铭牌 + 只读读数 -->
    <div v-else-if="node.kind === 'readout'" class="pf-readout">
      <div class="pf-readout-top">
        <div class="pf-readout-heading">
          <span class="pf-section">{{ node.props.title }}</span>
          <span class="pf-sub">{{ node.props.subtitle }}</span>
        </div>
        <div v-if="node.props.showSpecPlate !== false" class="pf-plate">
          <span class="dim">工位铭牌</span>
          <span>ND-P3-FORM-02</span>
          <span class="dim">|</span>
          <span class="lime">采样周期 {{ node.refreshInterval }}</span>
        </div>
      </div>

      <div class="pf-readout-grid">
        <div class="pf-hero">
          <div class="pf-hero-top">
            <span class="pf-hero-label">实时直通良率读数</span>
            <span class="pf-tag tone-ok"><i />环比 {{ node.props.heroMetricDelta || '+0.28%' }}</span>
          </div>
          <div class="pf-hero-value">
            <span class="num">{{ node.props.heroMetricValue || '99.42' }}</span>
            <sup>{{ node.props.heroMetricUnit || '%' }}</sup>
          </div>
          <div class="pf-hero-foot">
            <span>在制电芯总数</span>
            <span class="mono">18,420 支</span>
          </div>
        </div>

        <div class="pf-readout-side">
          <div class="pf-meter">
            <div class="pf-meter-top">
              <span class="pf-meter-title">化成恒流充放电区间负荷（当前班次推进线）</span>
              <span class="pf-meter-now mono">现在 14:28 · 负荷 84.6%</span>
            </div>
            <div class="pf-bar">
              <span class="fill" style="width: 68%" />
              <span v-if="node.props.showHatchBar !== false" class="hatch" style="width: 16.6%" title="当前慢操作静置老化区间" />
              <span class="now" />
            </div>
            <div class="pf-ticks">
              <span>08:00</span><span>10:00</span><span>12:00</span><span class="now">14:28（当前柱）</span><span>16:00</span>
            </div>
          </div>

          <div class="pf-kv">
            <div><p class="k">OCV 压降标准差</p><p class="v mono">0.42 <sup>mV</sup></p></div>
            <div><p class="k">化成舱均温偏差</p><p class="v mono">±0.35 <sup>°C</sup></p></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 查询表单 -->
    <div v-else-if="node.kind === 'filter'" class="pf-filter">
      <div class="pf-filter-fields">
        <label v-for="field in node.props.filterFields || []" :key="field.fieldKey" class="pf-field" :style="{ width: `${field.widthPx}px` }">
          <span>{{ field.label }}</span>
          <input class="pf-input" type="text" :readonly="!preview" :placeholder="field.placeholder">
        </label>
      </div>
      <div class="pf-filter-actions">
        <button type="button" class="pf-btn is-ghost">重置条件</button>
        <button type="button" class="pf-btn is-secondary">执行查询</button>
      </div>
    </div>

    <!-- 4. 数据表格 -->
    <div v-else-if="node.kind === 'table'" class="pf-table-block">
      <div class="pf-table-head">
        <div>
          <span class="pf-section">{{ node.props.title }}</span>
          <p class="pf-sub">{{ node.props.subtitle }}</p>
        </div>
        <div class="pf-density">
          <span>行高密度</span>
          <Segmented
            :model-value="table.density"
            label="表格行高密度"
            :options="DENSITY_OPTIONS"
            @update:model-value="emit('density', node.id, $event as TableDensity)"
          />
        </div>
      </div>

      <div class="pf-table-wrap" :style="cellVars">
        <table class="pf-table">
          <thead>
            <tr>
              <th class="sticky-left">
                <button type="button" class="pf-checkbox" :aria-checked="allChecked" aria-label="全选所有批次行" @click.stop="emit('toggle-all')">
                  <Icon v-if="allChecked" name="check" :size="11" />
                </button>
              </th>
              <th>电芯批次与产线柜位</th>
              <th>产品分类</th>
              <th>当班责任人</th>
              <th>一次直通率</th>
              <th>化成耗时</th>
              <th>放行状态</th>
              <th>权限标识</th>
              <th class="sticky-right">工艺操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in records"
              :key="row.id"
              :style="{ height: `${table.rowHeight}px`, background: row.checked ? 'var(--blue-soft)' : undefined }"
            >
              <td class="sticky-left" :style="{ background: row.checked ? 'var(--blue-soft)' : 'var(--surface-solid)' }">
                <button type="button" class="pf-checkbox" :aria-checked="Boolean(row.checked)" :aria-label="`选择批次 ${row.batchCode}`" @click.stop="emit('toggle-row', row.id)">
                  <Icon v-if="row.checked" name="check" :size="11" />
                </button>
              </td>
              <td>
                <div class="mono strong">{{ row.batchCode }}</div>
                <div class="sub">{{ row.lineName }}</div>
              </td>
              <td>
                <span class="pf-cat" :style="{ background: TONE_VAR[row.categoryTone].bg, color: TONE_VAR[row.categoryTone].fg }">{{ row.categoryLabel }}</span>
              </td>
              <td>{{ row.operator }}</td>
              <td class="mono strong">{{ row.yieldRate }}</td>
              <td class="mono muted">{{ row.cycleMs }}</td>
              <td><span class="pf-tag" :class="`tone-${tone(row.status)}`"><i />{{ row.statusText }}</span></td>
              <td><span class="pf-chip">{{ row.permissionCode }}</span></td>
              <td class="sticky-right" :style="{ background: row.checked ? 'var(--blue-soft)' : 'var(--surface-solid)' }">
                <button type="button" class="pf-btn is-sm is-text" @click.stop>复核曲线</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pf-table-foot">
        <span>共 <b class="mono">{{ records.length }}</b> 个待审批次 · 已勾选 <b class="mono accent">{{ records.filter(r => r.checked).length }}</b> 项</span>
        <div class="pf-pager">
          <button type="button" class="pf-btn is-sm is-ghost" disabled>上一页</button>
          <span class="page">1 / 4</span>
          <button type="button" class="pf-btn is-sm is-ghost">下一页</button>
        </div>
      </div>
    </div>

    <!-- 5. 工序流向 -->
    <div v-else-if="node.kind === 'topology'" class="pf-topo">
      <div class="pf-topo-head">
        <span class="pf-section">{{ node.props.title }}</span>
        <span class="pf-sub">{{ node.props.subtitle }}</span>
      </div>
      <div class="pf-topo-grid">
        <div v-for="(step, idx) in TOPOLOGY" :key="step.code" :class="['pf-topo-card', step.active && 'is-active']">
          <div class="pf-topo-top">
            <span class="pf-cat" :style="{ background: TONE_VAR[step.tone].bg, color: TONE_VAR[step.tone].fg }">
              <i :style="{ background: TONE_VAR[step.tone].fg, boxShadow: `0 0 0 3px ${TONE_VAR[step.tone].bg}` }" />{{ step.step }}
            </span>
            <span class="pf-chip">{{ step.code }}</span>
          </div>
          <div class="pf-topo-metric">
            <span class="pf-sub">{{ step.stateText }}</span>
            <span class="mono big">{{ step.metric }}</span>
          </div>
          <div v-if="idx < 2" class="pf-topo-loss">
            <span>工序连线损耗</span><span class="mono">0.04%</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 6. 面板卡片 -->
    <div v-else-if="node.kind === 'card'" class="pf-card">
      <div class="pf-card-head">
        <span class="pf-section">{{ node.props.title || '面板卡片' }}</span>
        <span class="pf-sub">{{ node.props.subtitle }}</span>
      </div>
      <div class="pf-card-body">
        <p class="pf-card-text">带标题栏与内边距的标准内容面板，子组件拖进来就地渲染。</p>
      </div>
    </div>

    <!-- 7. 分段容器 -->
    <div v-else-if="node.kind === 'tabs'" class="pf-tabs">
      <Segmented v-model="activeTab" class="pf-fluid" label="分段容器页签" :options="TAB_OPTIONS" />
      <div class="pf-tabs-body"><span class="pf-sub">当前页签内容区：{{ activeTab }}</span></div>
    </div>

    <!-- 8. 栅格容器：两列各自可接收拖放 -->
    <div v-else-if="node.kind === 'grid'" class="pf-grid-block">
      <div class="pf-grid-head">
        <span class="pf-section">{{ node.props.title || '栅格容器' }}</span>
        <span class="pf-sub">两列均分 · 间距 16px</span>
      </div>
      <div class="pf-grid">
        <div
          v-for="colIndex in gridColumnIndices(node)"
          :key="colIndex"
          :class="['pf-grid-col', zoneActive(node.id, colIndex) && 'is-active']"
          @dragover="emit('zone-over', node.id, colIndex, $event)"
          @dragleave="emit('zone-leave', node.id, colIndex, $event)"
          @drop="emit('zone-drop', node.id, colIndex, $event)"
        >
          <template v-if="(node.columns?.[colIndex] || []).length">
            <NodeRenderer
              v-for="child in node.columns?.[colIndex] || []"
              :key="child.id"
              :node="child"
              :records="records"
              :preview="preview"
              :drop-target="dropTarget"
              @select="emit('select', $event)"
              @zone-over="(id, col, event) => emit('zone-over', id, col, event)"
              @zone-leave="(id, col, event) => emit('zone-leave', id, col, event)"
              @zone-drop="(id, col, event) => emit('zone-drop', id, col, event)"
            />
          </template>
          <span v-else class="pf-slot-hint">第 {{ colIndex + 1 }} 列 · 拖组件到这里</span>
        </div>
      </div>
    </div>

    <!-- 9. 插槽容器 -->
    <div
      v-else-if="node.kind === 'emptySlot'"
      :class="['pf-slotbox', zoneActive(node.id) && 'is-active']"
      @dragover="emit('zone-over', node.id, undefined, $event)"
      @dragleave="emit('zone-leave', node.id, undefined, $event)"
      @drop="emit('zone-drop', node.id, undefined, $event)"
    >
      <div v-if="(node.children || []).length" class="pf-slot-filled">
        <div class="pf-slot-head">
          <span class="pf-section">{{ node.props.title || '插槽容器' }}</span>
          <span class="pf-sub">可继续拖入组件</span>
        </div>
        <NodeRenderer
          v-for="child in node.children || []"
          :key="child.id"
          :node="child"
          :records="records"
          :preview="preview"
          :drop-target="dropTarget"
          @select="emit('select', $event)"
          @zone-over="(id, col, event) => emit('zone-over', id, col, event)"
          @zone-leave="(id, col, event) => emit('zone-leave', id, col, event)"
          @zone-drop="(id, col, event) => emit('zone-drop', id, col, event)"
        />
      </div>
      <div v-else class="pf-slot-empty">
        <span class="pf-slot-plus"><Icon name="plus" :size="18" /></span>
        <strong>{{ node.props.title || '待放置插槽容器' }}</strong>
        <p class="pf-sub">直接将组件拖拽到此容器内松手即可原地放入</p>
      </div>
    </div>

    <!-- 10. 分割线 -->
    <div v-else-if="node.kind === 'divider'" class="pf-divider">
      <span class="line" /><span class="pf-sub">{{ node.props.title || '区块分割线' }}</span><span class="line" />
    </div>

    <!-- 11–19. 基础控件 -->
    <label v-else-if="node.kind === 'input'" class="pf-field is-narrow">
      <span>{{ node.props.title || '文本输入框' }}</span>
      <input class="pf-input" type="text" :readonly="!preview" :placeholder="node.props.subtitle || '请输入内容...'">
    </label>

    <label v-else-if="node.kind === 'select'" class="pf-field is-narrow">
      <span>{{ node.props.title || '下拉选择' }}</span>
      <select class="pf-input"><option>默认选项一</option><option>备选选项二</option><option>备选选项三</option></select>
    </label>

    <div v-else-if="node.kind === 'switch'" class="pf-switch-row">
      <div>
        <strong>{{ node.props.title || '状态开关' }}</strong>
        <p class="pf-sub">{{ node.props.subtitle || '开启后自动同步状态' }}</p>
      </div>
      <span class="pf-switch" role="switch" aria-checked="true"><i /></span>
    </div>

    <div v-else-if="node.kind === 'tag'" class="pf-inline">
      <span class="pf-tag tone-ok"><i />正常运行</span>
      <span class="pf-tag tone-info"><i />处理中</span>
      <span class="pf-tag tone-danger"><i />异常告警</span>
      <span class="pf-tag tone-neutral"><i />待机未启用</span>
    </div>

    <div v-else-if="node.kind === 'button'" class="pf-inline">
      <button type="button" class="pf-btn is-primary">{{ node.props.primaryActionText || '主操作按钮' }}</button>
      <button type="button" class="pf-btn is-secondary">{{ node.props.secondaryActionText || '次级按钮' }}</button>
      <button type="button" class="pf-btn is-ghost">幽灵按钮</button>
      <button type="button" class="pf-btn is-sm is-danger">危险操作</button>
    </div>

    <label v-else-if="node.kind === 'number'" class="pf-field is-number">
      <span>{{ node.props.title || '数字输入' }}</span>
      <span class="pf-input-wrap">
        <input class="pf-input mono" type="number" :readonly="!preview" value="100">
        <sup>件</sup>
      </span>
    </label>

    <label v-else-if="node.kind === 'date'" class="pf-field is-date">
      <span>{{ node.props.title || '日期选择' }}</span>
      <input class="pf-input mono" type="date" :readonly="!preview" value="2026-04-19">
    </label>

    <div v-else-if="node.kind === 'checkbox'" class="pf-inline">
      <label v-for="(label, idx) in ['选项一（已启用）', '选项二', '选项三']" :key="label" class="pf-check-label">
        <span class="pf-checkbox" :aria-checked="idx === 0"><Icon v-if="idx === 0" name="check" :size="11" /></span>
        <span>{{ label }}</span>
      </label>
    </div>

    <div v-else-if="node.kind === 'pagination'" class="pf-table-foot">
      <span>共 <b class="mono">128</b> 条记录</span>
      <div class="pf-pager">
        <button type="button" class="pf-btn is-sm is-ghost" disabled>上一页</button>
        <span class="page is-on">1</span><span class="page">2</span><span class="page">3</span>
        <button type="button" class="pf-btn is-sm is-ghost">下一页</button>
      </div>
    </div>

    <div v-else-if="node.kind === 'modal'" class="pf-modal-trigger">
      <div>
        <strong>{{ node.props.title || '模态对话框容器' }}</strong>
        <p class="pf-sub">点击右侧按钮可在运行时从触发点展开圆角 24 弹窗</p>
      </div>
      <button type="button" class="pf-btn is-sm is-secondary" @click.stop>唤起弹窗预览</button>
    </div>
  </div>
</template>

<style scoped>
.pf-render { display: flex; flex-direction: column; gap: 14px; }

.mono { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
.strong { font-weight: 620; color: var(--ink); }
.muted { color: var(--ink-muted); font-size: 12.5px; }
.accent { color: var(--blue); }
.pf-sub { margin: 2px 0 0; color: var(--ink-soft); font-size: 12px; }
.pf-inline { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }

.pf-header { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 16px; }
.pf-header-main { max-width: 680px; }
.pf-header-title { display: flex; align-items: center; gap: 10px; }
.pf-header-title h1 { margin: 0; font-size: 28px; font-weight: 740; line-height: 1.15; letter-spacing: -.03em; color: var(--ink); }
.pf-header-desc { margin: 8px 0 0; font-size: 13px; line-height: 1.55; color: var(--ink-muted); }
.pf-header-actions { display: flex; align-items: center; gap: 8px; }

.pf-readout { display: flex; flex-direction: column; gap: 16px; }
.pf-readout-top { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; }
.pf-readout-heading { display: flex; align-items: center; gap: 12px; }
.pf-readout-grid { display: grid; grid-template-columns: 5fr 7fr; gap: 16px; align-items: stretch; }

.pf-hero {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  background: linear-gradient(135deg, rgba(130, 221, 45, .085), rgba(255, 255, 255, .72));
}

.pf-hero-top { display: flex; align-items: center; justify-content: space-between; }
.pf-hero-label { font-size: 12px; font-weight: 600; color: var(--ink-muted); }
.pf-hero-value { display: flex; align-items: baseline; gap: 4px; margin: 8px 0; }
.pf-hero-value .num { font-family: var(--font-mono); font-variant-numeric: tabular-nums; font-size: 40px; font-weight: 720; line-height: 1.05; letter-spacing: -.045em; color: var(--ink); }
.pf-hero-value sup { font-family: var(--font-mono); font-size: 14px; font-weight: 650; color: var(--ink-muted); }
.pf-hero-foot { display: flex; align-items: center; justify-content: space-between; padding-top: 8px; border-top: 1px solid var(--line); font-size: 12px; color: var(--ink-soft); }
.pf-hero-foot .mono { font-weight: 640; color: var(--ink); }

.pf-readout-side { display: flex; flex-direction: column; justify-content: space-between; gap: 12px; }
.pf-meter { padding: 14px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--input-bg); }
.pf-meter-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; font-size: 12px; }
.pf-meter-title { font-weight: 600; color: var(--ink-muted); }
.pf-meter-now { font-size: 11.5px; font-weight: 620; color: var(--leaf-text); }

.pf-filter { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.pf-filter-fields { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px; }
.pf-filter-actions { display: flex; align-items: center; gap: 8px; margin-left: auto; }

.pf-table-block { display: flex; flex-direction: column; }
.pf-table-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px; padding-bottom: 14px; }
.pf-density { display: flex; align-items: center; gap: 8px; font-size: 12px; color: var(--ink-soft); }

.pf-table .big { font-size: 18px; font-weight: 680; }
.pf-cat { display: inline-flex; align-items: center; gap: 6px; height: 22px; padding: 0 10px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.pf-cat i { width: 8px; height: 8px; border-radius: 999px; }

.pf-topo { display: flex; flex-direction: column; gap: 12px; }
.pf-topo-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pf-topo-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; align-items: center; }
.pf-topo-card { padding: 14px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--input-bg); }
.pf-topo-card.is-active { border-color: var(--blue); background: var(--surface-solid); box-shadow: 0 0 0 3px var(--blue-soft); }
.pf-topo-top { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.pf-topo-metric { display: flex; align-items: baseline; justify-content: space-between; margin-top: 12px; }
.pf-topo-metric .big { font-size: 18px; font-weight: 680; color: var(--ink); }
.pf-topo-loss { display: flex; align-items: center; justify-content: space-between; margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); font-size: 11.5px; color: var(--ink-faint); }

.pf-card { display: flex; flex-direction: column; gap: 10px; }
.pf-card-head { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; }
.pf-card-body { padding: 14px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--input-bg); }
.pf-card-text { margin: 0; font-size: 13px; color: var(--ink-muted); }

.pf-tabs { display: flex; flex-direction: column; gap: 12px; }
.pf-tabs-body { min-height: 60px; padding: 14px; border: 1px dashed var(--line); border-radius: var(--r-md); background: var(--canvas); }

.pf-grid-block { display: flex; flex-direction: column; gap: 10px; }
.pf-grid-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.pf-slot-hint { display: flex; align-items: center; justify-content: center; min-height: 56px; color: var(--ink-faint); font-size: 12px; }

.pf-slotbox { padding: 16px; border: 1px dashed var(--line-strong); border-radius: var(--r-md); }
.pf-slot-filled { display: flex; flex-direction: column; gap: 10px; }
.pf-slot-head { display: flex; align-items: center; justify-content: space-between; padding: 0 4px; font-size: 12px; color: var(--ink-soft); }
.pf-slot-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 8px; padding: 20px 16px; text-align: center; cursor: copy; }
.pf-slot-empty strong { font-size: 13px; font-weight: 620; color: var(--ink-muted); }
.pf-slot-plus { display: inline-flex; align-items: center; justify-content: center; width: 42px; height: 42px; border-radius: 14px; background: var(--pill-bg); box-shadow: var(--edge), var(--e1); color: var(--blue); }

.pf-divider { display: flex; align-items: center; gap: 12px; padding: 8px 0; }
.pf-divider .line { flex: 1; height: 1px; background: var(--line-strong); }

.pf-field.is-narrow { max-width: 360px; }
.pf-field.is-number { max-width: 260px; }
.pf-field.is-date { max-width: 300px; }
.pf-input-wrap { position: relative; display: block; }
.pf-input-wrap sup { position: absolute; top: 11px; right: 12px; font-family: var(--font-mono); font-size: 10.5px; color: var(--ink-soft); }

.pf-switch-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; max-width: 360px; }
.pf-switch-row strong { font-size: 13px; font-weight: 620; color: var(--ink); }

.pf-check-label { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; cursor: pointer; }

.pf-modal-trigger { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px; border: 1px solid var(--line); border-radius: var(--r-md); background: var(--input-bg); }
.pf-modal-trigger strong { font-size: 13.5px; font-weight: 640; color: var(--ink); }

.pf-pager .page { padding: 4px 10px; border-radius: var(--r-xs); color: var(--ink-muted); }
.pf-pager .page.is-on { background: var(--pill-bg); box-shadow: var(--edge); color: var(--blue); font-weight: 640; }

@media (max-width: 1180px) {
  .pf-readout-grid { grid-template-columns: minmax(0, 1fr); }
  .pf-topo-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
