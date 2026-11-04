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

/** 左侧组件库的分组：容器给「整块」，控件给「往块里塞」 */
export const PALETTE: Array<{ group: string, items: Array<{ type: string, label: string }> }> = [
  { group: '区块', items: BLOCKS.map(type => ({ type, label: BLOCK_LABELS[type] })) },
  {
    group: '控件',
    items: [
      { type: 'TextInput', label: '文本输入' },
      { type: 'Textarea', label: '多行文本' },
      { type: 'NumberInput', label: '数字输入' },
      { type: 'DateInput', label: '日期选择' },
      { type: 'DateRangePicker', label: '日期区间' },
      { type: 'Select', label: '下拉选择' },
      { type: 'DictSelect', label: '字典下拉' },
      { type: 'Button', label: '按钮' },
    ],
  },
]

const ACTIONS = ['create', 'edit', 'delete', 'export', 'refresh']

export interface Designer {
  readonly root: UINode
  readonly selected: string | null
  readonly canUndo: boolean
  readonly canRedo: boolean
  modelRef(): string
  setModel(code: string): void
  select(key: string | null): void
  childrenOf(type: string): UINode[]
  find(key: string): UINode | null
  insert(target: string, spec: { type: string, props?: Record<string, unknown> }, index?: number): string
  move(key: string, target: string, index?: number): void
  remove(key: string): void
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

function copy(source: UINode): UINode {
  return {
    key: source.key,
    type: source.type,
    props: { ...source.props },
    children: source.children.map(copy),
    columns: source.columns.map(column => ({ ...column })),
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
      snapshot()
      hit.parent.children.splice(hit.parent.children.indexOf(hit.node), 1)
      if (selected === key) selected = null
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
