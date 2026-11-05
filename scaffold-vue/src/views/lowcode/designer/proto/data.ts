/**
 * 1:1 视觉原型的模型与演示数据 —— 逐字搬自 designer/src/data/designerData.ts。
 *
 * 这一层是**静态稿**：kind、节点级 dataSource / permissionKey / refreshInterval / events、
 * 样式四格与栅格分栏都不在 `design_json v1.0` 与后端 ComponentCatalog 的能力范围内，
 * 所以 proto 目录不接保存与发布。下一刀（design_json 升 1.1）要合回来的就是这份形状。
 */

export type NodeKind =
  | 'header' | 'readout' | 'filter' | 'table' | 'topology' | 'emptySlot'
  | 'grid' | 'card' | 'tabs' | 'divider' | 'input' | 'number' | 'select'
  | 'date' | 'checkbox' | 'switch' | 'button' | 'pagination' | 'modal' | 'tag'

export type CategoryTone = 'sand' | 'sky' | 'lilac' | 'rose' | 'mint'
export type TableDensity = 'compact' | 'standard' | 'relaxed'
export type MaterialGroup = '布局容器' | '表单输入' | '数据展示' | '基础操作'
export type EventActionType = 'refreshDataSource' | 'openModal' | 'showToast' | 'callApi' | 'exportData'

export interface EventBinding {
  id: string
  triggerCode: string
  triggerName: string
  actionType: EventActionType
  targetDesc: string
  enabled: boolean
}

export interface PageConfig {
  name: string
  code: string
  description: string
  permissionKey: string
  paddingX: number
  paddingY: number
  blockGap: number
  showDots: boolean
  canvasBgTone: 'default' | 'pure' | 'warm'
  events: EventBinding[]
}

export interface MaterialItem {
  id: string
  kind: NodeKind
  name: string
  category: MaterialGroup
  categoryTone: CategoryTone
  code: string
  desc: string
}

export interface TableRecord {
  id: string
  batchCode: string
  lineName: string
  categoryLabel: string
  categoryTone: CategoryTone
  operator: string
  yieldRate: string
  cycleMs: string
  updatedAt: string
  status: 'ok' | 'info' | 'danger' | 'neutral'
  statusText: string
  permissionCode: string
  checked?: boolean
}

export interface CanvasNode {
  id: string
  kind: NodeKind
  name: string
  code: string
  categoryLabel: string
  categoryTone: CategoryTone
  visible: boolean
  locked: boolean
  permissionKey: string
  dataSource: string
  refreshInterval: string
  paddingPx: number
  radiusPx: 8 | 12 | 16 | 20 | 24
  marginBottomPx?: number
  surfaceStyle?: 'solid' | 'glass' | 'subtle'
  events?: EventBinding[]
  children?: CanvasNode[]
  columns?: CanvasNode[][]
  props: {
    title: string
    subtitle: string
    primaryActionText?: string
    secondaryActionText?: string
    heroMetricValue?: string
    heroMetricUnit?: string
    heroMetricDelta?: string
    density?: TableDensity
    showHatchBar?: boolean
    showSpecPlate?: boolean
    filterFields?: { label: string, fieldKey: string, placeholder: string, widthPx: number }[]
  }
}

export const MATERIAL_LIBRARY: MaterialItem[] = [
  { id: 'mat-grid', kind: 'grid', name: '栅格容器', category: '布局容器', categoryTone: 'sky', code: 'Grid', desc: '响应式多列分栏布局容器' },
  { id: 'mat-card', kind: 'card', name: '面板卡片', category: '布局容器', categoryTone: 'sky', code: 'Panel', desc: '带标题栏与内边距的标准内容面板' },
  { id: 'mat-tabs', kind: 'tabs', name: '分段容器', category: '布局容器', categoryTone: 'sky', code: 'Tabs', desc: '凹槽底座与玻璃滑块切换视图' },
  { id: 'mat-slot', kind: 'emptySlot', name: '插槽容器', category: '布局容器', categoryTone: 'sky', code: 'Slot', desc: '虚线待放置自定义子容器' },
  { id: 'mat-divider', kind: 'divider', name: '分割线', category: '布局容器', categoryTone: 'sky', code: 'Divider', desc: '1px 细线区块分隔符' },

  { id: 'mat-input', kind: 'input', name: '输入框', category: '表单输入', categoryTone: 'sand', code: 'Input', desc: '标准 36px 文本输入字段' },
  { id: 'mat-number', kind: 'number', name: '数字输入', category: '表单输入', categoryTone: 'sand', code: 'InputNumber', desc: '带等宽数值与单位上标的输入框' },
  { id: 'mat-select', kind: 'select', name: '下拉选择', category: '表单输入', categoryTone: 'sand', code: 'Select', desc: '支持单选与检索的浮层选择器' },
  { id: 'mat-date', kind: 'date', name: '日期选择', category: '表单输入', categoryTone: 'sand', code: 'DatePicker', desc: '日期与时间范围选择器' },
  { id: 'mat-checkbox', kind: 'checkbox', name: '复选框', category: '表单输入', categoryTone: 'sand', code: 'Checkbox', desc: '17x17 圆角多选勾选框' },
  { id: 'mat-switch', kind: 'switch', name: '开关', category: '表单输入', categoryTone: 'sand', code: 'Switch', desc: '青柠色注满状态切换开关' },
  { id: 'mat-filter', kind: 'filter', name: '查询表单', category: '表单输入', categoryTone: 'sand', code: 'QueryForm', desc: '多字段组合筛选表单栏' },

  { id: 'mat-table', kind: 'table', name: '数据表格', category: '数据展示', categoryTone: 'lilac', code: 'Table', desc: '支持吸顶表头与三档密度的标准表格' },
  { id: 'mat-readout', kind: 'readout', name: '数值指标', category: '数据展示', categoryTone: 'mint', code: 'Statistic', desc: '大字号核心指标与进度读数面板' },
  { id: 'mat-tag', kind: 'tag', name: '状态标签', category: '数据展示', categoryTone: 'lilac', code: 'Tag', desc: '带光环圆点的语义状态胶囊' },
  { id: 'mat-header', kind: 'header', name: '页面标题', category: '数据展示', categoryTone: 'lilac', code: 'PageHeader', desc: '页面主标题、说明文字与操作区' },

  { id: 'mat-button', kind: 'button', name: '按钮组', category: '基础操作', categoryTone: 'rose', code: 'Button', desc: '主按钮、次级玻璃按钮与幽灵按钮' },
  { id: 'mat-pagination', kind: 'pagination', name: '分页器', category: '基础操作', categoryTone: 'rose', code: 'Pagination', desc: '列表页码切换与总条数统计' },
  { id: 'mat-modal', kind: 'modal', name: '弹窗容器', category: '基础操作', categoryTone: 'rose', code: 'Modal', desc: '圆角 24 模态对话框触发块' },
]

export const CATEGORY_GROUPS: MaterialGroup[] = ['布局容器', '表单输入', '数据展示', '基础操作']

/** 分组点的颜色：原型用五种分类色，只区分类别不表示状态 */
export const GROUP_TONE: Record<MaterialGroup, CategoryTone> = {
  布局容器: 'sky',
  表单输入: 'sand',
  数据展示: 'lilac',
  基础操作: 'rose',
}

export const EVENT_ACTION_OPTIONS: { value: EventActionType, label: string, defaultTarget: string }[] = [
  { value: 'refreshDataSource', label: '刷新关联数据源', defaultTarget: 'api.v2.formationBatches' },
  { value: 'callApi', label: '调用服务端工艺接口', defaultTarget: 'POST /api/v2/batch/release' },
  { value: 'openModal', label: '唤起弹窗容器', defaultTarget: '批次复核确认弹窗' },
  { value: 'showToast', label: '弹出状态提示胶囊', defaultTarget: '已触发组件交互动作' },
  { value: 'exportData', label: '导出当前工艺报表', defaultTarget: '化成工站放行留档.xlsx' },
]

export const AVAILABLE_TRIGGERS_BY_KIND: Record<NodeKind | 'page', { code: string, name: string }[]> = {
  page: [
    { code: 'onPageLoad', name: '页面初始化完成时' },
    { code: 'onPagePoll', name: '全局定时轮询同步时' },
    { code: 'onBeforeLeave', name: '离开当前工站页面前' },
  ],
  header: [
    { code: 'onPrimaryClick', name: '点击主操作按钮时' },
    { code: 'onSecondaryClick', name: '点击次级操作按钮时' },
  ],
  readout: [
    { code: 'onDataRefresh', name: '指标遥测读数更新时' },
    { code: 'onThresholdAlert', name: '良率低于基准阈值时' },
  ],
  filter: [
    { code: 'onSearch', name: '点击执行查询时' },
    { code: 'onReset', name: '点击重置筛选条件时' },
    { code: 'onFieldChange', name: '任意筛选项值改变时' },
  ],
  table: [
    { code: 'onRowSelect', name: '勾选或取消批次行时' },
    { code: 'onRowAction', name: '点击行内「复核曲线」时' },
    { code: 'onDensityChange', name: '切换行高密度档位时' },
    { code: 'onPageChange', name: '切换分页页码时' },
  ],
  topology: [
    { code: 'onStepClick', name: '点击工序节点卡片时' },
    { code: 'onDataRefresh', name: '工序流向数据同步时' },
  ],
  emptySlot: [{ code: 'onMount', name: '插槽容器挂载完成时' }],
  grid: [{ code: 'onMount', name: '栅格容器挂载完成时' }],
  card: [
    { code: 'onCardClick', name: '点击面板卡片区域时' },
    { code: 'onMount', name: '面板卡片挂载完成时' },
  ],
  tabs: [{ code: 'onTabChange', name: '切换分段页签时' }],
  divider: [{ code: 'onClick', name: '点击分割线标题时' }],
  input: [
    { code: 'onChange', name: '输入内容改变时' },
    { code: 'onBlur', name: '输入框失去焦点时' },
    { code: 'onPressEnter', name: '按下回车键时' },
  ],
  number: [
    { code: 'onChange', name: '数值发生改变时' },
    { code: 'onBlur', name: '数值框失去焦点时' },
  ],
  select: [
    { code: 'onChange', name: '选中选项改变时' },
    { code: 'onDropdownVisible', name: '展开下拉菜单时' },
  ],
  date: [{ code: 'onChange', name: '选中日期改变时' }],
  checkbox: [{ code: 'onChange', name: '勾选状态改变时' }],
  switch: [{ code: 'onChange', name: '开关状态切换时' }],
  button: [
    { code: 'onPrimaryClick', name: '点击主按钮时' },
    { code: 'onSecondaryClick', name: '点击次级按钮时' },
    { code: 'onDangerClick', name: '点击危险按钮时' },
  ],
  pagination: [{ code: 'onPageChange', name: '页码切换时' }],
  modal: [
    { code: 'onOpen', name: '弹窗打开时' },
    { code: 'onConfirm', name: '点击弹窗确认时' },
    { code: 'onCancel', name: '关闭或取消弹窗时' },
  ],
  tag: [{ code: 'onClick', name: '点击状态标签时' }],
}

export function getDefaultEventsForKind(kind: NodeKind): EventBinding[] {
  const triggers = AVAILABLE_TRIGGERS_BY_KIND[kind] || []
  const first = triggers[0] || { code: 'onClick', name: '点击组件时' }
  if (kind === 'header' || kind === 'button') {
    return [{ id: `evt-${kind}-1`, triggerCode: first.code, triggerName: first.name, actionType: 'callApi', targetDesc: 'POST /api/v2/batch/release', enabled: true }]
  }
  if (kind === 'filter') {
    return [{ id: `evt-${kind}-1`, triggerCode: 'onSearch', triggerName: '点击执行查询时', actionType: 'refreshDataSource', targetDesc: 'api.v2.formationBatches', enabled: true }]
  }
  if (kind === 'table') {
    return [{ id: `evt-${kind}-1`, triggerCode: 'onRowAction', triggerName: '点击行内「复核曲线」时', actionType: 'openModal', targetDesc: '电芯化成电压曲线复核弹窗', enabled: true }]
  }
  return [{ id: `evt-${kind}-1`, triggerCode: first.code, triggerName: first.name, actionType: 'showToast', targetDesc: `已响应「${first.name}」事件`, enabled: true }]
}

export const INITIAL_PAGE_CONFIG: PageConfig = {
  name: '电芯化成工站批次放行台',
  code: 'PAGE_MES_FORMATION_RELEASE',
  description: '宁德三期电芯化成工序实时遥测监控与待审电芯批次放行工作台',
  permissionKey: 'mes:station:formation:access',
  paddingX: 14,
  paddingY: 12,
  blockGap: 10,
  showDots: true,
  canvasBgTone: 'default',
  events: [
    { id: 'evt-page-init', triggerCode: 'onPageLoad', triggerName: '页面初始化完成时', actionType: 'refreshDataSource', targetDesc: 'telemetry.formationYield & api.v2.formationBatches', enabled: true },
    { id: 'evt-page-poll', triggerCode: 'onPagePoll', triggerName: '全局定时轮询同步时', actionType: 'callApi', targetDesc: 'GET /api/v2/station/heartbeat', enabled: true },
  ],
}

const node = (partial: Partial<CanvasNode> & Pick<CanvasNode, 'id' | 'kind' | 'name' | 'code' | 'categoryLabel' | 'categoryTone'>): CanvasNode => ({
  visible: true,
  locked: false,
  permissionKey: 'mes:station:view',
  dataSource: 'api.v2.formationBatches',
  refreshInterval: '15000ms',
  paddingPx: 20,
  radiusPx: 20,
  marginBottomPx: 0,
  surfaceStyle: 'solid',
  props: { title: '', subtitle: '' },
  ...partial,
})

export const INITIAL_CANVAS_NODES: CanvasNode[] = [
  node({
    id: 'node-header-1', kind: 'header', name: '页面标题', code: 'PageHeader_1', categoryLabel: '布局容器', categoryTone: 'sky',
    permissionKey: 'mes:station:view', dataSource: 'pageMeta.stationHeader', refreshInterval: '静态绑定',
    events: getDefaultEventsForKind('header'),
    props: {
      title: '电芯化成工站批次放行台',
      subtitle: '实时聚合宁德三期产线化成柜电压曲线、OCV 压降离散度与异常批次拦截记录，供值班工艺工程师核准放行。',
      primaryActionText: '批量核准放行',
      secondaryActionText: '导出工艺留档',
    },
  }),
  node({
    id: 'node-readout-1', kind: 'readout', name: '数值指标', code: 'Statistic_1', categoryLabel: '数据展示', categoryTone: 'mint',
    permissionKey: 'mes:telemetry:read', dataSource: 'telemetry.formationYield', refreshInterval: '5000ms',
    events: getDefaultEventsForKind('readout'),
    props: {
      title: '当班一次化成直通率',
      subtitle: '采样窗口 08:00—16:00 · 基准阈值 99.10%',
      heroMetricValue: '99.42', heroMetricUnit: '%', heroMetricDelta: '+0.28%',
      showHatchBar: true, showSpecPlate: true,
    },
  }),
  node({
    id: 'node-filter-1', kind: 'filter', name: '查询表单', code: 'QueryForm_1', categoryLabel: '表单输入', categoryTone: 'sand',
    permissionKey: 'mes:batch:query', dataSource: 'query.batchFilterForm', refreshInterval: '实时触发',
    events: getDefaultEventsForKind('filter'),
    props: {
      title: '批次筛选条件',
      subtitle: '支持按电芯批次号、所属车间产线与放行状态组合检索',
      filterFields: [
        { label: '电芯批次编码', fieldKey: 'batchCode', placeholder: '输入批次号 如 BT-202604...', widthPx: 220 },
        { label: '所属化成产线', fieldKey: 'lineCode', placeholder: '全部产线（三期 A/B/C 线）', widthPx: 200 },
        { label: '质检放行状态', fieldKey: 'releaseState', placeholder: '全部状态', widthPx: 180 },
      ],
    },
  }),
  node({
    id: 'node-table-1', kind: 'table', name: '数据表格', code: 'Table_1', categoryLabel: '数据展示', categoryTone: 'lilac',
    permissionKey: 'mes:batch:release', dataSource: 'api.v2.formationBatches', refreshInterval: '15000ms',
    events: getDefaultEventsForKind('table'),
    props: {
      title: '待核准电芯化成批次明细',
      subtitle: '勾选行可执行批量复判或锁定隔离，吸附列保持横向滚动上下文',
      density: 'standard',
    },
  }),
]

export const INITIAL_TABLE_RECORDS: TableRecord[] = [
  { id: 'row-1', batchCode: 'BT-20260419-081A', lineName: '三期化成 A02 线 · 14 号柜', categoryLabel: '磷酸铁锂储能', categoryTone: 'mint', operator: '周明远', yieldRate: '99.64%', cycleMs: '184.2 min', updatedAt: '14:28:09', status: 'ok', statusText: '达标待放行', permissionCode: 'mes:release:l2', checked: true },
  { id: 'row-2', batchCode: 'BT-20260419-082B', lineName: '三期化成 B01 线 · 07 号柜', categoryLabel: '三元高镍动力', categoryTone: 'sky', operator: '林书瑶', yieldRate: '99.18%', cycleMs: '212.6 min', updatedAt: '14:24:51', status: 'info', statusText: '复测校准中', permissionCode: 'mes:recheck:l1', checked: false },
  { id: 'row-3', batchCode: 'BT-20260419-079C', lineName: '三期化成 C04 线 · 22 号柜', categoryLabel: '高倍率商用车', categoryTone: 'sand', operator: '陈敬之', yieldRate: '96.45%', cycleMs: '248.0 min', updatedAt: '14:19:33', status: 'danger', statusText: '压降超限隔离', permissionCode: 'mes:quarantine:l3', checked: false },
  { id: 'row-4', batchCode: 'BT-20260419-076A', lineName: '三期化成 A01 线 · 03 号柜', categoryLabel: '户用低压模组', categoryTone: 'lilac', operator: '沈知秋', yieldRate: '99.51%', cycleMs: '179.8 min', updatedAt: '14:05:12', status: 'ok', statusText: '已签发入库', permissionCode: 'mes:archive:read', checked: false },
]

/** 分类色走平台令牌（--cat-1..5 就是沙 / 天 / 丁香 / 玫瑰 / 薄荷），不另建一套变量 */
export const TONE_VAR: Record<CategoryTone, { bg: string, fg: string }> = {
  sand: { bg: 'var(--cat-1-bg)', fg: 'var(--cat-1-ink)' },
  sky: { bg: 'var(--cat-2-bg)', fg: 'var(--cat-2-ink)' },
  lilac: { bg: 'var(--cat-3-bg)', fg: 'var(--cat-3-ink)' },
  rose: { bg: 'var(--cat-4-bg)', fg: 'var(--cat-4-ink)' },
  mint: { bg: 'var(--cat-5-bg)', fg: 'var(--cat-5-ink)' },
}

export function createNodeFromMaterial(material: MaterialItem): CanvasNode {
  const suffix = Math.floor(100 + Math.random() * 899)
  return node({
    id: `node-${material.kind}-${suffix}`,
    kind: material.kind,
    name: material.name,
    code: `${material.code}_${suffix}`,
    categoryLabel: material.category,
    categoryTone: material.categoryTone,
    permissionKey: `mes:${material.kind}:view`,
    dataSource: `api.v2.${material.kind}Source`,
    refreshInterval: material.kind === 'readout' ? '5000ms' : '15000ms',
    events: getDefaultEventsForKind(material.kind),
    children: material.kind === 'emptySlot' ? [] : undefined,
    columns: material.kind === 'grid' ? [[], []] : undefined,
    props: {
      title: material.kind === 'emptySlot' ? '待放置插槽容器' : material.name,
      subtitle: material.desc,
      primaryActionText: '执行主操作',
      secondaryActionText: '导出明细',
      heroMetricValue: '99.48',
      heroMetricUnit: '%',
      heroMetricDelta: '+0.19%',
      density: 'standard',
      showHatchBar: true,
      showSpecPlate: true,
      filterFields: [
        { label: '电芯批次号', fieldKey: 'batchNo', placeholder: '输入批次编码...', widthPx: 220 },
        { label: '所属车间产线', fieldKey: 'workshopLine', placeholder: '全部化成产线', widthPx: 200 },
      ],
    },
  })
}
