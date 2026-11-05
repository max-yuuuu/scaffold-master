/**
 * 设计器状态层（PR 6C）：一棵可撤销的区块树，进出都是方案 §3.4 的那份 design_json。
 *
 * 纯 TS、不引 Vue：画布与属性面板只把「拖到哪、改了哪一格」翻译成功令，形状规则集中在这里，
 * 脱离浏览器也能跑。落点合法性在前端拦一份是为了手感（拖不动比报错好），
 * <b>权威判定在后端 `PageDesign`</b> —— 两边都收紧，前端放过去而后端拒掉的那一份，
 * 表现是人在设计器里画完一整页才收到一条 400。
 */

export interface ModelField {
  fieldCode: string
  fieldName: string
  javaType: string
  formType: string
  dictType: string | null
  isPrimary: number
  isAutoIncrement: number
  isList: number
  isQuery: number
  queryType: string
  isInsert: number
  isEdit: number
  isRequired: number
}

export interface DesignColumn {
  key: string
  field: string
  title?: string
  width?: number
  minWidth?: number
  align?: string
  sortable?: boolean
  render?: string
}

export interface UINode {
  key: string
  type: string
  props: Record<string, unknown>
  children: UINode[]
  columns: DesignColumn[]
  /**
   * 画布上的会话状态，与 key 同级、不进 design_json：
   * locked 是「这一格先别动」，visible 是「这一格我先藏起来专心改别的」。
   * 生成的那份页面里没有这两个概念，所以存库、比对脏、给生成器看的时候都必须当它们不存在。
   */
  locked?: boolean
  visible?: boolean
}

export interface JsonNode {
  type: string
  props?: Record<string, unknown>
  children?: JsonNode[]
  columns?: Array<Record<string, unknown>>
}

export interface DesignJson {
  version: string
  pageType: string
  modelRef: string
  layout: JsonNode
}

export const SUPPORTED_VERSION = '1.0'

/** 页面直属的五个区块，顺序与 §3.1 的画布一致 */
export const BLOCKS = ['SearchForm', 'Toolbar', 'DataTable', 'RowActions', 'FormContainer'] as const

export const BLOCK_LABELS: Record<string, string> = {
  PageContainer: '页面',
  SearchForm: '查询区',
  Toolbar: '工具栏',
  DataTable: '表格区',
  RowActions: '行操作',
  FormContainer: '表单弹窗',
}

/** 控件与按钮 → 能待在哪些容器，与后端 ComponentCatalog 同一份（两处改了要一起改） */
const PLACEMENT: Record<string, string[]> = {
  TextInput: ['SearchForm', 'FormContainer'],
  Textarea: ['FormContainer'],
  NumberInput: ['SearchForm', 'FormContainer'],
  DateInput: ['SearchForm', 'FormContainer'],
  DateRangePicker: ['SearchForm'],
  Select: ['SearchForm', 'FormContainer'],
  DictSelect: ['SearchForm', 'FormContainer'],
  Button: ['Toolbar', 'RowActions'],
}

export const CONTROL_TYPES = Object.keys(PLACEMENT)

/**
 * 左侧组件库的分组。
 *
 * 只列 `PLACEMENT` 里有的类型 —— 物料库给出一格生成器产不出来的组件，就等于让人搭出一个
 * 保存时才报 400 的页面，而那时人已经画完整页了。这条与后端 ComponentCatalog 同一份，
 * 由 design.test.ts 逐项钉住。
 */
export const PALETTE: Array<{ group: string, tone: string, items: Array<{ type: string, label: string, icon: string, desc: string }> }> = [
  {
    group: '页面区块',
    tone: 'sky',
    items: [
      { type: 'SearchForm', label: '查询区', icon: 'filter', desc: '列表页顶部的筛选条件行' },
      { type: 'Toolbar', label: '工具栏', icon: 'sliders', desc: '表格上方的整页动作按钮' },
      { type: 'DataTable', label: '表格区', icon: 'table', desc: '列、分页与横向滚动的数据表' },
      { type: 'RowActions', label: '行操作', icon: 'more', desc: '每行尾部平铺与收进更多的按钮' },
      { type: 'FormContainer', label: '表单弹窗', icon: 'edit', desc: '新增与编辑共用的弹窗表单' },
    ],
  },
  {
    group: '表单控件',
    tone: 'sand',
    items: [
      { type: 'TextInput', label: '文本输入', icon: 'edit', desc: '单行文本，查询区与表单通用' },
      { type: 'Textarea', label: '多行文本', icon: 'file', desc: '长文本，只在表单里站得住' },
      { type: 'NumberInput', label: '数字输入', icon: 'activity', desc: '数值，小数位由模型标度给' },
      { type: 'DateInput', label: '日期选择', icon: 'clock', desc: '单个日期或日期时间' },
      { type: 'DateRangePicker', label: '日期区间', icon: 'board', desc: '起止两端，只在查询区成立' },
      { type: 'Select', label: '下拉选择', icon: 'chevronDown', desc: '选项由设计里的枚举给' },
      { type: 'DictSelect', label: '字典下拉', icon: 'dict', desc: '选项来自字典，字典挂在模型字段上' },
    ],
  },
  {
    group: '操作按钮',
    tone: 'rose',
    items: [
      { type: 'Button', label: '按钮', icon: 'command', desc: '动作选一个，权限点跟着动作走' },
    ],
  },
]

/**
 * 每个组件在属性面板里能改哪几格，与后端 ComponentCatalog 的属性白名单同一份。
 *
 * 面板只渲染这里列出的属性：多出来的一格（比如给 DictSelect 再填一个字典）在页面上完全能用，
 * 但生成器不认 —— 那才是真的难查。类型、字典、小数位这些不在这里，模型给。
 */
export const PROPS: Record<string, string[]> = {
  PageContainer: ['title', 'desc'],
  SearchForm: ['columns', 'gap'],
  Toolbar: [],
  DataTable: ['pagination', 'pageSize'],
  RowActions: ['priority'],
  FormContainer: ['columns'],
  TextInput: ['label', 'field', 'required', 'placeholder', 'queryType'],
  Textarea: ['label', 'field', 'required', 'placeholder'],
  NumberInput: ['label', 'field', 'required', 'placeholder', 'queryType'],
  DateInput: ['label', 'field', 'required', 'queryType'],
  DateRangePicker: ['label', 'field', 'queryType'],
  Select: ['label', 'field', 'required', 'placeholder', 'queryType'],
  DictSelect: ['label', 'field', 'required', 'placeholder', 'queryType'],
  Button: ['label', 'action', 'variant', 'danger'],
}

/** 属性面板的中文标签；不在表里的属性按名字原样显示（新增属性时先补这里再放面板出来） */
export const PROP_LABELS: Record<string, string> = {
  title: '页面标题',
  desc: '页面说明',
  columns: '每行列数',
  gap: '间距',
  pagination: '分页',
  pageSize: '每页条数',
  priority: '直出按钮数',
  label: '标签',
  field: '绑定字段',
  required: '必填',
  placeholder: '占位提示',
  queryType: '查询方式',
  action: '动作',
  variant: '样式',
  danger: '危险操作',
}

export function propsOf(type: string): string[] {
  return PROPS[type] ?? []
}

/**
 * 新落下的组件先带什么属性。
 *
 * 按钮默认给 refresh 而不是留空：动作是权限点的来源，空动作的按钮存进去，
 * 生成器就只能猜这颗按钮要不要种菜单权限。控件默认把标签带上，
 * 面板里不至于出现一格没有名字的空白。
 */
export function defaultPropsFor(type: string): Record<string, unknown> {
  if ((BLOCKS as readonly string[]).includes(type)) return {}
  if (type === 'Button') return { label: '按钮', action: 'refresh' }
  const item = PALETTE.flatMap(group => group.items).find(entry => entry.type === type)
  return { label: item?.label ?? type }
}

/** 物料库与层级树、画布共用一套图标：同一个类型在三处得长一样，否则人对不上号 */
const ICONS: Record<string, string> = Object.fromEntries(
  PALETTE.flatMap(group => group.items.map(item => [item.type, item.icon])),
)

export function iconOf(type: string): string {
  return ICONS[type] ?? 'file'
}

export function descOf(type: string): string {
  for (const group of PALETTE) {
    const hit = group.items.find(item => item.type === type)
    if (hit) return hit.desc
  }
  return ''
}

export const ACTIONS = ['create', 'edit', 'delete', 'export', 'refresh']

/** 动作 → 中文，与后端 ComponentCatalog.permissionSuffix 一一对应：权限点不在这里出现第二次 */
export const ACTION_LABELS: Record<string, string> = {
  create: '新增',
  edit: '编辑',
  delete: '删除',
  export: '导出',
  refresh: '刷新',
}

export interface Designer {
  readonly root: UINode
  readonly selected: string | null
  readonly canUndo: boolean
  readonly canRedo: boolean
  modelRef(): string
  /** 打开已有设计时把库里的 modelCode 绑上：这不是人做的一步，所以不进撤销栈 */
  bindModel(code: string): void
  setModel(code: string): void
  select(key: string | null): void
  childrenOf(type: string): UINode[]
  find(key: string): UINode | null
  /** 属性面板要按「这一格在哪个容器里」决定给不给改（required 在查询区没意义），所以要能问爹 */
  parentOf(key: string): UINode | null
  insert(target: string, spec: { type: string, props?: Record<string, unknown> }, index?: number): string
  move(key: string, target: string, index?: number): void
  remove(key: string): void
  /** 复制这一格，插在原件后面。只适用于控件：区块是按 type 定位的，复制出第二个容器会让 locate 找错 */
  duplicate(key: string): string
  /** 画布状态开关（锁定 / 显示），进撤销栈但不进 design_json */
  setFlag(key: string, flag: 'visible' | 'locked', value: boolean): void
  setProp(key: string, name: string, value: unknown): void
  setTitle(title: string): void
  setDesc(desc: string): void
  columns(): DesignColumn[]
  addColumn(spec: Omit<DesignColumn, 'key'>, index?: number): string
  updateColumn(key: string, patch: Partial<DesignColumn>): void
  removeColumn(index: number): void
  referencedFields(): string[]
  undo(): void
  redo(): void
  json(): DesignJson
}

let seedCounter = 0

/** 内部 key 只活在画布里（选中、拖放定位），不进 design_json */
function nextKey(prefix: string) {
  seedCounter += 1
  return `${prefix}-${seedCounter.toString(36)}`
}

function node(type: string, props: Record<string, unknown> = {}): UINode {
  return { key: nextKey(type), type, props, children: [], columns: [] }
}

/** 历史快照用：key 与画布状态都照抄，否则撤销一次锁定就把锁定弄丢了 */
function copy(source: UINode): UINode {
  return {
    key: source.key,
    type: source.type,
    props: { ...source.props },
    children: source.children.map(copy),
    columns: source.columns.map(column => ({ ...column })),
    ...(source.locked ? { locked: true } : {}),
    ...(source.visible === false ? { visible: false } : {}),
  }
}

/** 复制用：整棵子树重新发 key，且不继承锁定/隐藏 —— 副本是新建的东西，不是原件的影子 */
function rekey(source: UINode): UINode {
  return {
    key: nextKey(source.type),
    type: source.type,
    props: { ...source.props },
    children: source.children.map(rekey),
    columns: source.columns.map(column => ({ ...column, key: nextKey('col') })),
  }
}

function blockRoot(): UINode {
  const page = node('PageContainer', { title: '', desc: '' })
  return page
}

/** 从 design_json 读进来：缺 props / children / columns 都按空处理，序列化的时候再省略回去 */
function readNode(raw: JsonNode): UINode {
  const built = node(raw.type, { ...(raw.props ?? {}) })
  built.children = (raw.children ?? []).map(readNode)
  built.columns = (raw.columns ?? []).map(column => ({ key: nextKey('col'), ...(column as Omit<DesignColumn, 'key'>) }))
  return built
}

function writeNode(source: UINode): JsonNode {
  const out: JsonNode = { type: source.type }
  if (Object.keys(source.props).length) out.props = { ...source.props }
  if (source.type === 'DataTable') {
    out.columns = source.columns.map(({ key, ...rest }) => stripEmpty(rest))
    return out
  }
  out.children = source.children.map(writeNode)
  return out
}

function stripEmpty(record: Record<string, unknown>) {
  const out: Record<string, unknown> = {}
  Object.entries(record).forEach(([name, value]) => {
    if (value !== undefined && value !== null && value !== '') out[name] = value
  })
  return out
}

export function emptyDesign(): DesignJson {
  return { version: SUPPORTED_VERSION, pageType: 'list', modelRef: '', layout: { type: 'PageContainer', children: [] } }
}

/** 控件形态跟着字段类型与字典，与后端 GenerationInput 的推导同一条：设计器不该给出一个生成不出来的组合 */
function controlFor(field: ModelField, inSearch: boolean): string {
  if (field.dictType) return 'DictSelect'
  if (inSearch && isBetween(field) && isDate(field)) return 'DateRangePicker'
  if (isDate(field)) return 'DateInput'
  if (isNumber(field)) return 'NumberInput'
  if (field.formType === 'TEXTAREA') return 'Textarea'
  if (['SELECT', 'RADIO', 'CHECKBOX'].includes(field.formType)) return 'Select'
  return 'TextInput'
}

const isDate = (field: ModelField) => ['LocalDate', 'LocalDateTime'].includes(field.javaType)
const isNumber = (field: ModelField) => ['Integer', 'Long', 'BigDecimal', 'Double', 'Float'].includes(field.javaType)
const isBetween = (field: ModelField) => (field.queryType ?? '').toUpperCase() === 'BETWEEN'
const autoKey = (field: ModelField) => field.isPrimary === 1 && field.isAutoIncrement === 1
const writable = (field: ModelField) => field.isInsert === 1 || field.isEdit === 1

/**
 * 按模型初始化一份设计：`is_query` → 查询区，`is_list` → 列，`is_insert / is_edit` → 表单。
 *
 * 自增主键两处都不进：值由数据库给，进表单等于让人猜下一个 id（5G 的真机缺陷），
 * 进列则是把一列没人要的编号摆上台面。
 */
export function initFromModel(fields: ModelField[]): DesignJson {
  const layout = node('PageContainer')
  BLOCKS.forEach(type => layout.children.push(node(type)))
  const block = (type: string) => layout.children.find(item => item.type === type)!

  fields.filter(field => field.isQuery === 1).forEach(field => {
    const control = node(controlFor(field, true), { label: field.fieldName, field: field.fieldCode, queryType: field.queryType || '=' })
    block('SearchForm').children.push(control)
  })
  block('Toolbar').children.push(node('Button', { label: '新增', action: 'create', variant: 'primary' }))
  block('DataTable').columns = fields
    .filter(field => field.isList === 1 && !autoKey(field))
    .map(field => ({ key: nextKey('col'), field: field.fieldCode, title: field.fieldName, ...(isNumber(field) ? { align: 'right', render: 'money' } : {}), ...(field.dictType ? { render: 'tag' } : {}) }))
  block('RowActions').children.push(
    node('Button', { label: '编辑', action: 'edit' }),
    node('Button', { label: '删除', action: 'delete', danger: true }),
  )
  fields.filter(field => writable(field) && !autoKey(field)).forEach(field => {
    const props: Record<string, unknown> = { label: field.fieldName, field: field.fieldCode }
    if (field.isRequired === 1) props.required = true
    block('FormContainer').children.push(node(controlFor(field, false), props))
  })

  return { version: SUPPORTED_VERSION, pageType: 'list', modelRef: '', layout: writeNode(layout) }
}

export function parseDesign(json: DesignJson): UINode {
  if (!json || typeof json !== 'object') throw new Error('design_json 顶层必须是对象')
  if (json.version !== SUPPORTED_VERSION) throw new Error(`version 只支持 "${SUPPORTED_VERSION}"，这份设计里是 ${json.version ?? '（没写）'}`)
  if (!json.layout || json.layout.type !== 'PageContainer') throw new Error('layout 必须是 PageContainer')
  return readNode(json.layout)
}

/** 找到 target 那个容器节点：区块名，或 PageContainer（往页面直属层插区块） */
function locate(root: UINode, target: string): UINode | null {
  if (root.type === target) return root
  return root.children.find(child => child.type === target) ?? null
}

function search(tree: UINode, key: string): { node: UINode, parent: UINode } | null {
  for (const child of tree.children) {
    if (child.key === key) return { node: child, parent: tree }
    const hit = search(child, key)
    if (hit) return hit
  }
  return null
}

function placementError(target: string, type: string): string | null {
  const block = (BLOCKS as readonly string[]).includes(type)
  if (type === 'PageContainer') return '页面只有一个 PageContainer，它就是画布的根'
  if (block) {
    if (target === 'PageContainer') return null
    // 容器只允许两层：再深一格的方块在画布上点不到，生成物里也没有地方摆它
    return `${target} 里不能再套容器 ${type}：最多两层（PageContainer > 区块 > 组件）`
  }
  if (target === 'PageContainer') {
    return `PageContainer 下只能放容器，收到 ${type}。控件要先进容器 —— 查询条件进 SearchForm，`
      + '表单字段进 FormContainer，按钮进 Toolbar / RowActions，列进 DataTable'
  }
  const allowed = PLACEMENT[type]
  if (!allowed) {
    return `不支持的组件 ${type}。第一期支持：${['PageContainer', ...BLOCKS, ...CONTROL_TYPES].join('、')}`
  }
  if (target === 'DataTable') {
    return 'DataTable 只用 columns 声明列，不接受 children：列不是组件，写成 children 生成器就得猜这是列还是控件'
  }
  if (allowed.includes(target)) return null
  const candidates = CONTROL_TYPES.filter(item => (PLACEMENT[item] ?? []).includes(target))
  if (candidates.length === 1) return `${target} 里只能放 ${candidates[0]}，收到 ${type}`
  return `${target} 里不能放 ${type}：它能待在的容器是 ${allowed.join(' / ')}`
}

/** 报错文案里要报人看得懂的名字，不是 TextInput 这种类型码 */
function nameOf(target: UINode): string {
  return String(target.props.title || target.props.label || BLOCK_LABELS[target.type] || target.type)
}

function clamp(index: number | undefined, length: number) {
  if (index === undefined || Number.isNaN(index)) return length
  return Math.max(0, Math.min(index, length))
}

/** 属性值为空串就是「没填」：留着一个空串进 JSON，第二次读回来会变成一条看不见的差异 */
function applyProp(props: Record<string, unknown>, name: string, value: unknown) {
  if (value === undefined || value === null || value === '') delete props[name]
  else props[name] = value
}

export function createDesigner(seed?: DesignJson): Designer {
  let root = seed ? parseDesign(seed) : blockRoot()
  let model = seed?.modelRef ?? ''
  let selected: string | null = null
  // 历史里带上 modelRef：只存树的话，撤销一次「换模型」会把树还原而模型留着，两边对不上
  const past: Array<{ tree: UINode, model: string }> = []
  const future: Array<{ tree: UINode, model: string }> = []

  const snapshot = () => {
    past.push({ tree: copy(root), model })
    if (past.length > 100) past.shift()
    future.length = 0
  }

  const restore = (state: { tree: UINode, model: string }) => {
    root = state.tree
    model = state.model
    if (selected && !search(root, selected)) selected = null
  }

  const blockOf = (type: string): UINode | null => locate(root, type)

  const api: Designer = {
    get root() {
      return root
    },
    get selected() {
      return selected
    },
    get canUndo() {
      return past.length > 0
    },
    get canRedo() {
      return future.length > 0
    },
    modelRef: () => model,
    bindModel(code) {
      model = String(code ?? '')
    },
    setModel(code) {
      snapshot()
      model = String(code ?? '')
    },
    select(key) {
      selected = key
    },
    childrenOf(type) {
      return blockOf(type)?.children ?? []
    },
    find(key) {
      return search(root, key)?.node ?? null
    },
    parentOf(key) {
      return search(root, key)?.parent ?? null
    },
    insert(target, spec, index) {
      const container = blockOf(target)
      if (!container) throw new Error(`画布上没有 ${target} 这个容器`)
      const problem = placementError(target, spec.type)
      if (problem) throw new Error(problem)
      snapshot()
      const created = node(spec.type, { ...(spec.props ?? {}) })
      container.children.splice(clamp(index, container.children.length), 0, created)
      selected = created.key
      return created.key
    },
    move(key, target, index) {
      const container = blockOf(target)
      if (!container) throw new Error(`画布上没有 ${target} 这个容器`)
      const hit = search(root, key)
      if (!hit) throw new Error('要移动的组件不在画布上')
      if (hit.node.locked) throw new Error(`「${nameOf(hit.node)}」已锁定，先解锁再移动`)
      const problem = placementError(target, hit.node.type)
      if (problem) throw new Error(problem)
      snapshot()
      hit.parent.children.splice(hit.parent.children.indexOf(hit.node), 1)
      container.children.splice(clamp(index, container.children.length), 0, hit.node)
      selected = key
    },
    remove(key) {
      const hit = search(root, key)
      if (!hit) return
      if (hit.node.locked) throw new Error(`「${nameOf(hit.node)}」已锁定，先解锁再删除`)
      snapshot()
      hit.parent.children.splice(hit.parent.children.indexOf(hit.node), 1)
      if (selected === key) selected = null
    },
    duplicate(key) {
      const hit = search(root, key)
      if (!hit) throw new Error('要复制的组件不在画布上')
      if (hit.node.locked) throw new Error(`「${nameOf(hit.node)}」已锁定，先解锁再复制`)
      if ((BLOCKS as readonly string[]).includes(hit.node.type)) {
        throw new Error(`${hit.node.type} 是按类型定位的容器，复制出第二个会让画布找不到它：一个页面每类区块只有一个`)
      }
      snapshot()
      const created = rekey(hit.node)
      hit.parent.children.splice(hit.parent.children.indexOf(hit.node) + 1, 0, created)
      selected = created.key
      return created.key
    },
    setFlag(key, flag, value) {
      const hit = search(root, key) ?? (key === root.key ? { node: root } : null)
      if (!hit) return
      if (flag === 'visible') {
        snapshot()
        // 默认就是显示，只有藏起来才需要记一笔，免得撤销栈里多出一堆「显示」的空步
        if (value) delete hit.node.visible
        else hit.node.visible = false
        return
      }
      if (Boolean(hit.node.locked) === Boolean(value)) return
      snapshot()
      if (value) hit.node.locked = true
      else delete hit.node.locked
    },
    setProp(key, name, value) {
      const hit = search(root, key) ?? (key === root.key ? { node: root } : null)
      if (!hit) return
      snapshot()
      applyProp(hit.node.props, name, value)
    },
    setTitle(title) {
      snapshot()
      applyProp(root.props, 'title', title)
    },
    setDesc(desc) {
      snapshot()
      applyProp(root.props, 'desc', desc)
    },
    columns() {
      return blockOf('DataTable')?.columns ?? []
    },
    addColumn(spec, index) {
      const table = blockOf('DataTable')
      if (!table) throw new Error('画布上没有表格区：先把 DataTable 拖进来')
      snapshot()
      const created: DesignColumn = { key: nextKey('col'), ...spec }
      table.columns.splice(clamp(index, table.columns.length), 0, created)
      return created.key
    },
    updateColumn(key, patch) {
      const table = blockOf('DataTable')
      const column = table?.columns.find(item => item.key === key)
      if (!column || !table) return
      snapshot()
      Object.assign(column, patch)
    },
    removeColumn(index) {
      const table = blockOf('DataTable')
      if (!table) return
      snapshot()
      table.columns.splice(index, 1)
    },
    referencedFields() {
      const out: string[] = []
      const walk = (source: UINode) => {
        source.columns.forEach(column => {
          if (column.field && !out.includes(column.field)) out.push(column.field)
        })
        source.children.forEach(child => {
          const field = child.props.field
          if (typeof field === 'string' && field && !out.includes(field)) out.push(field)
          walk(child)
        })
      }
      walk(root)
      return out
    },
    undo() {
      const previous = past.pop()
      if (!previous) return
      future.push({ tree: copy(root), model })
      restore(previous)
    },
    redo() {
      const next = future.pop()
      if (!next) return
      past.push({ tree: copy(root), model })
      restore(next)
    },
    json() {
      return {
        version: SUPPORTED_VERSION,
        pageType: 'list',
        modelRef: model,
        layout: writeNode(root),
      }
    },
  }
  return api
}

export function actionOptions() {
  return ACTIONS
}
