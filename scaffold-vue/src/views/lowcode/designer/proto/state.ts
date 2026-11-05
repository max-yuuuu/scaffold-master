import { computed, reactive, ref } from 'vue'
import {
  AVAILABLE_TRIGGERS_BY_KIND, INITIAL_CANVAS_NODES, INITIAL_PAGE_CONFIG, INITIAL_TABLE_RECORDS,
  createNodeFromMaterial, dropZones, getDefaultEventsForKind,
  type CanvasNode, type EventBinding, type MaterialItem, type PageConfig, type TableRecord,
} from './data.ts'

export type Overlay = 'command' | 'schema' | 'publish' | 'eventDrawer'
export type DeviceMode = 'desktop' | 'tablet' | 'mobile'
export type InspectorTab = 'props' | 'style' | 'events'

export interface ToastItem { id: number, tone: 'ok' | 'info' | 'danger', text: string, bornAt: number }

export interface EventDrawerState {
  scope: 'node' | 'page'
  nodeId?: string
  mode: 'create' | 'edit'
  draft: EventBinding
}

/** 递归查任意层级（顶层 + 插槽 children + 栅格 columns） */
function findNode(list: CanvasNode[], id: string): CanvasNode | null {
  for (const item of list) {
    if (item.id === id) return item
    if (item.children) {
      const hit = findNode(item.children, id)
      if (hit) return hit
    }
    if (item.columns) {
      for (const column of item.columns) {
        const hit = findNode(column, id)
        if (hit) return hit
      }
    }
  }
  return null
}

function mapNode(list: CanvasNode[], id: string, updater: (node: CanvasNode) => CanvasNode): CanvasNode[] {
  return list.map(item => {
    if (item.id === id) return updater(item)
    return {
      ...item,
      children: item.children ? mapNode(item.children, id, updater) : item.children,
      columns: item.columns ? item.columns.map(column => mapNode(column, id, updater)) : item.columns,
    }
  })
}

function removeNode(list: CanvasNode[], id: string): { next: CanvasNode[], removed: CanvasNode | null } {
  let removed: CanvasNode | null = null
  const walk = (array: CanvasNode[]): CanvasNode[] => {
    const out: CanvasNode[] = []
    for (const item of array) {
      if (item.id === id) {
        removed = item
        continue
      }
      out.push({
        ...item,
        children: item.children ? walk(item.children) : item.children,
        columns: item.columns ? item.columns.map(walk) : item.columns,
      })
    }
    return out
  }
  return { next: walk(list), removed }
}

/** 在数组里就地挪一位；找不到就递归进子层，与原型 moveInArray 同一条路径 */
function moveRelative(list: CanvasNode[], id: string, direction: 'up' | 'down'): CanvasNode[] {
  const index = list.findIndex(item => item.id === id)
  if (index !== -1) {
    const target = direction === 'up' ? index - 1 : index + 1
    if (target < 0 || target >= list.length) return list
    const copy = [...list]
    const [moved] = copy.splice(index, 1)
    copy.splice(target, 0, moved)
    return copy
  }
  return list.map(item => ({
    ...item,
    children: item.children ? moveRelative(item.children, id, direction) : item.children,
    columns: item.columns ? item.columns.map(column => moveRelative(column, id, direction)) : item.columns,
  }))
}

/**
 * 1:1 视觉原型的状态层。
 *
 * 这一份只管画布上看得见的那些事：节点树、撤销栈、拖拽落点、浮层栈、提示牌堆。
 * 它**不产出 design_json**，也不与 `../design.ts` 那套真模型共用任何状态 ——
 * 两套形状（20 种 kind + 节点级事件 vs PageContainer + 5 区块）要到 design_json 升 1.1 那一刀才合流。
 */
export function createPrototypeState() {
  const nodes = ref<CanvasNode[]>(INITIAL_CANVAS_NODES)
  const history = ref<CanvasNode[][]>([INITIAL_CANVAS_NODES])
  const historyIndex = ref(0)
  const unsavedCount = ref(2)
  const selectedNodeId = ref<string | null>('node-readout-1')
  const pageConfig = reactive<PageConfig>({ ...INITIAL_PAGE_CONFIG, events: [...INITIAL_PAGE_CONFIG.events] })
  const tableRecords = ref<TableRecord[]>(INITIAL_TABLE_RECORDS)

  const leftTab = ref<'materials' | 'tree'>('materials')
  const inspectorTab = ref<InspectorTab>('props')
  const materialQuery = ref('')
  const groupCollapsed = ref<Record<string, boolean>>({})
  const treeExpanded = ref<Record<string, boolean>>({ page: true })

  const deviceMode = ref<DeviceMode>('desktop')
  const viewportWidth = ref<'auto' | number>('auto')
  const customWidth = ref('1280')
  const viewportMenuOpen = ref(false)
  const previewMode = ref(false)
  const leftCollapsed = ref(false)
  const rightCollapsed = ref(false)
  const canvasScrolled = ref(false)

  const overlays = ref<Overlay[]>([])
  const eventDrawer = ref<EventDrawerState | null>(null)
  const commandQuery = ref('')

  const draggingMaterial = ref<MaterialItem | null>(null)
  const draggingNodeId = ref<string | null>(null)
  /**
   * 拖拽期间在目标位置就地预览的那颗组件本体。
   * 必须在拖拽开始那一刻建好、整段拖拽复用同一份：`createNodeFromMaterial` 会生成随机
   * 后缀的 id 与 code，放进 computed 里每次 dragover 都重建一颗，预览帧会一直抖。
   */
  const dragPreview = ref<CanvasNode | null>(null)
  const activeDropIndex = ref<number | null>(null)
  const activeContainerTarget = ref<{ id: string, colIndex?: number } | null>(null)
  const canvasDragOver = ref(false)
  const treeDragNodeId = ref<string | null>(null)
  const treeDropIndicator = ref<{ id: string, position: 'before' | 'after' | 'inside' } | null>(null)

  const toasts = ref<ToastItem[]>([])
  let toastSeq = 0

  const selectedNode = computed(() => (selectedNodeId.value ? findNode(nodes.value, selectedNodeId.value) : null))
  const canUndo = computed(() => historyIndex.value > 0)
  const canRedo = computed(() => historyIndex.value < history.value.length - 1)
  const topOverlay = computed(() => overlays.value[overlays.value.length - 1] ?? null)

  function pushToast(text: string, tone: ToastItem['tone'] = 'ok') {
    const id = ++toastSeq
    toasts.value = [...toasts.value.slice(-2), { id, tone, text, bornAt: Date.now() }]
    setTimeout(() => { toasts.value = toasts.value.filter(item => item.id !== id) }, 3600)
  }

  function commit(next: CanvasNode[], markDirty = true) {
    const trimmed = history.value.slice(0, historyIndex.value + 1)
    history.value = [...trimmed, next]
    historyIndex.value = history.value.length - 1
    nodes.value = next
    if (markDirty) unsavedCount.value += 1
  }

  function undo() {
    if (historyIndex.value <= 0) return
    historyIndex.value -= 1
    nodes.value = history.value[historyIndex.value]
    unsavedCount.value = Math.max(0, unsavedCount.value - 1)
    pushToast('已撤销上一步画布改动', 'info')
  }

  function redo() {
    if (historyIndex.value >= history.value.length - 1) return
    historyIndex.value += 1
    nodes.value = history.value[historyIndex.value]
    unsavedCount.value += 1
    pushToast('已重做画布改动', 'info')
  }

  function openOverlay(kind: Overlay) {
    overlays.value = [...overlays.value.filter(item => item !== kind), kind]
  }

  function closeOverlay() {
    overlays.value = overlays.value.slice(0, -1)
  }

  function updateSelected(updater: (node: CanvasNode) => CanvasNode) {
    if (!selectedNode.value) return
    commit(mapNode(nodes.value, selectedNode.value.id, updater))
  }

  function insertMaterial(material: MaterialItem, index: number) {
    const created = createNodeFromMaterial(material)
    const next = [...nodes.value]
    next.splice(Math.max(0, Math.min(next.length, index)), 0, created)
    commit(next)
    selectedNodeId.value = created.id
    pushToast(`已将「${material.name}」挂载至画布`)
  }

  /**
   * 顶层落点由 `activeDropIndex` 自己说话：dragover 没在列表上算出索引，就是没有落点，
   * 松手什么也不做。以前由调用方兜底成「插到末尾」，等于把「没落到位置上」画成了一个新组件。
   */
  function dropAtTopIndex() {
    const index = activeDropIndex.value
    if (index === null) {
      resetDrag()
      return
    }
    if (draggingMaterial.value) {
      insertMaterial(draggingMaterial.value, index)
    } else if (draggingNodeId.value) {
      const oldIndex = nodes.value.findIndex(item => item.id === draggingNodeId.value)
      const { next, removed } = removeNode(nodes.value, draggingNodeId.value)
      if (removed) {
        const finalIndex = oldIndex !== -1 && oldIndex < index ? Math.max(0, index - 1) : index
        next.splice(Math.min(next.length, Math.max(0, finalIndex)), 0, removed)
        commit(next)
        selectedNodeId.value = removed.id
        pushToast(`已移动「${removed.name}」位置`, 'info')
      }
    }
    resetDrag()
  }

  function dropInsideContainer(containerId: string, colIndex?: number) {
    const target = findNode(nodes.value, containerId)
    // 没有可放置区域的节点不是落点：整件事当没发生过，不进撤销栈、不弹提示
    if (!target || !dropZones(target).length || draggingNodeId.value === containerId) {
      resetDrag()
      return
    }
    let payload: CanvasNode | null = null
    let base = nodes.value
    if (draggingMaterial.value) {
      payload = createNodeFromMaterial(draggingMaterial.value)
    } else if (draggingNodeId.value) {
      const result = removeNode(nodes.value, draggingNodeId.value)
      base = result.next
      payload = result.removed
    }
    if (!payload) {
      resetDrag()
      return
    }
    const inserted = payload
    const next = mapNode(base, containerId, container => {
      if (typeof colIndex === 'number') {
        const columns = container.columns ? container.columns.map(column => [...column]) : [[], []]
        while (columns.length <= colIndex) columns.push([])
        columns[colIndex].push(inserted)
        return { ...container, columns }
      }
      return { ...container, children: [...(container.children || []), inserted] }
    })
    commit(next)
    selectedNodeId.value = inserted.id
    pushToast(`已将「${inserted.name}」放入容器内`)
    resetDrag()
  }

  /** 拖物料卡：以这份物料建一颗预览节点，落点处直接画出组件本体 */
  function startMaterialDrag(item: MaterialItem) {
    draggingMaterial.value = item
    draggingNodeId.value = null
    dragPreview.value = createNodeFromMaterial(item)
  }

  /** 拖画布上已有的组件：预览就是它自己（同一份对象，只读渲染） */
  function startNodeDrag(id: string) {
    const target = findNode(nodes.value, id)
    draggingNodeId.value = target ? id : null
    draggingMaterial.value = null
    dragPreview.value = target
  }

  function resetDrag() {
    canvasDragOver.value = false
    activeDropIndex.value = null
    activeContainerTarget.value = null
    draggingMaterial.value = null
    draggingNodeId.value = null
    dragPreview.value = null
    treeDragNodeId.value = null
    treeDropIndicator.value = null
  }

  function toggleLock(id: string) {
    const target = findNode(nodes.value, id)
    if (!target) return
    commit(mapNode(nodes.value, id, item => ({ ...item, locked: !item.locked })))
    pushToast(target.locked ? `已解锁组件「${target.name}」` : `已锁定组件「${target.name}」`, 'info')
  }

  function toggleVisible(id: string) {
    const target = findNode(nodes.value, id)
    if (!target) return
    commit(mapNode(nodes.value, id, item => ({ ...item, visible: !item.visible })))
    pushToast(target.visible ? `已隐藏组件「${target.name}」` : `已显示组件「${target.name}」`, 'info')
  }

  function moveNode(id: string, direction: 'up' | 'down') {
    commit(moveRelative(nodes.value, id, direction))
  }

  function duplicateNode(id: string) {
    const source = findNode(nodes.value, id)
    if (!source) return
    const suffix = Math.floor(100 + Math.random() * 899)
    const copy: CanvasNode = {
      ...source,
      id: `${source.id}-copy-${suffix}`,
      name: `${source.name}（副本）`,
      code: `${source.code}_${suffix}`,
      props: { ...source.props },
    }
    const insert = (list: CanvasNode[]): CanvasNode[] => {
      const index = list.findIndex(item => item.id === id)
      if (index !== -1) {
        const next = [...list]
        next.splice(index + 1, 0, copy)
        return next
      }
      return list.map(item => ({
        ...item,
        children: item.children ? insert(item.children) : item.children,
        columns: item.columns ? item.columns.map(insert) : item.columns,
      }))
    }
    commit(insert(nodes.value))
    selectedNodeId.value = copy.id
    pushToast(`已复制节点「${source.name}」`, 'info')
  }

  function deleteNode(id: string) {
    const target = findNode(nodes.value, id)
    if (!target) return
    if (target.locked) {
      pushToast(`节点「${target.name}」已锁定，无法移除`, 'danger')
      return
    }
    if (nodes.value.length <= 1 && nodes.value[0]?.id === id) {
      pushToast('画布至少需保留一个根业务组件', 'danger')
      return
    }
    const { next } = removeNode(nodes.value, id)
    commit(next)
    if (selectedNodeId.value === id && next[0]) selectedNodeId.value = next[0].id
    pushToast(`已移除组件「${target.name}」`, 'danger')
  }

  function treeDrop(draggedId: string, targetId: string, position: 'before' | 'after' | 'inside') {
    if (!draggedId || draggedId === targetId) return
    const dragged = findNode(nodes.value, draggedId)
    if (!dragged || dragged.locked) return
    // 「放进去」只对真的有可放置区域的组件成立，与画布那条 dropInsideContainer 同一个口径
    if (position === 'inside') {
      const container = findNode(nodes.value, targetId)
      if (!container || !dropZones(container).length) return
    }
    // 防止把父容器拖进自己的子节点内部
    if (findNode([dragged], targetId)) return
    const { next, removed } = removeNode(nodes.value, draggedId)
    if (!removed) return
    if (position === 'inside') {
      commit(mapNode(next, targetId, container => {
        if (container.kind === 'grid') {
          const columns = container.columns ? container.columns.map(column => [...column]) : [[], []]
          columns[0] = [...columns[0], removed]
          return { ...container, columns }
        }
        return { ...container, children: [...(container.children || []), removed] }
      }))
      selectedNodeId.value = removed.id
      pushToast(`已将「${removed.name}」移入容器内`)
      return
    }
    const insertRelative = (list: CanvasNode[]): CanvasNode[] => {
      const index = list.findIndex(item => item.id === targetId)
      if (index !== -1) {
        const copy = [...list]
        copy.splice(position === 'before' ? index : index + 1, 0, removed)
        return copy
      }
      return list.map(item => ({
        ...item,
        children: item.children ? insertRelative(item.children) : item.children,
        columns: item.columns ? item.columns.map(insertRelative) : item.columns,
      }))
    }
    commit(insertRelative(next))
    selectedNodeId.value = removed.id
    pushToast(`已调整「${removed.name}」层级顺序`, 'info')
  }

  function openEventDrawer(scope: 'node' | 'page', editing?: EventBinding) {
    const triggers = scope === 'node' && selectedNode.value
      ? AVAILABLE_TRIGGERS_BY_KIND[selectedNode.value.kind] || AVAILABLE_TRIGGERS_BY_KIND.button
      : AVAILABLE_TRIGGERS_BY_KIND.page
    const first = triggers[0] || { code: 'onClick', name: '点击组件时' }
    eventDrawer.value = editing
      ? { scope, nodeId: scope === 'node' ? selectedNode.value?.id : undefined, mode: 'edit', draft: { ...editing } }
      : {
          scope,
          nodeId: scope === 'node' ? selectedNode.value?.id : undefined,
          mode: 'create',
          draft: scope === 'node' && selectedNode.value
            ? { id: `evt-${Date.now()}`, triggerCode: first.code, triggerName: first.name, actionType: 'showToast', targetDesc: `执行「${selectedNode.value.name}」交互动作`, enabled: true }
            : { id: `evt-page-${Date.now()}`, triggerCode: first.code, triggerName: first.name, actionType: 'refreshDataSource', targetDesc: 'api.v2.formationBatches', enabled: true },
        }
    openOverlay('eventDrawer')
  }

  function saveEventDrawer() {
    const state = eventDrawer.value
    if (!state) return
    const { scope, nodeId, mode, draft } = state
    if (scope === 'node' && nodeId) {
      commit(mapNode(nodes.value, nodeId, item => {
        const current = item.events || getDefaultEventsForKind(item.kind)
        return { ...item, events: mode === 'create' ? [...current, draft] : current.map(e => (e.id === draft.id ? draft : e)) }
      }))
      pushToast(mode === 'create' ? `已新增组件事件「${draft.triggerName}」` : `已更新组件事件「${draft.triggerName}」`)
    } else {
      pageConfig.events = mode === 'create'
        ? [...pageConfig.events, draft]
        : pageConfig.events.map(e => (e.id === draft.id ? draft : e))
      pushToast(mode === 'create' ? `已新增页面事件「${draft.triggerName}」` : `已更新页面事件「${draft.triggerName}」`)
    }
    closeOverlay()
  }

  function saveDraft() {
    unsavedCount.value = 0
    pushToast('页面模型与权限绑定已保存至本地草稿')
  }

  function discardChanges() {
    nodes.value = INITIAL_CANVAS_NODES
    history.value = [INITIAL_CANVAS_NODES]
    historyIndex.value = 0
    unsavedCount.value = 0
    selectedNodeId.value = 'node-readout-1'
    pushToast('已还原全部未保存改动', 'info')
  }

  function toggleRow(rowId: string) {
    tableRecords.value = tableRecords.value.map(row => (row.id === rowId ? { ...row, checked: !row.checked } : row))
  }

  function toggleAllRows() {
    const next = !(tableRecords.value.length > 0 && tableRecords.value.every(row => row.checked))
    tableRecords.value = tableRecords.value.map(row => ({ ...row, checked: next }))
  }

  /** 就地改任意层级的一个节点（表格密度、属性面板的每一格都走这里） */
  function updateNode(id: string, updater: (node: CanvasNode) => CanvasNode) {
    commit(mapNode(nodes.value, id, updater))
  }

  const checkedRows = computed(() => tableRecords.value.filter(row => row.checked).length)

  return {
    nodes, history, historyIndex, unsavedCount, selectedNodeId, pageConfig, tableRecords, checkedRows,
    leftTab, inspectorTab, materialQuery, groupCollapsed, treeExpanded,
    deviceMode, viewportWidth, customWidth, viewportMenuOpen, previewMode, leftCollapsed, rightCollapsed, canvasScrolled,
    overlays, topOverlay, openOverlay, closeOverlay, eventDrawer, commandQuery,
    draggingMaterial, draggingNodeId, dragPreview, activeDropIndex, activeContainerTarget, canvasDragOver, treeDragNodeId, treeDropIndicator,
    startMaterialDrag, startNodeDrag,
    toasts, pushToast, selectedNode, canUndo, canRedo,
    commit, undo, redo, updateSelected, insertMaterial, dropAtTopIndex, dropInsideContainer, resetDrag,
    toggleLock, toggleVisible, moveNode, duplicateNode, deleteNode, treeDrop,
    openEventDrawer, saveEventDrawer, saveDraft, discardChanges, toggleRow, toggleAllRows, updateNode,
  }
}

export type PrototypeState = ReturnType<typeof createPrototypeState>
