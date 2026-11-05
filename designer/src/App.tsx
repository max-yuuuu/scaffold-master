import React, { useEffect, useMemo, useRef, useState } from 'react';
import {
  AVAILABLE_TRIGGERS_BY_KIND,
  CanvasNode,
  EVENT_ACTION_OPTIONS,
  EventActionType,
  EventBinding,
  getDefaultEventsForKind,
  INITIAL_CANVAS_NODES,
  INITIAL_PAGE_CONFIG,
  INITIAL_TABLE_RECORDS,
  MATERIAL_LIBRARY,
  MaterialGroup,
  MaterialItem,
  PageConfig,
  TableDensity,
  TableRecord,
} from './data/designerData';
import { CanvasNodeRenderer } from './components/CanvasNodeRenderer';
import {
  IconArrowLeft,
  IconBolt,
  IconCheck,
  IconChevronDown,
  IconChevronRight,
  IconCode,
  IconComponentKind,
  IconCopy,
  IconDesktop,
  IconEye,
  IconEyeOff,
  IconGrip,
  IconLock,
  IconMobile,
  IconMoon,
  IconPage,
  IconPanelLeftClose,
  IconPanelLeftOpen,
  IconPanelRightClose,
  IconPanelRightOpen,
  IconPlus,
  IconRedo,
  IconSearch,
  IconSun,
  IconTablet,
  IconTrash,
  IconUnlock,
  IconUndo,
} from './components/Icons';

interface ToastItem {
  id: string;
  tone: 'ok' | 'info' | 'danger';
  text: string;
}

export default function App() {
  // 主题（浅色 / 深色）
  const [theme, setTheme] = useState<'light' | 'dark'>('light');

  // 左侧面板 Tab：默认展示「组件库」（materials 在前，tree 在后）
  const [leftTab, setLeftTab] = useState<'materials' | 'tree'>('materials');
  const [materialSearch, setMaterialSearch] = useState('');
  const [groupCollapsed, setGroupCollapsed] = useState<Record<MaterialGroup, boolean>>({
    布局容器: false,
    表单输入: false,
    数据展示: false,
    基础操作: false,
  });
  const [treeExpanded, setTreeExpanded] = useState<Record<string, boolean>>({
    root: true,
  });

  // 画布节点与历史栈（支持撤销/重做）
  const [nodes, setNodes] = useState<CanvasNode[]>(INITIAL_CANVAS_NODES);
  const [history, setHistory] = useState<CanvasNode[][]>([INITIAL_CANVAS_NODES]);
  const [historyIndex, setHistoryIndex] = useState<number>(0);
  const [unsavedCount, setUnsavedCount] = useState<number>(2);

  // 当前选中节点（可点击画布空白处取消选中）
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>('node-readout-1');

  // 页面级全局配置（点击画布空白处或点击「页面层级」根节点时在右侧面板配置）
  const [pageConfig, setPageConfig] = useState<PageConfig>(INITIAL_PAGE_CONFIG);

  // 右侧配置面板分段：组件/页面属性 | 样式属性 | 事件配置
  const [inspectorTab, setInspectorTab] = useState<'props' | 'style' | 'events'>('props');

  // 页面层级树节点拖拽状态
  const [treeDragNodeId, setTreeDragNodeId] = useState<string | null>(null);
  const [treeDropIndicator, setTreeDropIndicator] = useState<{
    targetId: string;
    position: 'before' | 'after' | 'inside';
  } | null>(null);

  // 画布视口模式：支持「自适应满宽 (auto)」或任意自定义像素宽度，不再锁死固定值
  const [deviceMode, setDeviceMode] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');
  const [viewportWidth, setViewportWidth] = useState<'auto' | number>('auto');
  const [customWidthInput, setCustomWidthInput] = useState<string>('1280');
  const [viewportMenuOpen, setViewportMenuOpen] = useState<boolean>(false);
  const [isCanvasDragOver, setIsCanvasDragOver] = useState<boolean>(false);
  const [previewMode, setPreviewMode] = useState<boolean>(false);
  const [canvasScrolled, setCanvasScrolled] = useState<boolean>(false);
  const [leftPanelCollapsed, setLeftPanelCollapsed] = useState<boolean>(false);
  const [rightPanelCollapsed, setRightPanelCollapsed] = useState<boolean>(false);

  // 运行预览态下自动同时收起左右两侧面板
  const isLeftCollapsed = previewMode || leftPanelCollapsed;
  const isRightCollapsed = previewMode || rightPanelCollapsed;

  // 拖拽状态：支持左侧新组件拖入 + 画布已有组件拖动重排 + 容器内嵌套落位
  const [draggingMaterial, setDraggingMaterial] = useState<MaterialItem | null>(null);
  const [draggingNodeId, setDraggingNodeId] = useState<string | null>(null);
  const [activeDropIndex, setActiveDropIndex] = useState<number | null>(null);
  const [activeContainerTarget, setActiveContainerTarget] = useState<{
    nodeId: string;
    colIndex?: number;
  } | null>(null);
  const canvasListRef = useRef<HTMLDivElement | null>(null);

  // 表格行数据
  const [tableRecords, setTableRecords] = useState<TableRecord[]>(INITIAL_TABLE_RECORDS);

  // 统一焦点栈浮层（命令面板、Schema 抽屉、发布确认弹窗）
  const [overlayStack, setOverlayStack] = useState<
    ('command' | 'schema' | 'publish' | 'eventDrawer')[]
  >([]);
  const [commandQuery, setCommandQuery] = useState('');
  const [eventDrawerState, setEventDrawerState] = useState<{
    scope: 'node' | 'page';
    nodeId?: string;
    mode: 'create' | 'edit';
    draft: EventBinding;
  } | null>(null);

  // 底部胶囊轻提示牌堆（最多 3 张）
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
  }, [theme]);

  // 统一焦点栈：Escape 优先关闭最上层浮层，⌘K 唤起命令面板
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setOverlayStack((prev) =>
          prev.includes('command') ? prev.filter((x) => x !== 'command') : [...prev, 'command']
        );
      } else if (e.key === 'Escape') {
        if (overlayStack.length > 0) {
          e.preventDefault();
          setOverlayStack((prev) => prev.slice(0, -1));
        }
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [overlayStack]);

  const pushToast = (text: string, tone: 'ok' | 'info' | 'danger' = 'ok') => {
    const id = `toast-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`;
    setToasts((prev) => [...prev.slice(-2), { id, tone, text }]);
    window.setTimeout(() => {
      setToasts((prev) => prev.filter((item) => item.id !== id));
    }, 3600);
  };

  const openOverlay = (kind: 'command' | 'schema' | 'publish' | 'eventDrawer') => {
    setOverlayStack((prev) => [...prev.filter((k) => k !== kind), kind]);
  };

  const closeTopOverlay = () => {
    setOverlayStack((prev) => prev.slice(0, -1));
  };

  // 打开「新增事件」抽屉
  const openCreateEventDrawer = (scope: 'node' | 'page') => {
    if (scope === 'node' && selectedNode) {
      const triggers =
        AVAILABLE_TRIGGERS_BY_KIND[selectedNode.kind] ||
        AVAILABLE_TRIGGERS_BY_KIND.button;
      const firstTrig = triggers[0] || { code: 'onClick', name: '点击组件时' };
      setEventDrawerState({
        scope: 'node',
        nodeId: selectedNode.id,
        mode: 'create',
        draft: {
          id: `evt-${Date.now()}`,
          triggerCode: firstTrig.code,
          triggerName: firstTrig.name,
          actionType: 'showToast',
          targetDesc: `执行「${selectedNode.name}」交互动作`,
          enabled: true,
        },
      });
      openOverlay('eventDrawer');
      return;
    }

    const pageTriggers = AVAILABLE_TRIGGERS_BY_KIND.page;
    const first = pageTriggers[0];
    setEventDrawerState({
      scope: 'page',
      mode: 'create',
      draft: {
        id: `evt-page-${Date.now()}`,
        triggerCode: first.code,
        triggerName: first.name,
        actionType: 'refreshDataSource',
        targetDesc: 'api.v2.formationBatches',
        enabled: true,
      },
    });
    openOverlay('eventDrawer');
  };

  // 打开「编辑已有事件」抽屉
  const openEditEventDrawer = (scope: 'node' | 'page', evt: EventBinding) => {
    setEventDrawerState({
      scope,
      nodeId: scope === 'node' ? selectedNode?.id : undefined,
      mode: 'edit',
      draft: { ...evt },
    });
    openOverlay('eventDrawer');
  };

  // 保存抽屉中的事件配置
  const handleSaveEventDrawer = () => {
    if (!eventDrawerState) return;
    const { scope, nodeId, mode, draft } = eventDrawerState;

    if (scope === 'node' && nodeId) {
      const next = mapNodesRecursive(nodes, nodeId, (n) => {
        const currentEvents = n.events || getDefaultEventsForKind(n.kind);
        const nextEvents =
          mode === 'create'
            ? [...currentEvents, draft]
            : currentEvents.map((item) => (item.id === draft.id ? draft : item));
        return { ...n, events: nextEvents };
      });
      commitNodes(next);
      pushToast(
        mode === 'create'
          ? `已新增组件事件「${draft.triggerName}」`
          : `已更新组件事件「${draft.triggerName}」`,
        'ok'
      );
    } else if (scope === 'page') {
      setPageConfig((prev) => ({
        ...prev,
        events:
          mode === 'create'
            ? [...prev.events, draft]
            : prev.events.map((item) => (item.id === draft.id ? draft : item)),
      }));
      pushToast(
        mode === 'create'
          ? `已新增页面事件「${draft.triggerName}」`
          : `已更新页面事件「${draft.triggerName}」`,
        'ok'
      );
    }

    closeTopOverlay();
  };

  // 推入新节点状态并记录撤销栈
  const commitNodes = (nextNodes: CanvasNode[], markDirty = true) => {
    const sliced = history.slice(0, historyIndex + 1);
    const nextHistory = [...sliced, nextNodes];
    setNodes(nextNodes);
    setHistory(nextHistory);
    setHistoryIndex(nextHistory.length - 1);
    if (markDirty) {
      setUnsavedCount((c) => c + 1);
    }
  };

  const handleUndo = () => {
    if (historyIndex <= 0) return;
    const nextIdx = historyIndex - 1;
    setHistoryIndex(nextIdx);
    setNodes(history[nextIdx]);
    setUnsavedCount((c) => Math.max(0, c - 1));
    pushToast('已撤销上一步画布改动', 'info');
  };

  const handleRedo = () => {
    if (historyIndex >= history.length - 1) return;
    const nextIdx = historyIndex + 1;
    setHistoryIndex(nextIdx);
    setNodes(history[nextIdx]);
    setUnsavedCount((c) => c + 1);
    pushToast('已重做画布改动', 'info');
  };

  // 递归查找任意层级节点（支持顶层 + 插槽 children + 栅格 columns）
  const findNodeRecursive = (list: CanvasNode[], id: string): CanvasNode | null => {
    for (const item of list) {
      if (item.id === id) return item;
      if (item.children) {
        const found = findNodeRecursive(item.children, id);
        if (found) return found;
      }
      if (item.columns) {
        for (const col of item.columns) {
          const found = findNodeRecursive(col, id);
          if (found) return found;
        }
      }
    }
    return null;
  };

  // 递归映射修改任意层级节点
  const mapNodesRecursive = (
    list: CanvasNode[],
    id: string,
    updater: (n: CanvasNode) => CanvasNode
  ): CanvasNode[] =>
    list.map((item) => {
      if (item.id === id) return updater(item);
      return {
        ...item,
        children: item.children
          ? mapNodesRecursive(item.children, id, updater)
          : item.children,
        columns: item.columns
          ? item.columns.map((col) => mapNodesRecursive(col, id, updater))
          : item.columns,
      };
    });

  // 递归移除某个节点并返回 [移除后的树, 被移除的节点]
  const removeNodeRecursive = (
    list: CanvasNode[],
    id: string
  ): { nextList: CanvasNode[]; removed: CanvasNode | null } => {
    let removed: CanvasNode | null = null;
    const walk = (arr: CanvasNode[]): CanvasNode[] => {
      const res: CanvasNode[] = [];
      for (const item of arr) {
        if (item.id === id) {
          removed = item;
          continue;
        }
        res.push({
          ...item,
          children: item.children ? walk(item.children) : item.children,
          columns: item.columns ? item.columns.map((c) => walk(c)) : item.columns,
        });
      }
      return res;
    };
    return { nextList: walk(list), removed };
  };

  const selectedNode = useMemo(
    () => (selectedNodeId ? findNodeRecursive(nodes, selectedNodeId) : null),
    [nodes, selectedNodeId]
  );

  // 更新当前选中节点属性
  const updateSelectedNode = (updater: (node: CanvasNode) => CanvasNode) => {
    if (!selectedNode) return;
    const next = mapNodesRecursive(nodes, selectedNode.id, updater);
    commitNodes(next);
  };

  // 根据物料模板生成新 CanvasNode
  const createNodeFromMaterial = (material: MaterialItem): CanvasNode => {
    const suffix = Math.floor(100 + Math.random() * 899);
    return {
      id: `node-${material.kind}-${suffix}`,
      kind: material.kind,
      name: material.name,
      code: `${material.code}_${suffix}`,
      categoryLabel: material.category,
      categoryTone: material.categoryTone,
      visible: true,
      locked: false,
      permissionKey: `mes:${material.kind}:view`,
      dataSource: `api.v2.${material.kind}Source`,
      refreshInterval: material.kind === 'readout' ? '5000ms' : '15000ms',
      paddingPx: 20,
      radiusPx: 20,
      marginBottomPx: 0,
      surfaceStyle: 'solid',
      events: getDefaultEventsForKind(material.kind),
      children: material.kind === 'emptySlot' ? [] : undefined,
      columns: material.kind === 'grid' ? [[], []] : undefined,
      props: {
        title:
          material.kind === 'topology'
            ? '宁德三期化成工序流向监控'
            : material.kind === 'emptySlot'
              ? '待放置插槽容器'
              : `${material.name}`,
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
          {
            label: '电芯批次号',
            fieldKey: 'batchNo',
            placeholder: '输入批次编码...',
            widthPx: 220,
          },
          {
            label: '所属车间产线',
            fieldKey: 'workshopLine',
            placeholder: '全部化成产线',
            widthPx: 200,
          },
        ],
      },
    };
  };

  // 从物料库插入新节点到指定位置
  const insertMaterialAt = (material: MaterialItem, index: number) => {
    const newNode = createNodeFromMaterial(material);
    const next = [...nodes];
    const safeIdx = Math.max(0, Math.min(next.length, index));
    next.splice(safeIdx, 0, newNode);
    commitNodes(next);
    setSelectedNodeId(newNode.id);
    pushToast(`已将「${material.name}」挂载至画布`, 'ok');
  };

  // 根据鼠标在画布内的 Y 坐标实时计算应当落位的插入索引（上半区插前面，下半区插后面）
  const computeDropIndexFromClientY = (clientY: number): number => {
    const container = canvasListRef.current;
    if (!container) return nodes.length;

    // 如果鼠标正处于已渲染的实时预览框内部，保持当前落点索引稳定不抖动
    const ghostEl = container.querySelector<HTMLElement>('[data-ghost-preview="true"]');
    if (ghostEl && activeDropIndex !== null) {
      const ghostRect = ghostEl.getBoundingClientRect();
      if (clientY >= ghostRect.top - 10 && clientY <= ghostRect.bottom + 10) {
        return activeDropIndex;
      }
    }

    const topLevelWrappers = Array.from(
      container.querySelectorAll<HTMLElement>(':scope > [data-top-node-index]')
    );
    for (let i = 0; i < topLevelWrappers.length; i++) {
      const rect = topLevelWrappers[i].getBoundingClientRect();
      const midY = rect.top + rect.height / 2;
      if (clientY < midY) {
        return i;
      }
    }
    return nodes.length;
  };

  // 当前正在拖拽的组件 1:1 实时预览节点（无论是从左侧组件库拖入，还是画布内既有组件移位，都保持完全一致的组件形态）
  const dragPreviewNode = useMemo<CanvasNode | null>(() => {
    if (draggingMaterial) {
      return createNodeFromMaterial(draggingMaterial);
    }
    if (draggingNodeId) {
      return findNodeRecursive(nodes, draggingNodeId);
    }
    return null;
  }, [draggingMaterial, draggingNodeId, nodes]);

  const resetDragState = () => {
    setIsCanvasDragOver(false);
    setActiveDropIndex(null);
    setActiveContainerTarget(null);
    setDraggingMaterial(null);
    setDraggingNodeId(null);
    setTreeDragNodeId(null);
    setTreeDropIndicator(null);
  };

  // 切换节点锁定状态（画布悬浮 ToolBar 与左侧「页面层级」悬停按钮共用）
  const handleToggleLockNode = (id: string) => {
    const target = findNodeRecursive(nodes, id);
    if (!target) return;
    const nextLocked = !target.locked;
    const next = mapNodesRecursive(nodes, id, (n) => ({
      ...n,
      locked: nextLocked,
    }));
    commitNodes(next);
    pushToast(
      nextLocked ? `已锁定组件「${target.name}」` : `已解锁组件「${target.name}」`,
      'info'
    );
  };

  // 切换节点显隐状态
  const handleToggleVisibleNode = (id: string) => {
    const target = findNodeRecursive(nodes, id);
    if (!target) return;
    const nextVisible = !target.visible;
    const next = mapNodesRecursive(nodes, id, (n) => ({
      ...n,
      visible: nextVisible,
    }));
    commitNodes(next);
    pushToast(
      nextVisible ? `已显示组件「${target.name}」` : `已隐藏组件「${target.name}」`,
      'info'
    );
  };

  // 在「页面层级」树中拖拽节点重排或拖入容器
  const handleTreeDrop = (
    draggedId: string,
    targetId: string,
    position: 'before' | 'after' | 'inside'
  ) => {
    if (!draggedId || draggedId === targetId) return;
    const draggedNode = findNodeRecursive(nodes, draggedId);
    if (!draggedNode || draggedNode.locked) return;
    // 防止把父容器拖进自己的子节点内部
    if (findNodeRecursive([draggedNode], targetId)) return;

    const { nextList, removed } = removeNodeRecursive(nodes, draggedId);
    if (!removed) return;

    if (position === 'inside') {
      const updated = mapNodesRecursive(nextList, targetId, (container) => {
        if (container.kind === 'grid') {
          const cols = container.columns
            ? container.columns.map((c) => [...c])
            : [[], []];
          cols[0] = [...(cols[0] || []), removed];
          return { ...container, columns: cols };
        }
        return {
          ...container,
          children: [...(container.children || []), removed],
        };
      });
      commitNodes(updated);
      setSelectedNodeId(removed.id);
      pushToast(`已将「${removed.name}」移入容器内`, 'ok');
      return;
    }

    const insertRelative = (arr: CanvasNode[]): CanvasNode[] => {
      const idx = arr.findIndex((n) => n.id === targetId);
      if (idx !== -1) {
        const copy = [...arr];
        const insertIdx = position === 'before' ? idx : idx + 1;
        copy.splice(insertIdx, 0, removed);
        return copy;
      }
      return arr.map((item) => ({
        ...item,
        children: item.children ? insertRelative(item.children) : item.children,
        columns: item.columns
          ? item.columns.map((col) => insertRelative(col))
          : item.columns,
      }));
    };

    commitNodes(insertRelative(nextList));
    setSelectedNodeId(removed.id);
    pushToast(`已调整「${removed.name}」层级顺序`, 'info');
  };

  const handleMoveNode = (id: string, direction: 'up' | 'down') => {
    const moveInArray = (arr: CanvasNode[]): CanvasNode[] => {
      const idx = arr.findIndex((n) => n.id === id);
      if (idx !== -1) {
        const targetIdx = direction === 'up' ? idx - 1 : idx + 1;
        if (targetIdx < 0 || targetIdx >= arr.length) return arr;
        const copy = [...arr];
        const [moved] = copy.splice(idx, 1);
        copy.splice(targetIdx, 0, moved);
        return copy;
      }
      return arr.map((item) => ({
        ...item,
        children: item.children ? moveInArray(item.children) : item.children,
        columns: item.columns
          ? item.columns.map((col) => moveInArray(col))
          : item.columns,
      }));
    };
    commitNodes(moveInArray(nodes));
  };

  const handleDuplicateNode = (id: string) => {
    const source = findNodeRecursive(nodes, id);
    if (!source) return;
    const suffix = Math.floor(100 + Math.random() * 899);
    const copy: CanvasNode = {
      ...source,
      id: `${source.id}-copy-${suffix}`,
      name: `${source.name}（副本）`,
      code: `${source.code}_${suffix}`,
      props: { ...source.props },
    };
    const dupInArray = (arr: CanvasNode[]): CanvasNode[] => {
      const idx = arr.findIndex((n) => n.id === id);
      if (idx !== -1) {
        const next = [...arr];
        next.splice(idx + 1, 0, copy);
        return next;
      }
      return arr.map((item) => ({
        ...item,
        children: item.children ? dupInArray(item.children) : item.children,
        columns: item.columns
          ? item.columns.map((col) => dupInArray(col))
          : item.columns,
      }));
    };
    commitNodes(dupInArray(nodes));
    setSelectedNodeId(copy.id);
    pushToast(`已复制节点「${source.name}」`, 'info');
  };

  const handleDeleteNode = (id: string) => {
    const target = findNodeRecursive(nodes, id);
    if (!target) return;
    if (target.locked) {
      pushToast(`节点「${target.name}」已锁定，无法移除`, 'danger');
      return;
    }
    if (nodes.length <= 1 && nodes[0]?.id === id) {
      pushToast('画布至少需保留一个根业务组件', 'danger');
      return;
    }
    const { nextList } = removeNodeRecursive(nodes, id);
    commitNodes(nextList);
    if (selectedNodeId === id && nextList[0]) {
      setSelectedNodeId(nextList[0].id);
    }
    pushToast(`已移除组件「${target.name}」`, 'danger');
  };

  // 执行在指定顶层索引处的放置（兼容左侧新组件拖入 & 画布已有组件拖动移位）
  const executeDropAtTopIndex = (targetIndex: number) => {
    if (draggingMaterial) {
      insertMaterialAt(draggingMaterial, targetIndex);
    } else if (draggingNodeId) {
      const topOldIdx = nodes.findIndex((n) => n.id === draggingNodeId);
      const { nextList, removed } = removeNodeRecursive(nodes, draggingNodeId);
      if (removed) {
        let finalIdx = targetIndex;
        if (topOldIdx !== -1 && topOldIdx < targetIndex) {
          finalIdx = Math.max(0, targetIndex - 1);
        }
        const updated = [...nextList];
        updated.splice(Math.min(updated.length, Math.max(0, finalIdx)), 0, removed);
        commitNodes(updated);
        setSelectedNodeId(removed.id);
        pushToast(`已移动「${removed.name}」位置`, 'info');
      }
    }
    resetDragState();
  };

  // 执行在容器内部（插槽容器或栅格列）的直接放置
  const executeDropInsideContainer = (containerId: string, colIndex?: number) => {
    if (draggingNodeId === containerId) {
      resetDragState();
      return;
    }
    let nodeToInsert: CanvasNode | null = null;
    let baseTree = nodes;

    if (draggingMaterial) {
      nodeToInsert = createNodeFromMaterial(draggingMaterial);
    } else if (draggingNodeId) {
      const res = removeNodeRecursive(nodes, draggingNodeId);
      baseTree = res.nextList;
      nodeToInsert = res.removed;
    }

    if (!nodeToInsert) {
      resetDragState();
      return;
    }

    const inserted = nodeToInsert;
    const nextTree = mapNodesRecursive(baseTree, containerId, (container) => {
      if (typeof colIndex === 'number') {
        const cols = container.columns ? [...container.columns.map((c) => [...c])] : [[], []];
        while (cols.length <= colIndex) cols.push([]);
        cols[colIndex].push(inserted);
        return { ...container, columns: cols };
      }
      const children = container.children ? [...container.children, inserted] : [inserted];
      return { ...container, children };
    });

    commitNodes(nextTree);
    setSelectedNodeId(inserted.id);
    pushToast(`已将「${inserted.name}」放入容器内`, 'ok');
    resetDragState();
  };

  const handleSaveDraft = () => {
    setUnsavedCount(0);
    pushToast('页面模型与权限绑定已保存至本地草稿', 'ok');
  };

  const handleDiscardChanges = () => {
    setNodes(INITIAL_CANVAS_NODES);
    setHistory([INITIAL_CANVAS_NODES]);
    setHistoryIndex(0);
    setUnsavedCount(0);
    setSelectedNodeId('node-readout-1');
    pushToast('已还原全部未保存改动', 'info');
  };

  const filteredMaterials = useMemo(() => {
    const q = materialSearch.trim().toLowerCase();
    if (!q) return MATERIAL_LIBRARY;
    return MATERIAL_LIBRARY.filter(
      (m) =>
        m.name.toLowerCase().includes(q) ||
        m.category.toLowerCase().includes(q) ||
        m.code.toLowerCase().includes(q)
    );
  }, [materialSearch]);

  const checkedRowsCount = useMemo(
    () => tableRecords.filter((r) => r.checked).length,
    [tableRecords]
  );

  const topOverlay = overlayStack[overlayStack.length - 1] || null;

  return (
    <div className="pg-stage">
      {/* 10px 深色机身外框 #17191d + 38px 圆角 */}
      <div className="pg-chassis">
        {/* 顶栏（64px 高，平时透明，画布滚动后凝结出玻璃面与投影） */}
        <header
          style={{ height: 'var(--topbar-h)' }}
          className={`px-5 flex items-center justify-between gap-4 shrink-0 z-20 transition-all duration-300 ${
            canvasScrolled
              ? 'm-rail border-t-0 border-x-0 shadow-[var(--e1)]'
              : 'border-b border-[var(--line)] bg-transparent'
          }`}
        >
          {/* 左侧：返回 + 品牌标 + 面包屑 */}
          <div className="flex items-center gap-3 min-w-0">
            <button
              type="button"
              aria-label="返回应用页面列表"
              title="返回应用页面列表"
              onClick={() => pushToast('已记录当前工站上下文，随时可返回', 'info')}
              className="pg-icon-btn pg-icon-btn-bordered"
            >
              <IconArrowLeft size={15} />
            </button>

            {/* 34x34 深色圆角块 + 9px 青柠发光点 */}
            <div className="pg-brand-mark" title="元搭企业级低代码仪器工作台">
              <span className="pg-brand-dot" />
            </div>

            {/* 面包屑 12.5px，当前位置 620 黑 */}
            <nav aria-label="页面位置面包屑" className="hidden sm:flex items-center gap-1.5 text-[12.5px] truncate">
              <span className="text-[var(--ink-soft)]">宁德三期制造执行</span>
              <span className="text-[var(--ink-faint)]">/</span>
              <span className="text-[var(--ink-soft)]">工艺工作台设计器</span>
              <span className="text-[var(--ink-faint)]">/</span>
              <span className="font-[620] text-[var(--ink)] truncate">
                {pageConfig.name}
              </span>
              <span className="pg-tag pg-tag-ok ml-1.5">
                <span className="pg-tag-dot" />
                已发布 v2.4
              </span>
            </nav>
          </div>

          {/* 中间：命令面板入口 240x34 + 右侧 ⌘K 键帽 20 高、圆角 6 */}
          <div className="hidden lg:flex items-center gap-2">
            <button
              type="button"
              onClick={() => openOverlay('command')}
              style={{ width: '240px', height: '34px' }}
              className="rounded-[12px] border border-[var(--line-strong)] bg-[var(--input-bg)] shadow-[var(--edge)] px-3 flex items-center justify-between text-[12.5px] text-[var(--ink-soft)] hover:border-[var(--line-hover)] hover:text-[var(--ink)] transition-all cursor-pointer"
            >
              <span className="inline-flex items-center gap-2">
                <IconSearch size={14} />
                <span>搜索组件、属性或指令...</span>
              </span>
              <kbd className="h-[20px] px-1.5 rounded-[6px] bg-[var(--hover-bg)] border border-[var(--line)] font-mono-num text-[11px] text-[var(--ink-muted)] inline-flex items-center">
                ⌘K
              </kbd>
            </button>
          </div>

          {/* 右侧：撤销/重做 32x32 图标按钮 + 视图工具 + 保存与发布 */}
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 pr-2 border-r border-[var(--line)]">
              <button
                type="button"
                aria-label="撤销上一步操作"
                title="撤销上一步操作"
                disabled={historyIndex <= 0}
                onClick={handleUndo}
                className="pg-icon-btn"
              >
                <IconUndo size={15} />
              </button>
              <button
                type="button"
                aria-label="重做上一步操作"
                title="重做上一步操作"
                disabled={historyIndex >= history.length - 1}
                onClick={handleRedo}
                className="pg-icon-btn"
              >
                <IconRedo size={15} />
              </button>
              <button
                type="button"
                aria-label="查看页面结构代码"
                title="查看页面结构代码"
                onClick={() => openOverlay('schema')}
                className="pg-icon-btn"
              >
                <IconCode size={15} />
              </button>
              <button
                type="button"
                aria-label={theme === 'light' ? '切换深色模式' : '切换浅色模式'}
                title={theme === 'light' ? '切换深色模式' : '切换浅色模式'}
                onClick={() => setTheme((t) => (t === 'light' ? 'dark' : 'light'))}
                className="pg-icon-btn"
              >
                {theme === 'light' ? <IconMoon size={15} /> : <IconSun size={15} />}
              </button>
            </div>

            <button
              type="button"
              onClick={() => setPreviewMode((p) => !p)}
              className={`pg-btn pg-btn-secondary ${
                previewMode ? '!border-[var(--blue)] !text-[var(--blue)]' : ''
              }`}
            >
              <IconEye size={15} />
              {previewMode ? '退出运行预览' : '预览运行态'}
            </button>

            <button
              type="button"
              onClick={handleSaveDraft}
              className="pg-btn pg-btn-secondary"
            >
              保存草稿
            </button>

            <button
              type="button"
              onClick={() => openOverlay('publish')}
              className="pg-btn pg-btn-primary"
            >
              发布上线
            </button>
          </div>
        </header>

        {/* 三栏主体工作区：gap 12px，左 236 / 中 1fr / 右 312，各自圆角 20 实色面 #fbfcfc */}
        <div className="flex-1 min-h-0 p-3 flex flex-col lg:flex-row gap-[12px] overflow-hidden">
          {/* 左栏：组件物料 + 页面层级（圆角 20、1px --line、实色 #fbfcfc） */}
          {!isLeftCollapsed && (
            <aside
              aria-label="组件物料与页面层级面板"
              className="w-full lg:w-[236px] shrink-0 m-solid rounded-[20px] flex flex-col min-h-0 overflow-hidden"
            >
            {/* 面板头：分段控件切换（组件物料在前默认选中，页面层级在后） */}
            <div className="p-3 border-b border-[var(--line)] space-y-2.5">
              <div className="flex items-center gap-1.5">
                <div className="m-groove grid grid-cols-2 gap-1 flex-1">
                  <button
                    type="button"
                    onClick={() => setLeftTab('materials')}
                    className={`h-[26px] rounded-[8px] text-[12px] font-[600] transition-all cursor-pointer ${
                      leftTab === 'materials'
                        ? 'm-pill text-[var(--blue)]'
                        : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'
                    }`}
                  >
                    组件物料
                  </button>
                  <button
                    type="button"
                    onClick={() => setLeftTab('tree')}
                    className={`h-[26px] rounded-[8px] text-[12px] font-[600] transition-all cursor-pointer ${
                      leftTab === 'tree'
                        ? 'm-pill text-[var(--blue)]'
                        : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'
                    }`}
                  >
                    页面层级
                  </button>
                </div>
                <button
                  type="button"
                  aria-label="收起左侧组件物料面板"
                  title="收起左侧面板"
                  onClick={() => setLeftPanelCollapsed(true)}
                  className="w-[28px] h-[28px] rounded-[8px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] transition-colors cursor-pointer shrink-0"
                >
                  <IconPanelLeftClose size={14} />
                </button>
              </div>

              {leftTab === 'materials' && (
                <div className="relative">
                  <input
                    type="text"
                    value={materialSearch}
                    onChange={(e) => setMaterialSearch(e.target.value)}
                    placeholder="搜索组件物料..."
                    aria-label="检索组件物料"
                    className="pg-input pg-input-sm pl-7 pr-7"
                  />
                  <span className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--ink-faint)] pointer-events-none">
                    <IconSearch size={13} />
                  </span>
                  {materialSearch ? (
                    <button
                      type="button"
                      aria-label="清空搜索"
                      title="清空搜索"
                      onClick={() => setMaterialSearch('')}
                      className="absolute right-1.5 top-1/2 -translate-y-1/2 w-5 h-5 rounded-[5px] inline-flex items-center justify-center text-[12px] text-[var(--ink-faint)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] cursor-pointer"
                    >
                      ×
                    </button>
                  ) : (
                    <button
                      type="button"
                      aria-label="全部收起或展开分组"
                      title={
                        Object.values(groupCollapsed).some((v) => !v)
                          ? '全部收起分组'
                          : '全部展开分组'
                      }
                      onClick={() => {
                        const anyOpen = Object.values(groupCollapsed).some((v) => !v);
                        setGroupCollapsed({
                          布局容器: anyOpen,
                          表单输入: anyOpen,
                          数据展示: anyOpen,
                          基础操作: anyOpen,
                        });
                      }}
                      className="absolute right-1.5 top-1/2 -translate-y-1/2 w-5 h-5 rounded-[5px] inline-flex items-center justify-center text-[var(--ink-faint)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] transition-colors cursor-pointer"
                    >
                      <IconChevronDown
                        size={12}
                        className={`transition-transform duration-200 ${
                          Object.values(groupCollapsed).some((v) => !v) ? '' : '-rotate-90'
                        }`}
                      />
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* 左栏主体列表 */}
            <div className="flex-1 overflow-y-auto p-2.5">
              {leftTab === 'materials' ? (
                /* 组件物料列表：精致分组头 + 3列上图下字仪器键帽卡片（34x26 大图标底座 + 11px 组件名） */
                <div className="space-y-2.5">
                  {(
                    [
                      {
                        name: '布局容器' as MaterialGroup,
                        dotColor: 'var(--cat-sky-fg)',
                        dotRing: 'var(--cat-sky-bg)',
                      },
                      {
                        name: '表单输入' as MaterialGroup,
                        dotColor: 'var(--cat-sand-fg)',
                        dotRing: 'var(--cat-sand-bg)',
                      },
                      {
                        name: '数据展示' as MaterialGroup,
                        dotColor: 'var(--cat-lilac-fg)',
                        dotRing: 'var(--cat-lilac-bg)',
                      },
                      {
                        name: '基础操作' as MaterialGroup,
                        dotColor: 'var(--cat-rose-fg)',
                        dotRing: 'var(--cat-rose-bg)',
                      },
                    ]
                  ).map((groupMeta) => {
                    const groupName = groupMeta.name;
                    const items = filteredMaterials.filter(
                      (m) => m.category === groupName
                    );
                    if (items.length === 0) return null;
                    const isSearching = materialSearch.trim().length > 0;
                    const collapsed = !isSearching && Boolean(groupCollapsed[groupName]);

                    return (
                      <div key={groupName} className="space-y-1.5">
                        {/* 可折叠分组头（无 hover 背景或字色跳变） */}
                        <button
                          type="button"
                          onClick={() =>
                            setGroupCollapsed((prev) => ({
                              ...prev,
                              [groupName]: !prev[groupName],
                            }))
                          }
                          className="pg-group-header"
                        >
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="pg-group-dot"
                              style={{
                                backgroundColor: groupMeta.dotColor,
                                boxShadow: `0 0 0 3px ${groupMeta.dotRing}`,
                              }}
                            />
                            <span className="text-[12px] font-[640] tracking-[-0.01em] text-[var(--ink-muted)]">
                              {groupName}
                            </span>
                          </span>
                          <span className="inline-flex items-center gap-1.5">
                            <span className="pg-group-count">{items.length}</span>
                            <IconChevronDown
                              size={12}
                              className={`text-[var(--ink-soft)] transition-transform duration-200 ${
                                collapsed ? '-rotate-90' : ''
                              }`}
                            />
                          </span>
                        </button>

                        {!collapsed && (
                          <div className="grid grid-cols-3 gap-1.5 pb-1">
                            {items.map((mat) => (
                              <button
                                key={mat.id}
                                type="button"
                                draggable
                                title={`${mat.name} · ${mat.desc}`}
                                onClick={() => insertMaterialAt(mat, nodes.length)}
                                onDragStart={(e) => {
                                  setDraggingMaterial(mat);
                                  e.dataTransfer.setData('text/plain', mat.id);
                                }}
                                onDragEnd={() => {
                                  setDraggingMaterial(null);
                                  setActiveDropIndex(null);
                                }}
                                className="pg-comp-tile group"
                              >
                                {/* 上层 100% 满宽 37px 凹槽微缩画布展台（图标放大至 19px，内外同心圆角） */}
                                <span className="pg-comp-icon-box">
                                  <IconComponentKind kind={mat.kind} size={19} />
                                </span>

                                {/* 下层 11px 精致小字组件名 */}
                                <span className="h-[18px] inline-flex items-center justify-center text-[11px] leading-none font-[560] tracking-[-0.01em] text-[var(--ink-muted)] group-hover:text-[var(--blue)] group-hover:font-[620] transition-colors truncate max-w-full">
                                  {mat.name}
                                </span>
                              </button>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ) : (
                /* 页面层级视图：支持点击根页面切换页面级配置、树节点拖拽排序，且节点操作按钮仅在鼠标悬浮时展示 */
                <div className="space-y-1">
                  {/* 根页面节点：点击即可切换到「页面级」配置样式与事件 */}
                  <div
                    onClick={() => setSelectedNodeId(null)}
                    style={{
                      height: '34px',
                      background: selectedNodeId === null ? 'var(--blue-soft)' : undefined,
                      color: selectedNodeId === null ? 'var(--blue)' : 'var(--ink)',
                    }}
                    className={`px-2 rounded-[9px] flex items-center justify-between gap-2 text-[12.5px] font-[640] cursor-pointer transition-colors ${
                      selectedNodeId === null ? '' : 'hover:bg-[var(--hover-bg)]'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 min-w-0">
                      <button
                        type="button"
                        aria-label="折叠或展开页面根节点"
                        onClick={(e) => {
                          e.stopPropagation();
                          setTreeExpanded((prev) => ({ ...prev, root: !prev.root }));
                        }}
                        className="w-[20px] h-[20px] rounded-[5px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:bg-[var(--hover-bg)] cursor-pointer shrink-0"
                      >
                        <IconChevronRight
                          size={12}
                          className={`transition-transform duration-200 ${
                            treeExpanded.root ? 'rotate-90' : ''
                          }`}
                        />
                      </button>
                      <span
                        className={
                          selectedNodeId === null
                            ? 'text-[var(--blue)] shrink-0'
                            : 'text-[var(--ink-soft)] shrink-0'
                        }
                      >
                        <IconPage size={14} />
                      </span>
                      <span className="truncate">{pageConfig.name}</span>
                    </div>
                    <span
                      className={`text-[10.5px] px-1.5 py-0.5 rounded-[5px] shrink-0 ${
                        selectedNodeId === null
                          ? 'bg-[var(--blue)] text-white font-[600]'
                          : 'text-[var(--ink-faint)] bg-[var(--hover-bg)]'
                      }`}
                    >
                      页面
                    </span>
                  </div>

                  {/* 子节点列表（带垂直层级丝线 + 仅在悬浮时显现拖拽/锁定/显隐/删除操作） */}
                  {treeExpanded.root && (
                    <div className="ml-[11px] pl-[9px] border-l border-[var(--line-strong)] space-y-0.5">
                      {(() => {
                        const renderHierarchyNode = (
                          node: CanvasNode,
                          depth = 0
                        ): React.ReactNode => {
                          const active = node.id === selectedNode?.id;
                          const isContainer =
                            node.kind === 'emptySlot' ||
                            node.kind === 'grid' ||
                            node.kind === 'card' ||
                            node.kind === 'tabs';
                          const nestedChildren = [
                            ...(node.children || []),
                            ...(node.columns ? node.columns.flat() : []),
                          ];
                          const isNodeExpanded = treeExpanded[node.id] ?? true;
                          const dropBefore =
                            treeDropIndicator?.targetId === node.id &&
                            treeDropIndicator.position === 'before';
                          const dropAfter =
                            treeDropIndicator?.targetId === node.id &&
                            treeDropIndicator.position === 'after';
                          const dropInside =
                            treeDropIndicator?.targetId === node.id &&
                            treeDropIndicator.position === 'inside';

                          return (
                            <div key={node.id} className="space-y-0.5">
                              <div
                                draggable={!node.locked}
                                onClick={() => setSelectedNodeId(node.id)}
                                onDragStart={(e) => {
                                  if (node.locked) {
                                    e.preventDefault();
                                    return;
                                  }
                                  e.stopPropagation();
                                  setTreeDragNodeId(node.id);
                                  setDraggingNodeId(node.id);
                                  e.dataTransfer.setData('text/plain', node.id);
                                }}
                                onDragOver={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                  const draggedId = treeDragNodeId || draggingNodeId;
                                  if (!draggedId || draggedId === node.id) return;
                                  const rect = e.currentTarget.getBoundingClientRect();
                                  const ratio = (e.clientY - rect.top) / rect.height;
                                  let position: 'before' | 'after' | 'inside' = 'after';
                                  if (ratio < 0.32) {
                                    position = 'before';
                                  } else if (isContainer && ratio <= 0.68) {
                                    position = 'inside';
                                  } else {
                                    position = 'after';
                                  }
                                  setTreeDropIndicator({ targetId: node.id, position });
                                }}
                                onDragLeave={(e) => {
                                  if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                                    if (treeDropIndicator?.targetId === node.id) {
                                      setTreeDropIndicator(null);
                                    }
                                  }
                                }}
                                onDrop={(e) => {
                                  e.preventDefault();
                                  e.stopPropagation();
                                  const draggedId = treeDragNodeId || draggingNodeId;
                                  if (draggedId && treeDropIndicator) {
                                    handleTreeDrop(
                                      draggedId,
                                      node.id,
                                      treeDropIndicator.position
                                    );
                                  }
                                  resetDragState();
                                }}
                                onDragEnd={resetDragState}
                                style={{
                                  height: '32px',
                                  paddingLeft: `${6 + depth * 12}px`,
                                  background: active ? 'var(--blue-soft)' : undefined,
                                  color: active ? 'var(--blue)' : 'var(--ink)',
                                  fontWeight: active ? 620 : 500,
                                  outline: dropInside
                                    ? '1.5px dashed var(--blue)'
                                    : undefined,
                                }}
                                className={`group/tree relative pr-1.5 rounded-[8px] flex items-center justify-between gap-1.5 text-[12.5px] cursor-pointer transition-colors ${
                                  active ? '' : 'hover:bg-[var(--hover-bg)]'
                                } ${!node.visible ? 'opacity-60' : ''}`}
                              >
                                {/* 拖拽排序落位指示线 */}
                                {dropBefore && (
                                  <span className="absolute -top-[2px] left-1 right-1 h-[2px] rounded-full bg-[var(--blue)] pointer-events-none z-10" />
                                )}
                                {dropAfter && (
                                  <span className="absolute -bottom-[2px] left-1 right-1 h-[2px] rounded-full bg-[var(--blue)] pointer-events-none z-10" />
                                )}

                                {/* 左侧：悬浮时显现的拖拽手柄 + 容器折叠箭头 + 组件图标 + 名称 */}
                                <div className="flex items-center gap-1.5 min-w-0">
                                  {nestedChildren.length > 0 ? (
                                    <button
                                      type="button"
                                      aria-label="展开或收起子组件"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        setTreeExpanded((prev) => ({
                                          ...prev,
                                          [node.id]: !isNodeExpanded,
                                        }));
                                      }}
                                      className="w-[16px] h-[16px] rounded-[4px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:bg-[var(--hover-bg)] shrink-0"
                                    >
                                      <IconChevronRight
                                        size={11}
                                        className={`transition-transform duration-200 ${
                                          isNodeExpanded ? 'rotate-90' : ''
                                        }`}
                                      />
                                    </button>
                                  ) : (
                                    <span
                                      title={node.locked ? '已锁定，不可拖拽' : '按住拖拽排序'}
                                      className={`w-[14px] inline-flex items-center justify-center text-[var(--ink-faint)] opacity-0 group-hover/tree:opacity-100 transition-opacity shrink-0 ${
                                        node.locked ? 'cursor-not-allowed opacity-0' : 'cursor-grab'
                                      }`}
                                    >
                                      <IconGrip size={12} />
                                    </span>
                                  )}

                                  <span
                                    className={
                                      active
                                        ? 'text-[var(--blue)] shrink-0'
                                        : 'text-[var(--ink-soft)] shrink-0'
                                    }
                                  >
                                    <IconComponentKind kind={node.kind} size={14} />
                                  </span>
                                  <span className="truncate">{node.name}</span>
                                </div>

                                {/* 右侧：默认态仅静默显示锁定/隐藏微标或子数，鼠标悬浮时切换为操作按钮组 */}
                                <div className="flex items-center shrink-0">
                                  {/* 非悬浮态静默状态标记 */}
                                  <div className="flex items-center gap-1 group-hover/tree:hidden">
                                    {node.locked && (
                                      <span
                                        title="已锁定"
                                        className="text-[var(--ink-faint)] inline-flex items-center"
                                      >
                                        <IconLock size={12} />
                                      </span>
                                    )}
                                    {!node.visible && (
                                      <span
                                        title="已隐藏"
                                        className="text-[var(--ink-faint)] inline-flex items-center"
                                      >
                                        <IconEyeOff size={12} />
                                      </span>
                                    )}
                                    {nestedChildren.length > 0 && (
                                      <span className="pg-group-count">
                                        {nestedChildren.length}
                                      </span>
                                    )}
                                  </div>

                                  {/* 仅鼠标悬浮（group-hover/tree）时显现的节点操作栏：锁定、显隐、删除 */}
                                  <div
                                    onClick={(e) => e.stopPropagation()}
                                    className="hidden group-hover/tree:inline-flex items-center gap-0.5"
                                  >
                                    <button
                                      type="button"
                                      aria-label={node.locked ? '解锁节点' : '锁定节点'}
                                      title={node.locked ? '已锁定（点击解锁）' : '锁定节点'}
                                      onClick={() => handleToggleLockNode(node.id)}
                                      className={`w-[22px] h-[22px] rounded-[6px] inline-flex items-center justify-center transition-colors cursor-pointer ${
                                        node.locked
                                          ? 'text-[var(--blue)] bg-[var(--blue-soft)]'
                                          : 'text-[var(--ink-soft)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)]'
                                      }`}
                                    >
                                      {node.locked ? (
                                        <IconLock size={12} />
                                      ) : (
                                        <IconUnlock size={12} />
                                      )}
                                    </button>
                                    <button
                                      type="button"
                                      aria-label={node.visible ? '隐藏节点' : '显示节点'}
                                      title={node.visible ? '隐藏节点' : '显示节点'}
                                      onClick={() => handleToggleVisibleNode(node.id)}
                                      className="w-[22px] h-[22px] rounded-[6px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] transition-colors cursor-pointer"
                                    >
                                      {node.visible ? (
                                        <IconEye size={12} />
                                      ) : (
                                        <IconEyeOff size={12} />
                                      )}
                                    </button>
                                    <button
                                      type="button"
                                      aria-label="删除节点"
                                      title={node.locked ? '节点已锁定，无法删除' : '删除节点'}
                                      disabled={node.locked}
                                      onClick={() => handleDeleteNode(node.id)}
                                      className="w-[22px] h-[22px] rounded-[6px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--red-text)] hover:bg-[var(--red-soft)] disabled:opacity-35 transition-colors cursor-pointer"
                                    >
                                      <IconTrash size={12} />
                                    </button>
                                  </div>
                                </div>
                              </div>
                              {nestedChildren.length > 0 && isNodeExpanded && (
                                <div className="ml-[12px] pl-[8px] border-l border-[var(--line)] space-y-0.5">
                                  {nestedChildren.map((child) =>
                                    renderHierarchyNode(child, depth + 1)
                                  )}
                                </div>
                              )}
                            </div>
                          );
                        };
                        return nodes.map((n) => renderHierarchyNode(n, 0));
                      })()}
                    </div>
                  )}
                </div>
              )}
            </div>

            {/* 左栏底部：快速插入虚线扩展容器 */}
            <div className="p-3 border-t border-[var(--line)] bg-[var(--input-bg)] flex items-center justify-between">
              <span className="text-[12px] text-[var(--ink-soft)]">
                画布节点 <strong className="font-mono-num text-[var(--ink)]">{nodes.length}</strong> 个
              </span>
              <button
                type="button"
                onClick={() => {
                  const slotMat =
                    MATERIAL_LIBRARY.find((m) => m.kind === 'emptySlot') ||
                    MATERIAL_LIBRARY[0];
                  insertMaterialAt(slotMat, nodes.length);
                }}
                className="pg-btn pg-btn-sm pg-btn-secondary"
              >
                <IconPlus size={13} />
                新建扩展槽
              </button>
            </div>
            </aside>
          )}

          {/* 中栏：低代码画布舞台（默认开启参考点阵 + 拖拽到任意组件位置实时感知插入点，松开即就地放下） */}
          <main
            aria-label="低代码可视化画布工作区"
            onDragOver={(e) => {
              e.preventDefault();
              if (draggingMaterial || draggingNodeId) {
                if (!isCanvasDragOver) setIsCanvasDragOver(true);
                if (!activeContainerTarget) {
                  const idx = computeDropIndexFromClientY(e.clientY);
                  if (activeDropIndex !== idx) {
                    setActiveDropIndex(idx);
                  }
                }
              }
            }}
            onDragLeave={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                setIsCanvasDragOver(false);
                setActiveDropIndex(null);
                setActiveContainerTarget(null);
              }
            }}
            onDrop={(e) => {
              e.preventDefault();
              if (activeContainerTarget) {
                executeDropInsideContainer(
                  activeContainerTarget.nodeId,
                  activeContainerTarget.colIndex
                );
                return;
              }
              const computedIdx =
                activeDropIndex !== null
                  ? activeDropIndex
                  : computeDropIndexFromClientY(e.clientY);
              executeDropAtTopIndex(computedIdx);
            }}
            style={{
              borderColor: isCanvasDragOver ? 'var(--blue)' : undefined,
              boxShadow: isCanvasDragOver
                ? 'inset 0 0 0 2px var(--blue-soft)'
                : undefined,
              backgroundColor:
                pageConfig.canvasBgTone === 'pure'
                  ? 'var(--surface-solid)'
                  : pageConfig.canvasBgTone === 'warm'
                    ? 'var(--input-bg)'
                    : undefined,
            }}
            className="pg-canvas-stage flex-1 min-w-0 rounded-[20px] border border-[var(--line)] flex flex-col min-h-0 overflow-hidden relative transition-all"
          >
            {/* 参考点阵（由页面级样式属性 showDots 控制） */}
            {pageConfig.showDots && <div className="pg-canvas-dots" />}

            {/* 左上角：当左侧面板被手动收起且非运行预览态时，悬浮显示「展开组件物料」胶囊 */}
            {isLeftCollapsed && !previewMode && (
              <div className="absolute top-2.5 left-3 z-30">
                <button
                  type="button"
                  title="展开左侧组件物料与页面层级面板"
                  onClick={(e) => {
                    e.stopPropagation();
                    setLeftPanelCollapsed(false);
                  }}
                  className="h-[30px] px-2.5 rounded-[999px] m-pill flex items-center gap-1.5 text-[12px] font-[600] text-[var(--ink)] hover:text-[var(--blue)] hover:border-[var(--line-hover)] transition-all cursor-pointer"
                >
                  <span className="text-[var(--blue)] inline-flex items-center">
                    <IconPanelLeftOpen size={14} />
                  </span>
                  <span>展开物料</span>
                </button>
              </div>
            )}

            {/* 右上角收纳式视口切换胶囊（点击展开珍珠玻璃 m-float 菜单，支持终端切换 + 自适应/常用断点 + 自定义像素宽度） */}
            <div className="absolute top-2.5 right-3 z-30 flex items-center gap-2">
              <div className="relative">
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  setViewportMenuOpen((o) => !o);
                }}
                className="h-[30px] px-2.5 rounded-[999px] m-pill flex items-center gap-1.5 text-[12px] font-[600] text-[var(--ink)] hover:border-[var(--line-hover)] transition-all cursor-pointer"
              >
                <span className="text-[var(--blue)]">
                  {deviceMode === 'mobile' ? (
                    <IconMobile size={14} />
                  ) : deviceMode === 'tablet' ? (
                    <IconTablet size={14} />
                  ) : (
                    <IconDesktop size={14} />
                  )}
                </span>
                <span>
                  {deviceMode === 'mobile'
                    ? '移动端'
                    : deviceMode === 'tablet'
                      ? '平板端'
                      : '桌面端'}
                </span>
                <span className="font-mono-num text-[11px] text-[var(--ink-soft)]">
                  {viewportWidth === 'auto' ? '自适应' : `${viewportWidth}px`}
                </span>
                <IconChevronDown
                  size={12}
                  className={`text-[var(--ink-soft)] transition-transform duration-200 ${
                    viewportMenuOpen ? 'rotate-180' : ''
                  }`}
                />
              </button>

              {viewportMenuOpen && (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="mt-1.5 w-[248px] p-2.5 rounded-[16px] m-float space-y-2.5 right-0 absolute"
                >
                  {/* 1. 终端设备类型切换 */}
                  <div className="m-groove grid grid-cols-3 gap-1">
                    {(
                      [
                        {
                          key: 'desktop' as const,
                          label: '桌面',
                          defaultW: 'auto' as const,
                          icon: <IconDesktop size={13} />,
                        },
                        {
                          key: 'tablet' as const,
                          label: '平板',
                          defaultW: 768,
                          icon: <IconTablet size={13} />,
                        },
                        {
                          key: 'mobile' as const,
                          label: '手机',
                          defaultW: 390,
                          icon: <IconMobile size={13} />,
                        },
                      ]
                    ).map((dev) => {
                      const active = deviceMode === dev.key;
                      return (
                        <button
                          key={dev.key}
                          type="button"
                          onClick={() => {
                            setDeviceMode(dev.key);
                            setViewportWidth(dev.defaultW);
                            if (typeof dev.defaultW === 'number') {
                              setCustomWidthInput(String(dev.defaultW));
                            }
                          }}
                          className={`h-[26px] rounded-[8px] text-[12px] font-[600] inline-flex items-center justify-center gap-1 transition-all cursor-pointer ${
                            active
                              ? 'm-pill text-[var(--blue)]'
                              : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'
                          }`}
                        >
                          {dev.icon}
                          <span>{dev.label}</span>
                        </button>
                      );
                    })}
                  </div>

                  {/* 2. 当前设备下的灵活宽度参考项（不锁死单一分辨率） */}
                  <div className="space-y-0.5">
                    {(deviceMode === 'desktop'
                      ? [
                          { label: '自适应画布满宽', value: 'auto' as const, tag: '100%' },
                          { label: '紧凑桌面视口', value: 1280, tag: '1280px' },
                          { label: '标准宽屏视口', value: 1440, tag: '1440px' },
                        ]
                      : deviceMode === 'tablet'
                        ? [
                            { label: '平板竖屏标准', value: 768, tag: '768px' },
                            { label: '平板宽幅竖屏', value: 834, tag: '834px' },
                            { label: '平板横屏模式', value: 1024, tag: '1024px' },
                          ]
                        : [
                            { label: '紧凑手机视口', value: 375, tag: '375px' },
                            { label: '主流手机视口', value: 390, tag: '390px' },
                            { label: '大屏手机视口', value: 430, tag: '430px' },
                          ]
                    ).map((preset) => {
                      const selected = viewportWidth === preset.value;
                      return (
                        <button
                          key={String(preset.value)}
                          type="button"
                          onClick={() => {
                            setViewportWidth(preset.value);
                            if (typeof preset.value === 'number') {
                              setCustomWidthInput(String(preset.value));
                            }
                            setViewportMenuOpen(false);
                          }}
                          className={`w-full h-[30px] px-2.5 rounded-[8px] flex items-center justify-between text-[12px] transition-colors cursor-pointer ${
                            selected
                              ? 'text-[var(--blue)] font-[620] bg-[var(--blue-soft)]'
                              : 'text-[var(--ink)] hover:bg-[var(--hover-bg)]'
                          }`}
                        >
                          <span>{preset.label}</span>
                          <span className="inline-flex items-center gap-1.5 font-mono-num text-[11px] text-[var(--ink-soft)]">
                            <span>{preset.tag}</span>
                            {selected && (
                              <span className="text-[var(--blue)]">
                                <IconCheck size={11} />
                              </span>
                            )}
                          </span>
                        </button>
                      );
                    })}
                  </div>

                  {/* 3. 自由输入任意视口宽度 px */}
                  <div className="pt-2 border-t border-[var(--line)] flex items-center justify-between gap-2">
                    <span className="text-[11.5px] text-[var(--ink-soft)] shrink-0">
                      自定义宽度
                    </span>
                    <div className="flex items-center gap-1">
                      <div className="relative w-[92px]">
                        <input
                          type="number"
                          min={320}
                          max={2560}
                          value={customWidthInput}
                          onChange={(e) => setCustomWidthInput(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                              const n = Math.min(
                                2560,
                                Math.max(320, Number(customWidthInput) || 1280)
                              );
                              setViewportWidth(n);
                              setCustomWidthInput(String(n));
                              setViewportMenuOpen(false);
                            }
                          }}
                          aria-label="自定义画布像素宽度"
                          className="pg-input pg-input-sm font-mono-num text-[12px] pr-6"
                        />
                        <sup className="absolute right-2 top-2 font-mono-num text-[10px] text-[var(--ink-faint)]">
                          px
                        </sup>
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          const n = Math.min(
                            2560,
                            Math.max(320, Number(customWidthInput) || 1280)
                          );
                          setViewportWidth(n);
                          setCustomWidthInput(String(n));
                          setViewportMenuOpen(false);
                        }}
                        className="pg-btn pg-btn-sm pg-btn-secondary !px-2.5"
                      >
                        应用
                      </button>
                    </div>
                  </div>
                </div>
              )}
              </div>

              {/* 当右侧配置面板被手动收起且非运行预览态时，并列显示「展开配置」胶囊 */}
              {isRightCollapsed && !previewMode && (
                <button
                  type="button"
                  title="展开右侧属性配置面板"
                  onClick={(e) => {
                    e.stopPropagation();
                    setRightPanelCollapsed(false);
                  }}
                  className="h-[30px] px-2.5 rounded-[999px] m-pill flex items-center gap-1.5 text-[12px] font-[600] text-[var(--ink)] hover:text-[var(--blue)] hover:border-[var(--line-hover)] transition-all cursor-pointer"
                >
                  <span>展开配置</span>
                  <span className="text-[var(--blue)] inline-flex items-center">
                    <IconPanelRightOpen size={14} />
                  </span>
                </button>
              )}
            </div>

            {/* 画布滚动区（收紧内边距为 12px 14px，让画布组件拥有更大可用面积） */}
            <div
              onClick={() => {
                setSelectedNodeId(null);
                setViewportMenuOpen(false);
              }}
              onScroll={(e) => setCanvasScrolled(e.currentTarget.scrollTop > 8)}
              style={{
                paddingLeft: `${pageConfig.paddingX}px`,
                paddingRight: `${pageConfig.paddingX}px`,
                paddingTop: `${pageConfig.paddingY}px`,
              }}
              className="relative z-10 flex-1 overflow-y-auto pb-[72px]"
            >
              <div
                ref={canvasListRef}
                style={{
                  maxWidth: viewportWidth === 'auto' ? '100%' : `${viewportWidth}px`,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: `${pageConfig.blockGap}px`,
                }}
                className={`mx-auto transition-all duration-300 ${
                  deviceMode === 'mobile'
                    ? 'p-3.5 rounded-[28px] border-[6px] border-[var(--frame)] bg-[var(--canvas)] shadow-[var(--e3)]'
                    : deviceMode === 'tablet'
                      ? 'p-5 rounded-[24px] border-[4px] border-[var(--frame)] bg-[var(--canvas)] shadow-[var(--e2)]'
                      : ''
                }`}
              >
                {nodes.map((node, idx) => (
                  <React.Fragment key={node.id}>
                    {/* 1:1 实时组件拖拽悬浮预览（与对应被拖拽组件完全一致） */}
                    {!previewMode &&
                      !activeContainerTarget &&
                      activeDropIndex === idx &&
                      dragPreviewNode && (
                        <div data-ghost-preview="true" className="pg-ghost-preview-wrap my-2">
                          <div className="pg-ghost-badge">
                            <IconComponentKind kind={dragPreviewNode.kind} size={12} />
                            <span>预览落位 · {dragPreviewNode.name}（松手即放置）</span>
                          </div>
                          <CanvasNodeRenderer
                            node={dragPreviewNode}
                            isSelected={false}
                            selectedNodeId={null}
                            previewMode={true}
                            isGhostPreview={true}
                            canMoveUp={false}
                            canMoveDown={false}
                            tableRecords={tableRecords}
                            activeContainerTarget={null}
                            onSelect={() => {}}
                            onMoveUp={() => {}}
                            onMoveDown={() => {}}
                            onDuplicate={() => {}}
                            onDelete={() => {}}
                            onToggleRowCheck={() => {}}
                            onToggleAllRows={() => {}}
                            onChangeTableDensity={() => {}}
                          />
                        </div>
                      )}

                    <div data-top-node-index={idx}>
                      <CanvasNodeRenderer
                        node={node}
                        isSelected={selectedNode?.id === node.id}
                        selectedNodeId={selectedNode?.id || null}
                        previewMode={previewMode}
                        isBeingDragged={draggingNodeId === node.id}
                        draggingNodeId={draggingNodeId}
                        dragPreviewNode={dragPreviewNode}
                        canMoveUp={idx > 0}
                        canMoveDown={idx < nodes.length - 1}
                        tableRecords={tableRecords}
                        activeContainerTarget={activeContainerTarget}
                        onSelect={(id) => setSelectedNodeId(id)}
                        onMoveUp={(id) => handleMoveNode(id, 'up')}
                        onMoveDown={(id) => handleMoveNode(id, 'down')}
                        onDuplicate={handleDuplicateNode}
                        onToggleLock={handleToggleLockNode}
                        onToggleVisible={handleToggleVisibleNode}
                        onDelete={handleDeleteNode}
                        onToggleRowCheck={(rowId) => {
                          setTableRecords((prev) =>
                            prev.map((r) =>
                              r.id === rowId ? { ...r, checked: !r.checked } : r
                            )
                          );
                        }}
                        onToggleAllRows={() => {
                          const all = tableRecords.every((r) => r.checked);
                          setTableRecords((prev) =>
                            prev.map((r) => ({ ...r, checked: !all }))
                          );
                        }}
                        onChangeTableDensity={(nodeId, density) => {
                          const next = mapNodesRecursive(nodes, nodeId, (n) => ({
                            ...n,
                            props: { ...n.props, density },
                          }));
                          commitNodes(next);
                        }}
                        onNodeDragStart={(nodeId, e) => {
                          setDraggingNodeId(nodeId);
                          e.dataTransfer.setData('text/plain', nodeId);
                        }}
                        onNodeDragEnd={resetDragState}
                        onContainerDragOver={(nodeId, colIndex, e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setActiveDropIndex(null);
                          setActiveContainerTarget({ nodeId, colIndex });
                        }}
                        onContainerDragLeave={(nodeId, colIndex, e) => {
                          e.stopPropagation();
                          if (
                            activeContainerTarget?.nodeId === nodeId &&
                            activeContainerTarget?.colIndex === colIndex
                          ) {
                            setActiveContainerTarget(null);
                          }
                        }}
                        onContainerDrop={(nodeId, colIndex, e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          executeDropInsideContainer(nodeId, colIndex);
                        }}
                        onQuickAddInsideSlot={() => {
                          const cardMat = MATERIAL_LIBRARY.find(
                            (m) => m.kind === 'card'
                          );
                          if (cardMat) {
                            insertMaterialAt(cardMat, idx + 1);
                          }
                        }}
                      />
                    </div>
                  </React.Fragment>
                ))}

                {/* 末尾动态落点 1:1 实时组件预览 */}
                {!previewMode &&
                  !activeContainerTarget &&
                  activeDropIndex === nodes.length &&
                  dragPreviewNode && (
                    <div data-ghost-preview="true" className="pg-ghost-preview-wrap my-2">
                      <div className="pg-ghost-badge">
                        <IconComponentKind kind={dragPreviewNode.kind} size={12} />
                        <span>预览落位 · {dragPreviewNode.name}（松手即放置）</span>
                      </div>
                      <CanvasNodeRenderer
                        node={dragPreviewNode}
                        isSelected={false}
                        selectedNodeId={null}
                        previewMode={true}
                        isGhostPreview={true}
                        canMoveUp={false}
                        canMoveDown={false}
                        tableRecords={tableRecords}
                        activeContainerTarget={null}
                        onSelect={() => {}}
                        onMoveUp={() => {}}
                        onMoveDown={() => {}}
                        onDuplicate={() => {}}
                        onDelete={() => {}}
                        onToggleRowCheck={() => {}}
                        onToggleAllRows={() => {}}
                        onChangeTableDensity={() => {}}
                      />
                    </div>
                  )}

                {/* 末尾待放置虚线槽（容器 1px dashed --line-strong、outline-offset -3、cursor: copy） */}
                {!previewMode && (
                  <div
                    onClick={() => {
                      setLeftTab('materials');
                      pushToast('可从左侧组件物料拖拽或点击卡片插入组件', 'info');
                    }}
                    style={{
                      border:
                        !activeContainerTarget && activeDropIndex === nodes.length
                          ? '1.5px dashed var(--blue)'
                          : '1px dashed var(--line-strong)',
                      outlineOffset: '-3px',
                      background:
                        !activeContainerTarget && activeDropIndex === nodes.length
                          ? 'var(--blue-soft)'
                          : 'transparent',
                    }}
                    className="mt-4 rounded-[20px] py-5 px-4 flex items-center justify-between text-[12.5px] text-[var(--ink-soft)] hover:border-[var(--blue)] hover:text-[var(--ink)] transition-all cursor-copy"
                  >
                    <div className="flex items-center gap-2.5">
                      <span className="w-7 h-7 rounded-[8px] m-pill inline-flex items-center justify-center text-[var(--blue)]">
                        <IconPlus size={14} />
                      </span>
                      <span>
                        拖拽左侧组件物料到此处追加区块，或点击切换至组件物料
                      </span>
                    </div>
                    <span className="pg-code-chip">drop-zone:tail</span>
                  </div>
                )}
              </div>
            </div>
          </main>

          {/* 右栏：精简高效配置面板（支持「组件级别」与「页面级别」无缝切换：属性 / 样式属性 / 事件配置） */}
          {!isRightCollapsed && (
            <aside
              aria-label="组件与页面配置面板"
              className="w-full lg:w-[312px] shrink-0 m-solid rounded-[20px] flex flex-col min-h-0 overflow-hidden"
            >
            {/* 面板头：当前选中组件或页面根级概览 */}
            <div className="p-3.5 border-b border-[var(--line)] space-y-3">
              {selectedNode ? (
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="w-7 h-7 rounded-[8px] bg-[var(--blue-soft)] text-[var(--blue)] inline-flex items-center justify-center shrink-0">
                      <IconComponentKind kind={selectedNode.kind} size={15} />
                    </span>
                    <div className="min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="text-[13.5px] font-[650] text-[var(--ink)] truncate">
                          {selectedNode.name}
                        </span>
                        {selectedNode.locked && (
                          <span
                            title="组件已锁定"
                            className="text-[var(--blue)] inline-flex items-center shrink-0"
                          >
                            <IconLock size={12} />
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-[var(--ink-soft)] font-mono-num truncate">
                        {selectedNode.code}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <button
                      type="button"
                      title="切换到页面级配置"
                      onClick={() => setSelectedNodeId(null)}
                      className="h-[26px] px-2 rounded-[7px] border border-[var(--line)] bg-[var(--input-bg)] text-[11.5px] font-[580] text-[var(--ink-muted)] hover:text-[var(--blue)] hover:border-[var(--line-hover)] transition-colors inline-flex items-center gap-1 cursor-pointer"
                    >
                      <IconPage size={12} />
                      <span>页面设置</span>
                    </button>
                    <button
                      type="button"
                      aria-label="收起右侧配置面板"
                      title="收起右侧面板"
                      onClick={() => setRightPanelCollapsed(true)}
                      className="w-[26px] h-[26px] rounded-[7px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] transition-colors cursor-pointer"
                    >
                      <IconPanelRightClose size={14} />
                    </button>
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className="w-7 h-7 rounded-[8px] bg-[var(--blue-soft)] text-[var(--blue)] inline-flex items-center justify-center shrink-0">
                      <IconPage size={15} />
                    </span>
                    <div className="min-w-0">
                      <div className="text-[13.5px] font-[650] text-[var(--ink)] truncate">
                        {pageConfig.name}
                      </div>
                      <div className="text-[11px] text-[var(--ink-soft)] font-mono-num truncate">
                        {pageConfig.code}
                      </div>
                    </div>
                  </div>
                  <div className="flex items-center gap-1 shrink-0">
                    <span className="pg-tag pg-tag-info">
                      <span className="pg-tag-dot" />
                      页面级
                    </span>
                    <button
                      type="button"
                      aria-label="收起右侧配置面板"
                      title="收起右侧面板"
                      onClick={() => setRightPanelCollapsed(true)}
                      className="w-[26px] h-[26px] rounded-[7px] inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--ink)] hover:bg-[var(--hover-bg)] transition-colors cursor-pointer"
                    >
                      <IconPanelRightClose size={14} />
                    </button>
                  </div>
                </div>
              )}

              {/* 三段式配置切换：组件属性(或页面属性) / 样式属性 / 事件配置 */}
              <div className="m-groove grid grid-cols-3 gap-1">
                {(
                  [
                    {
                      key: 'props' as const,
                      label: selectedNode ? '组件属性' : '页面属性',
                    },
                    { key: 'style' as const, label: '样式属性' },
                    { key: 'events' as const, label: '事件配置' },
                  ]
                ).map((tab) => (
                  <button
                    key={tab.key}
                    type="button"
                    onClick={() => setInspectorTab(tab.key)}
                    className={`h-[28px] rounded-[8px] text-[12.5px] font-[600] transition-all cursor-pointer ${
                      inspectorTab === tab.key
                        ? 'm-pill text-[var(--blue)]'
                        : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'
                    }`}
                  >
                    {tab.label}
                  </button>
                ))}
              </div>
            </div>

            {/* 右栏主体配置区 */}
            <div className="flex-1 overflow-y-auto p-4 space-y-5">
              {selectedNode ? (
                /* ==================== A. 组件级配置 ==================== */
                <>
                  {/* 1. 组件属性 Tab */}
                  {inspectorTab === 'props' && (
                    <>
                      <div className="space-y-3">
                        <div className="pg-section-label">基础信息</div>

                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-node-name"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            节点名称
                          </label>
                          <input
                            id="insp-node-name"
                            type="text"
                            value={selectedNode.name}
                            onChange={(e) =>
                              updateSelectedNode((n) => ({ ...n, name: e.target.value }))
                            }
                            className="pg-input"
                          />
                        </div>

                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-prop-title"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            标题文案
                          </label>
                          <input
                            id="insp-prop-title"
                            type="text"
                            value={selectedNode.props.title}
                            onChange={(e) =>
                              updateSelectedNode((n) => ({
                                ...n,
                                props: { ...n.props, title: e.target.value },
                              }))
                            }
                            className="pg-input"
                          />
                        </div>

                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-prop-subtitle"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            辅助说明 / 占位提示
                          </label>
                          <input
                            id="insp-prop-subtitle"
                            type="text"
                            value={selectedNode.props.subtitle}
                            onChange={(e) =>
                              updateSelectedNode((n) => ({
                                ...n,
                                props: { ...n.props, subtitle: e.target.value },
                              }))
                            }
                            className="pg-input"
                          />
                        </div>
                      </div>

                      {/* 针对不同组件类型的专属属性 */}
                      {(selectedNode.kind === 'header' || selectedNode.kind === 'button') && (
                        <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                          <div className="pg-section-label">按钮文案</div>
                          <div className="grid grid-cols-2 gap-2.5">
                            <div className="space-y-1.5">
                              <label
                                htmlFor="insp-primary-btn"
                                className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                              >
                                主按钮
                              </label>
                              <input
                                id="insp-primary-btn"
                                type="text"
                                value={selectedNode.props.primaryActionText || ''}
                                onChange={(e) =>
                                  updateSelectedNode((n) => ({
                                    ...n,
                                    props: {
                                      ...n.props,
                                      primaryActionText: e.target.value,
                                    },
                                  }))
                                }
                                className="pg-input"
                              />
                            </div>
                            <div className="space-y-1.5">
                              <label
                                htmlFor="insp-secondary-btn"
                                className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                              >
                                次级按钮
                              </label>
                              <input
                                id="insp-secondary-btn"
                                type="text"
                                value={selectedNode.props.secondaryActionText || ''}
                                onChange={(e) =>
                                  updateSelectedNode((n) => ({
                                    ...n,
                                    props: {
                                      ...n.props,
                                      secondaryActionText: e.target.value,
                                    },
                                  }))
                                }
                                className="pg-input"
                              />
                            </div>
                          </div>
                        </div>
                      )}

                      {selectedNode.kind === 'readout' && (
                        <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                          <div className="pg-section-label">读数与铭牌参数</div>
                          <div className="grid grid-cols-2 gap-2.5">
                            <div className="space-y-1.5">
                              <label
                                htmlFor="insp-metric-val"
                                className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                              >
                                主角读数值
                              </label>
                              <input
                                id="insp-metric-val"
                                type="text"
                                value={selectedNode.props.heroMetricValue || ''}
                                onChange={(e) =>
                                  updateSelectedNode((n) => ({
                                    ...n,
                                    props: {
                                      ...n.props,
                                      heroMetricValue: e.target.value,
                                    },
                                  }))
                                }
                                className="pg-input font-mono-num"
                              />
                            </div>
                            <div className="space-y-1.5">
                              <label
                                htmlFor="insp-metric-delta"
                                className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                              >
                                环比读数
                              </label>
                              <input
                                id="insp-metric-delta"
                                type="text"
                                value={selectedNode.props.heroMetricDelta || ''}
                                onChange={(e) =>
                                  updateSelectedNode((n) => ({
                                    ...n,
                                    props: {
                                      ...n.props,
                                      heroMetricDelta: e.target.value,
                                    },
                                  }))
                                }
                                className="pg-input font-mono-num"
                              />
                            </div>
                          </div>

                          <div className="flex items-center justify-between py-1">
                            <span className="text-[12.5px] text-[var(--ink-muted)]">
                              显示慢操作斜线区间
                            </span>
                            <button
                              type="button"
                              role="switch"
                              aria-checked={selectedNode.props.showHatchBar !== false}
                              data-checked={selectedNode.props.showHatchBar !== false}
                              onClick={() =>
                                updateSelectedNode((n) => ({
                                  ...n,
                                  props: {
                                    ...n.props,
                                    showHatchBar: !(n.props.showHatchBar !== false),
                                  },
                                }))
                              }
                              className="pg-switch"
                            >
                              <span className="pg-switch-thumb" />
                            </button>
                          </div>

                          <div className="flex items-center justify-between py-1">
                            <span className="text-[12.5px] text-[var(--ink-muted)]">
                              显示工位规格铭牌
                            </span>
                            <button
                              type="button"
                              role="switch"
                              aria-checked={selectedNode.props.showSpecPlate !== false}
                              data-checked={selectedNode.props.showSpecPlate !== false}
                              onClick={() =>
                                updateSelectedNode((n) => ({
                                  ...n,
                                  props: {
                                    ...n.props,
                                    showSpecPlate: !(n.props.showSpecPlate !== false),
                                  },
                                }))
                              }
                              className="pg-switch"
                            >
                              <span className="pg-switch-thumb" />
                            </button>
                          </div>
                        </div>
                      )}

                      {selectedNode.kind === 'table' && (
                        <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                          <div className="pg-section-label">表格密度档位</div>
                          <div className="m-groove grid grid-cols-3 gap-1">
                            {(
                              [
                                { key: 'compact', label: '紧凑 40' },
                                { key: 'standard', label: '标准 48' },
                                { key: 'relaxed', label: '宽松 56' },
                              ] as { key: TableDensity; label: string }[]
                            ).map((d) => (
                              <button
                                key={d.key}
                                type="button"
                                onClick={() =>
                                  updateSelectedNode((n) => ({
                                    ...n,
                                    props: { ...n.props, density: d.key },
                                  }))
                                }
                                className={`h-[28px] rounded-[8px] text-[12px] font-[600] cursor-pointer ${
                                  (selectedNode.props.density || 'standard') === d.key
                                    ? 'm-pill text-[var(--blue)]'
                                    : 'text-[var(--ink-muted)]'
                                }`}
                              >
                                {d.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* 精简内聚的数据与权限绑定（整合在组件属性底部，不再单独占用顶层 Tab） */}
                      <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                        <div className="pg-section-label">数据与权限绑定</div>
                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-datasource"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            绑定数据源标识
                          </label>
                          <input
                            id="insp-datasource"
                            type="text"
                            value={selectedNode.dataSource}
                            onChange={(e) =>
                              updateSelectedNode((n) => ({
                                ...n,
                                dataSource: e.target.value,
                              }))
                            }
                            className="pg-input font-mono-num text-[12.5px]"
                          />
                        </div>
                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-perm-key"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            权限校验标识（RBAC）
                          </label>
                          <input
                            id="insp-perm-key"
                            type="text"
                            value={selectedNode.permissionKey}
                            onChange={(e) =>
                              updateSelectedNode((n) => ({
                                ...n,
                                permissionKey: e.target.value,
                              }))
                            }
                            className="pg-input font-mono-num text-[12.5px]"
                          />
                        </div>
                      </div>
                    </>
                  )}

                  {/* 2. 组件样式属性 Tab */}
                  {inspectorTab === 'style' && (
                    <div className="space-y-4">
                      <div className="pg-section-label">容器圆角与间距</div>

                      {/* 圆角严格 5 档：8 / 12 / 16 / 20 / 24 */}
                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between text-[12px] font-[600] text-[var(--ink-muted)]">
                          <span>容器圆角阶梯</span>
                          <span className="font-mono-num text-[12.5px] text-[var(--ink)]">
                            {selectedNode.radiusPx}
                            <sup className="text-[10px] text-[var(--ink-soft)] ml-0.5">px</sup>
                          </span>
                        </div>
                        <div className="m-groove grid grid-cols-5 gap-1">
                          {([8, 12, 16, 20, 24] as const).map((r) => (
                            <button
                              key={r}
                              type="button"
                              onClick={() =>
                                updateSelectedNode((n) => ({ ...n, radiusPx: r }))
                              }
                              className={`h-[28px] rounded-[8px] font-mono-num text-[12px] font-[600] cursor-pointer ${
                                selectedNode.radiusPx === r
                                  ? 'm-pill text-[var(--blue)]'
                                  : 'text-[var(--ink-muted)]'
                              }`}
                            >
                              {r}
                            </button>
                          ))}
                        </div>
                      </div>

                      {/* 内边距与外边距数值配置 */}
                      <div className="grid grid-cols-2 gap-2.5">
                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-padding-px"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            容器内边距
                          </label>
                          <div className="relative">
                            <input
                              id="insp-padding-px"
                              type="number"
                              min={8}
                              max={40}
                              step={2}
                              value={selectedNode.paddingPx}
                              onChange={(e) =>
                                updateSelectedNode((n) => ({
                                  ...n,
                                  paddingPx: Number(e.target.value) || 20,
                                }))
                              }
                              className="pg-input font-mono-num text-[12.5px] pr-8"
                            />
                            <sup className="absolute right-3 top-3 font-mono-num text-[10.5px] text-[var(--ink-soft)]">
                              px
                            </sup>
                          </div>
                        </div>

                        <div className="space-y-1.5">
                          <label
                            htmlFor="insp-margin-bottom"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            底部外边距
                          </label>
                          <div className="relative">
                            <input
                              id="insp-margin-bottom"
                              type="number"
                              min={0}
                              max={48}
                              step={2}
                              value={selectedNode.marginBottomPx || 0}
                              onChange={(e) =>
                                updateSelectedNode((n) => ({
                                  ...n,
                                  marginBottomPx: Math.max(0, Number(e.target.value) || 0),
                                }))
                              }
                              className="pg-input font-mono-num text-[12.5px] pr-8"
                            />
                            <sup className="absolute right-3 top-3 font-mono-num text-[10.5px] text-[var(--ink-soft)]">
                              px
                            </sup>
                          </div>
                        </div>
                      </div>

                      {/* 表面材质（显隐与锁定均已收敛至画布选中组件 ToolBar 与页面层级悬停操作栏） */}
                      <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                        <div className="pg-section-label">卡片表面材质</div>
                        <div className="m-groove grid grid-cols-3 gap-1">
                          {(
                            [
                              { key: 'solid' as const, label: '实色阅读面' },
                              { key: 'glass' as const, label: '珍珠玻璃面' },
                              { key: 'subtle' as const, label: '轻量浅底面' },
                            ]
                          ).map((item) => (
                            <button
                              key={item.key}
                              type="button"
                              onClick={() =>
                                updateSelectedNode((n) => ({
                                  ...n,
                                  surfaceStyle: item.key,
                                }))
                              }
                              className={`h-[28px] rounded-[8px] text-[12px] font-[600] cursor-pointer ${
                                (selectedNode.surfaceStyle || 'solid') === item.key
                                  ? 'm-pill text-[var(--blue)]'
                                  : 'text-[var(--ink-muted)]'
                              }`}
                            >
                              {item.label}
                            </button>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}

                  {/* 3. 组件事件配置 Tab（面板内仅展示简洁事件列表，点击「新增事件」或列表项打开抽屉配置） */}
                  {inspectorTab === 'events' && (() => {
                    const events =
                      selectedNode.events || getDefaultEventsForKind(selectedNode.kind);

                    return (
                      <div className="space-y-3.5">
                        <div className="flex items-center justify-between">
                          <span className="pg-section-label">组件交互事件</span>
                          <button
                            type="button"
                            onClick={() => openCreateEventDrawer('node')}
                            className="pg-btn pg-btn-sm pg-btn-secondary !h-[26px] !px-2 !text-[11.5px]"
                          >
                            <IconPlus size={12} />
                            新增事件
                          </button>
                        </div>

                        {events.length === 0 ? (
                          <div className="rounded-[12px] p-4 border border-dashed border-[var(--line-strong)] text-center text-[12px] text-[var(--ink-soft)]">
                            当前组件暂无事件，点击右上角「新增事件」配置交互逻辑
                          </div>
                        ) : (
                          <div className="space-y-2">
                            {events.map((evt) => {
                              const actionOpt = EVENT_ACTION_OPTIONS.find(
                                (o) => o.value === evt.actionType
                              );
                              return (
                                <div
                                  key={evt.id}
                                  onClick={() => openEditEventDrawer('node', evt)}
                                  className="group/evt rounded-[12px] p-2.5 border border-[var(--line)] bg-[var(--input-bg)] hover:border-[var(--blue)] hover:bg-[var(--surface-solid)] transition-all cursor-pointer space-y-1.5"
                                >
                                  <div className="flex items-center justify-between gap-2">
                                    <span className="inline-flex items-center gap-1.5 text-[12.5px] font-[620] text-[var(--ink)] group-hover/evt:text-[var(--blue)] transition-colors truncate">
                                      <span className="text-[var(--blue)] shrink-0">
                                        <IconBolt size={13} />
                                      </span>
                                      <span className="truncate">{evt.triggerName}</span>
                                    </span>
                                    <div className="flex items-center gap-1 shrink-0">
                                      <span
                                        className={`pg-tag !h-[19px] !px-2 !text-[10.5px] ${
                                          evt.enabled ? 'pg-tag-ok' : 'pg-tag-neutral'
                                        }`}
                                      >
                                        {evt.enabled ? '已启用' : '已停用'}
                                      </span>
                                      <button
                                        type="button"
                                        aria-label="删除事件"
                                        title="删除该事件绑定"
                                        onClick={(e) => {
                                          e.stopPropagation();
                                          updateSelectedNode((n) => ({
                                            ...n,
                                            events: (n.events || events).filter(
                                              (item) => item.id !== evt.id
                                            ),
                                          }));
                                          pushToast('已移除事件绑定', 'info');
                                        }}
                                        className="w-[20px] h-[20px] rounded-[5px] hidden group-hover/evt:inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--red-text)] hover:bg-[var(--red-soft)] transition-colors cursor-pointer"
                                      >
                                        <IconTrash size={11.5} />
                                      </button>
                                    </div>
                                  </div>
                                  <div className="flex items-center justify-between gap-2 text-[11.5px] text-[var(--ink-soft)]">
                                    <span className="truncate">
                                      {actionOpt?.label || evt.actionType} · {evt.targetDesc}
                                    </span>
                                    <span className="pg-code-chip !h-[19px] !text-[10.5px] shrink-0">
                                      {evt.triggerCode}
                                    </span>
                                  </div>
                                </div>
                              );
                            })}
                          </div>
                        )}
                      </div>
                    );
                  })()}
                </>
              ) : (
                /* ==================== B. 页面级配置（点击画布空白或点击页面层级根节点进入） ==================== */
                <>
                  {/* 1. 页面属性 Tab */}
                  {inspectorTab === 'props' && (
                    <div className="space-y-3.5">
                      <div className="pg-section-label">页面基础属性</div>

                      <div className="space-y-1.5">
                        <label
                          htmlFor="page-prop-name"
                          className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                        >
                          页面名称
                        </label>
                        <input
                          id="page-prop-name"
                          type="text"
                          value={pageConfig.name}
                          onChange={(e) =>
                            setPageConfig((prev) => ({ ...prev, name: e.target.value }))
                          }
                          className="pg-input"
                        />
                      </div>

                      <div className="space-y-1.5">
                        <label
                          htmlFor="page-prop-code"
                          className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                        >
                          页面唯一编码
                        </label>
                        <input
                          id="page-prop-code"
                          type="text"
                          value={pageConfig.code}
                          onChange={(e) =>
                            setPageConfig((prev) => ({ ...prev, code: e.target.value }))
                          }
                          className="pg-input font-mono-num text-[12.5px]"
                        />
                      </div>

                      <div className="space-y-1.5">
                        <label
                          htmlFor="page-prop-desc"
                          className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                        >
                          页面业务说明
                        </label>
                        <input
                          id="page-prop-desc"
                          type="text"
                          value={pageConfig.description}
                          onChange={(e) =>
                            setPageConfig((prev) => ({
                              ...prev,
                              description: e.target.value,
                            }))
                          }
                          className="pg-input"
                        />
                      </div>

                      <div className="pt-3.5 border-t border-[var(--line)] space-y-1.5">
                        <label
                          htmlFor="page-prop-perm"
                          className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                        >
                          页面访问权限标识（RBAC）
                        </label>
                        <input
                          id="page-prop-perm"
                          type="text"
                          value={pageConfig.permissionKey}
                          onChange={(e) =>
                            setPageConfig((prev) => ({
                              ...prev,
                              permissionKey: e.target.value,
                            }))
                          }
                          className="pg-input font-mono-num text-[12.5px]"
                        />
                      </div>
                    </div>
                  )}

                  {/* 2. 页面样式属性 Tab */}
                  {inspectorTab === 'style' && (
                    <div className="space-y-4">
                      <div className="pg-section-label">画布布局与间距</div>

                      <div className="grid grid-cols-2 gap-2.5">
                        <div className="space-y-1.5">
                          <label
                            htmlFor="page-pad-x"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            左右内边距
                          </label>
                          <div className="relative">
                            <input
                              id="page-pad-x"
                              type="number"
                              min={8}
                              max={48}
                              step={2}
                              value={pageConfig.paddingX}
                              onChange={(e) =>
                                setPageConfig((prev) => ({
                                  ...prev,
                                  paddingX: Math.max(0, Number(e.target.value) || 14),
                                }))
                              }
                              className="pg-input font-mono-num text-[12.5px] pr-8"
                            />
                            <sup className="absolute right-3 top-3 font-mono-num text-[10.5px] text-[var(--ink-soft)]">
                              px
                            </sup>
                          </div>
                        </div>

                        <div className="space-y-1.5">
                          <label
                            htmlFor="page-pad-y"
                            className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                          >
                            顶部内边距
                          </label>
                          <div className="relative">
                            <input
                              id="page-pad-y"
                              type="number"
                              min={8}
                              max={48}
                              step={2}
                              value={pageConfig.paddingY}
                              onChange={(e) =>
                                setPageConfig((prev) => ({
                                  ...prev,
                                  paddingY: Math.max(0, Number(e.target.value) || 12),
                                }))
                              }
                              className="pg-input font-mono-num text-[12.5px] pr-8"
                            />
                            <sup className="absolute right-3 top-3 font-mono-num text-[10.5px] text-[var(--ink-soft)]">
                              px
                            </sup>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between text-[12px] font-[600] text-[var(--ink-muted)]">
                          <span>组件区块纵向间距</span>
                          <span className="font-mono-num text-[12.5px] text-[var(--ink)]">
                            {pageConfig.blockGap}
                            <sup className="text-[10px] text-[var(--ink-soft)] ml-0.5">px</sup>
                          </span>
                        </div>
                        <div className="m-groove grid grid-cols-4 gap-1">
                          {([6, 10, 16, 24] as const).map((g) => (
                            <button
                              key={g}
                              type="button"
                              onClick={() =>
                                setPageConfig((prev) => ({ ...prev, blockGap: g }))
                              }
                              className={`h-[28px] rounded-[8px] font-mono-num text-[12px] font-[600] cursor-pointer ${
                                pageConfig.blockGap === g
                                  ? 'm-pill text-[var(--blue)]'
                                  : 'text-[var(--ink-muted)]'
                              }`}
                            >
                              {g}px
                            </button>
                          ))}
                        </div>
                      </div>

                      <div className="pt-3.5 border-t border-[var(--line)] space-y-3">
                        <div className="pg-section-label">画布底色与辅助点阵</div>
                        <div className="m-groove grid grid-cols-3 gap-1">
                          {(
                            [
                              { key: 'default' as const, label: '标准仪器灰' },
                              { key: 'pure' as const, label: '纯白阅读底' },
                              { key: 'warm' as const, label: '温润护眼底' },
                            ]
                          ).map((tone) => (
                            <button
                              key={tone.key}
                              type="button"
                              onClick={() =>
                                setPageConfig((prev) => ({
                                  ...prev,
                                  canvasBgTone: tone.key,
                                }))
                              }
                              className={`h-[28px] rounded-[8px] text-[12px] font-[600] cursor-pointer ${
                                pageConfig.canvasBgTone === tone.key
                                  ? 'm-pill text-[var(--blue)]'
                                  : 'text-[var(--ink-muted)]'
                              }`}
                            >
                              {tone.label}
                            </button>
                          ))}
                        </div>

                        <div className="flex items-center justify-between py-1">
                          <span className="text-[12.5px] text-[var(--ink-muted)]">
                            显示画布参考点阵
                          </span>
                          <button
                            type="button"
                            role="switch"
                            aria-checked={pageConfig.showDots}
                            data-checked={pageConfig.showDots}
                            onClick={() =>
                              setPageConfig((prev) => ({
                                ...prev,
                                showDots: !prev.showDots,
                              }))
                            }
                            className="pg-switch"
                          >
                            <span className="pg-switch-thumb" />
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* 3. 页面事件配置 Tab（面板内仅展示简洁事件列表，点击新增或列表项打开抽屉配置） */}
                  {inspectorTab === 'events' && (
                    <div className="space-y-3.5">
                      <div className="flex items-center justify-between">
                        <span className="pg-section-label">页面生命周期事件</span>
                        <button
                          type="button"
                          onClick={() => openCreateEventDrawer('page')}
                          className="pg-btn pg-btn-sm pg-btn-secondary !h-[26px] !px-2 !text-[11.5px]"
                        >
                          <IconPlus size={12} />
                          新增事件
                        </button>
                      </div>

                      {pageConfig.events.length === 0 ? (
                        <div className="rounded-[12px] p-4 border border-dashed border-[var(--line-strong)] text-center text-[12px] text-[var(--ink-soft)]">
                          当前页面暂无生命周期事件，点击右上角「新增事件」配置
                        </div>
                      ) : (
                        <div className="space-y-2">
                          {pageConfig.events.map((evt) => {
                            const actionOpt = EVENT_ACTION_OPTIONS.find(
                              (o) => o.value === evt.actionType
                            );
                            return (
                              <div
                                key={evt.id}
                                onClick={() => openEditEventDrawer('page', evt)}
                                className="group/evt rounded-[12px] p-2.5 border border-[var(--line)] bg-[var(--input-bg)] hover:border-[var(--blue)] hover:bg-[var(--surface-solid)] transition-all cursor-pointer space-y-1.5"
                              >
                                <div className="flex items-center justify-between gap-2">
                                  <span className="inline-flex items-center gap-1.5 text-[12.5px] font-[620] text-[var(--ink)] group-hover/evt:text-[var(--blue)] transition-colors truncate">
                                    <span className="text-[var(--blue)] shrink-0">
                                      <IconBolt size={13} />
                                    </span>
                                    <span className="truncate">{evt.triggerName}</span>
                                  </span>
                                  <div className="flex items-center gap-1 shrink-0">
                                    <span
                                      className={`pg-tag !h-[19px] !px-2 !text-[10.5px] ${
                                        evt.enabled ? 'pg-tag-ok' : 'pg-tag-neutral'
                                      }`}
                                    >
                                      {evt.enabled ? '已启用' : '已停用'}
                                    </span>
                                    <button
                                      type="button"
                                      aria-label="删除页面事件"
                                      title="删除该页面事件"
                                      onClick={(e) => {
                                        e.stopPropagation();
                                        setPageConfig((prev) => ({
                                          ...prev,
                                          events: prev.events.filter(
                                            (item) => item.id !== evt.id
                                          ),
                                        }));
                                        pushToast('已移除页面事件绑定', 'info');
                                      }}
                                      className="w-[20px] h-[20px] rounded-[5px] hidden group-hover/evt:inline-flex items-center justify-center text-[var(--ink-soft)] hover:text-[var(--red-text)] hover:bg-[var(--red-soft)] transition-colors cursor-pointer"
                                    >
                                      <IconTrash size={11.5} />
                                    </button>
                                  </div>
                                </div>
                                <div className="flex items-center justify-between gap-2 text-[11.5px] text-[var(--ink-soft)]">
                                  <span className="truncate">
                                    {actionOpt?.label || evt.actionType} · {evt.targetDesc}
                                  </span>
                                  <span className="pg-code-chip !h-[19px] !text-[10.5px] shrink-0">
                                    {evt.triggerCode}
                                  </span>
                                </div>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  )}
                </>
              )}
            </div>
            </aside>
          )}
        </div>

        {/* 底部深色玻璃浮条 .float-bar：用于未保存改动与表格批量操作（底部居中 28px） */}
        {(unsavedCount > 0 || checkedRowsCount > 0) && (
          <div
            style={{ bottom: '28px', zIndex: 120 }}
            className="pg-float-bar fixed left-1/2 -translate-x-1/2 h-[44px] px-4 flex items-center gap-4 text-[13px] transition-all"
          >
            <div className="flex items-center gap-2">
              <span
                className="w-2 h-2 rounded-full"
                style={{
                  background: 'var(--lime)',
                  boxShadow: '0 0 0 4px rgba(130, 221, 45, 0.22)',
                }}
              />
              <span className="font-[600]">
                {unsavedCount > 0
                  ? `有 ${unsavedCount} 项画布改动尚未保存`
                  : `已勾选 ${checkedRowsCount} 个待放行电芯批次`}
              </span>
            </div>

            <div className="h-3.5 w-[1px] bg-white/15" />

            <div className="flex items-center gap-1">
              <button
                type="button"
                aria-label="撤销改动"
                disabled={historyIndex <= 0}
                onClick={handleUndo}
                className="w-8 h-8 rounded-[8px] inline-flex items-center justify-center text-white/75 hover:text-white hover:bg-white/10 disabled:opacity-35 cursor-pointer"
              >
                <IconUndo size={14} />
              </button>
              <button
                type="button"
                aria-label="重做改动"
                disabled={historyIndex >= history.length - 1}
                onClick={handleRedo}
                className="w-8 h-8 rounded-[8px] inline-flex items-center justify-center text-white/75 hover:text-white hover:bg-white/10 disabled:opacity-35 cursor-pointer"
              >
                <IconRedo size={14} />
              </button>
            </div>

            <div className="flex items-center gap-3">
              {unsavedCount > 0 && (
                <button
                  type="button"
                  onClick={handleDiscardChanges}
                  className="text-[12.5px] font-[620] text-[#ff8a82] hover:underline cursor-pointer"
                >
                  放弃改动
                </button>
              )}
              <button
                type="button"
                onClick={handleSaveDraft}
                className="text-[12.5px] font-[640] text-[#8fb8ff] hover:underline cursor-pointer"
              >
                立即保存草稿
              </button>
            </div>
          </div>
        )}

        {/* 底部居中轻提示牌堆（高 44、宽 ≤460，越旧越往上缩 5%、透明度减 .28，最多 3 张） */}
        <div
          style={{ bottom: unsavedCount > 0 || checkedRowsCount > 0 ? '84px' : '28px', zIndex: 400 }}
          className="fixed left-1/2 -translate-x-1/2 pointer-events-none flex flex-col items-center"
        >
          {toasts.map((t, i) => {
            const age = toasts.length - 1 - i;
            const scale = 1 - age * 0.05;
            const opacity = 1 - age * 0.28;
            const translateY = -age * 10;
            const dotColor =
              t.tone === 'danger'
                ? 'var(--red)'
                : t.tone === 'info'
                  ? 'var(--blue)'
                  : 'var(--lime)';

            return (
              <div
                key={t.id}
                style={{
                  height: '44px',
                  maxWidth: '460px',
                  transform: `translateY(${translateY}px) scale(${scale})`,
                  opacity,
                }}
                className="m-float rounded-[999px] px-4 flex items-center gap-3 shadow-[var(--e3)] transition-all duration-300 mb-1"
              >
                <span
                  style={{
                    width: '8px',
                    height: '8px',
                    borderRadius: '999px',
                    background: dotColor,
                    boxShadow: `0 0 0 4px ${
                      t.tone === 'danger'
                        ? 'var(--red-soft)'
                        : t.tone === 'info'
                          ? 'var(--blue-soft)'
                          : 'var(--lime-soft)'
                    }`,
                  }}
                />
                <span className="text-[13px] font-[620] text-[var(--ink)] whitespace-nowrap">
                  {t.text}
                </span>
                {/* 右侧 44 周长的倒计时细环 */}
                <svg width="18" height="18" viewBox="0 0 18 18" className="shrink-0">
                  <circle
                    cx="9"
                    cy="9"
                    r="7"
                    fill="none"
                    stroke="var(--line-strong)"
                    strokeWidth="1.5"
                  />
                  <circle
                    cx="9"
                    cy="9"
                    r="7"
                    fill="none"
                    stroke={dotColor}
                    strokeWidth="1.5"
                    strokeDasharray="44"
                    strokeDashoffset="12"
                    strokeLinecap="round"
                  />
                </svg>
              </div>
            );
          })}
        </div>

        {/* 统一焦点栈浮层（遮罩 z-index 200，rgba(16,20,24,.26) + blur 3px） */}
        {topOverlay && (
          <div
            style={{
              zIndex: 200,
              background: 'var(--overlay)',
              backdropFilter: 'blur(3px)',
            }}
            onClick={closeTopOverlay}
            className="fixed inset-0 flex items-center justify-center p-4"
          >
            {/* 1. ⌘K 命令面板弹窗（圆角 24、实色面 + --e3 + 内高光） */}
            {topOverlay === 'command' && (
              <div
                onClick={(e) => e.stopPropagation()}
                className="m-solid rounded-[24px] w-full max-w-[540px] shadow-[var(--e3)] overflow-hidden"
              >
                <div className="px-[22px] pt-[20px] pb-[10px] border-b border-[var(--line)]">
                  <div className="flex items-center justify-between mb-2">
                    <h2
                      style={{
                        fontSize: '16px',
                        fontWeight: 700,
                        letterSpacing: '-0.015em',
                      }}
                    >
                      快速指令与组件跳转
                    </h2>
                    <span className="pg-code-chip">ESC 关闭</span>
                  </div>
                  <input
                    type="text"
                    autoFocus
                    value={commandQuery}
                    onChange={(e) => setCommandQuery(e.target.value)}
                    placeholder="输入指令名称，例如：插入工序图谱、切换深色模式、导出 Schema..."
                    className="pg-input"
                  />
                </div>

                <div className="px-[22px] py-[14px] max-h-[300px] overflow-y-auto space-y-1">
                  {[
                    {
                      label: '向画布末尾插入「面板卡片」基础组件',
                      code: 'cmd:insert-card',
                      run: () => {
                        const m = MATERIAL_LIBRARY.find((x) => x.kind === 'card');
                        if (m) insertMaterialAt(m, nodes.length);
                        closeTopOverlay();
                      },
                    },
                    {
                      label: '切换浅色 / 深色珍珠玻璃材质主题',
                      code: 'cmd:toggle-theme',
                      run: () => {
                        setTheme((t) => (t === 'light' ? 'dark' : 'light'));
                        closeTopOverlay();
                      },
                    },
                    {
                      label: '打开页面完整 JSON Schema 抽屉',
                      code: 'cmd:open-schema',
                      run: () => {
                        openOverlay('schema');
                      },
                    },
                    {
                      label: '立即保存当前画布草稿并清零未保存计数',
                      code: 'cmd:save-draft',
                      run: () => {
                        handleSaveDraft();
                        closeTopOverlay();
                      },
                    },
                  ]
                    .filter((item) =>
                      item.label.toLowerCase().includes(commandQuery.trim().toLowerCase())
                    )
                    .map((item) => (
                      <button
                        key={item.code}
                        type="button"
                        onClick={item.run}
                        className="w-full h-[36px] px-3 rounded-[8px] flex items-center justify-between text-[13px] text-[var(--ink)] hover:bg-[var(--hover-bg)] cursor-pointer"
                      >
                        <span>{item.label}</span>
                        <span className="pg-code-chip">{item.code}</span>
                      </button>
                    ))}
                </div>
              </div>
            )}

            {/* 2. 页面 JSON Schema 满高抽屉（满高、圆角 24、外层内距 8） */}
            {topOverlay === 'schema' && (
              <div
                onClick={(e) => e.stopPropagation()}
                className="fixed inset-y-0 right-0 p-[8px] w-full max-w-[520px] flex"
              >
                <div className="m-solid rounded-[24px] flex-1 flex flex-col shadow-[var(--e3)] overflow-hidden">
                  <div className="px-[22px] pt-[20px] pb-[12px] border-b border-[var(--line)] flex items-center justify-between">
                    <div>
                      <h2
                        style={{
                          fontSize: '16px',
                          fontWeight: 700,
                          letterSpacing: '-0.015em',
                        }}
                      >
                        页面低代码声明式结构（Schema）
                      </h2>
                      <p className="text-[12px] text-[var(--ink-soft)] mt-0.5">
                        实时映射三栏工作台内所有节点的属性、令牌与 RBAC 权限标识
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={closeTopOverlay}
                      className="pg-btn pg-btn-sm pg-btn-ghost"
                    >
                      关闭
                    </button>
                  </div>

                  <div className="flex-1 overflow-y-auto p-[20px]">
                    <pre
                      style={{
                        background: '#131619',
                        color: '#d9e1e4',
                        fontSize: '12px',
                        lineHeight: 1.65,
                        padding: '14px 16px',
                        borderRadius: '16px',
                      }}
                      className="font-mono-num overflow-x-auto"
                    >
                      {JSON.stringify(
                        {
                          pageCode: 'mes_formation_release_station',
                          version: '2.4.0',
                          styleSystem: 'pearl-glass',
                          nodes,
                        },
                        null,
                        2
                      )}
                    </pre>
                  </div>

                  <div className="px-[22px] py-[14px] border-t border-[var(--line)] flex items-center justify-between">
                    <span className="pg-code-chip">UTF-8 · JSON Schema v2</span>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard?.writeText(JSON.stringify(nodes, null, 2));
                        pushToast('已复制完整页面 Schema 到剪贴板', 'ok');
                      }}
                      className="pg-btn pg-btn-primary"
                    >
                      <IconCopy size={14} />
                      复制结构代码
                    </button>
                  </div>
                </div>
              </div>
            )}

            {/* 3. 发布上线核准弹窗（圆角 24、头 20 22 6、体 14 22 20、脚 14 22 上边线；表单两列 gap 16 18） */}
            {topOverlay === 'publish' && (
              <div
                onClick={(e) => e.stopPropagation()}
                className="m-solid rounded-[24px] w-full max-w-[560px] shadow-[var(--e3)] overflow-hidden"
              >
                <div style={{ padding: '20px 22px 6px' }}>
                  <h2
                    style={{
                      fontSize: '16px',
                      fontWeight: 700,
                      letterSpacing: '-0.015em',
                    }}
                  >
                    发布电芯化成工站批次放行台
                  </h2>
                  <p className="text-[12.5px] text-[var(--ink-soft)] mt-1">
                    发布后将同步更新宁德三期车间终端的页面布局与服务端权限规则
                  </p>
                </div>

                <div
                  style={{
                    padding: '14px 22px 20px',
                    display: 'grid',
                    gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
                    gap: '16px 18px',
                  }}
                >
                  <div className="space-y-1.5">
                    <label
                      htmlFor="pub-version"
                      className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                    >
                      发布版本号
                    </label>
                    <input
                      id="pub-version"
                      type="text"
                      defaultValue="v2.4.1-release"
                      className="pg-input font-mono-num"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label
                      htmlFor="pub-env"
                      className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                    >
                      目标运行环境
                    </label>
                    <select id="pub-env" className="pg-input" defaultValue="prod">
                      <option value="prod">生产环境（宁德三期 A/B/C 线）</option>
                      <option value="staging">预发验证环境</option>
                    </select>
                  </div>
                  <div className="col-span-2 space-y-1.5">
                    <label
                      htmlFor="pub-note"
                      className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                    >
                      变更说明
                    </label>
                    <input
                      id="pub-note"
                      type="text"
                      defaultValue="校准一次化成直通率读数台采样周期，新增压降超限隔离批次复核入口"
                      className="pg-input"
                    />
                  </div>
                </div>

                <div
                  style={{ padding: '14px 22px' }}
                  className="border-t border-[var(--line)] flex items-center justify-end gap-2"
                >
                  <button
                    type="button"
                    onClick={closeTopOverlay}
                    className="pg-btn pg-btn-ghost"
                  >
                    取消
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setUnsavedCount(0);
                      closeTopOverlay();
                      pushToast('已签发上线 v2.4.1，车间终端已同步生效', 'ok');
                    }}
                    className="pg-btn pg-btn-primary"
                  >
                    确认签发上线
                  </button>
                </div>
              </div>
            )}

            {/* 4. 事件配置满高抽屉（点击右侧面板「新增事件」或点击已添加事件列表项时滑出） */}
            {topOverlay === 'eventDrawer' && eventDrawerState && (() => {
              const isNodeScope = eventDrawerState.scope === 'node';
              const ownerNode =
                isNodeScope && eventDrawerState.nodeId
                  ? findNodeRecursive(nodes, eventDrawerState.nodeId)
                  : null;
              const availableTriggers = isNodeScope && ownerNode
                ? AVAILABLE_TRIGGERS_BY_KIND[ownerNode.kind] ||
                  AVAILABLE_TRIGGERS_BY_KIND.button
                : AVAILABLE_TRIGGERS_BY_KIND.page;
              const { draft, mode } = eventDrawerState;

              return (
                <div
                  onClick={(e) => e.stopPropagation()}
                  className="fixed inset-y-0 right-0 p-[8px] w-full max-w-[460px] flex"
                >
                  <div className="m-solid rounded-[24px] flex-1 flex flex-col shadow-[var(--e3)] overflow-hidden">
                    {/* 抽屉头 */}
                    <div className="px-[22px] pt-[20px] pb-[14px] border-b border-[var(--line)] flex items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="w-6 h-6 rounded-[7px] bg-[var(--blue-soft)] text-[var(--blue)] inline-flex items-center justify-center">
                            <IconBolt size={14} />
                          </span>
                          <h2
                            style={{
                              fontSize: '16px',
                              fontWeight: 700,
                              letterSpacing: '-0.015em',
                            }}
                          >
                            {mode === 'create' ? '新增' : '配置'}
                            {isNodeScope ? '组件交互事件' : '页面生命周期事件'}
                          </h2>
                        </div>
                        <p className="text-[12px] text-[var(--ink-soft)] mt-1">
                          所属对象：
                          <strong className="text-[var(--ink)] font-[600] ml-1">
                            {isNodeScope && ownerNode
                              ? `${ownerNode.name} (${ownerNode.code})`
                              : `页面级 · ${pageConfig.name}`}
                          </strong>
                        </p>
                      </div>
                      <span className="pg-code-chip shrink-0">ESC 关闭</span>
                    </div>

                    {/* 抽屉体 */}
                    <div className="flex-1 overflow-y-auto p-[22px] space-y-5">
                      {/* 1. 触发时机 */}
                      <div className="space-y-2">
                        <label className="pg-section-label">选择触发时机</label>
                        <div className="space-y-1.5">
                          {availableTriggers.map((trig) => {
                            const active = draft.triggerCode === trig.code;
                            return (
                              <button
                                key={trig.code}
                                type="button"
                                onClick={() =>
                                  setEventDrawerState((prev) =>
                                    prev
                                      ? {
                                          ...prev,
                                          draft: {
                                            ...prev.draft,
                                            triggerCode: trig.code,
                                            triggerName: trig.name,
                                          },
                                        }
                                      : prev
                                  )
                                }
                                className={`w-full h-[38px] px-3 rounded-[10px] border flex items-center justify-between text-[12.5px] transition-all cursor-pointer ${
                                  active
                                    ? 'border-[var(--blue)] bg-[var(--blue-soft)] text-[var(--blue)] font-[620]'
                                    : 'border-[var(--line)] bg-[var(--input-bg)] text-[var(--ink)] hover:border-[var(--line-hover)]'
                                }`}
                              >
                                <span>{trig.name}</span>
                                <span className="pg-code-chip">{trig.code}</span>
                              </button>
                            );
                          })}
                        </div>
                      </div>

                      {/* 2. 执行动作 */}
                      <div className="space-y-2 pt-3 border-t border-[var(--line)]">
                        <label className="pg-section-label">选择响应动作</label>
                        <select
                          value={draft.actionType}
                          onChange={(e) => {
                            const nextType = e.target.value as EventActionType;
                            const opt = EVENT_ACTION_OPTIONS.find(
                              (o) => o.value === nextType
                            );
                            setEventDrawerState((prev) =>
                              prev
                                ? {
                                    ...prev,
                                    draft: {
                                      ...prev.draft,
                                      actionType: nextType,
                                      targetDesc:
                                        opt?.defaultTarget || prev.draft.targetDesc,
                                    },
                                  }
                                : prev
                            );
                          }}
                          className="pg-input"
                        >
                          {EVENT_ACTION_OPTIONS.map((opt) => (
                            <option key={opt.value} value={opt.value}>
                              {opt.label}
                            </option>
                          ))}
                        </select>
                      </div>

                      {/* 3. 动作目标与参数 */}
                      <div className="space-y-2">
                        <label
                          htmlFor="drawer-evt-target"
                          className="block text-[12px] font-[600] text-[var(--ink-muted)]"
                        >
                          动作目标 / 接口地址 / 提示内容
                        </label>
                        <input
                          id="drawer-evt-target"
                          type="text"
                          value={draft.targetDesc}
                          onChange={(e) =>
                            setEventDrawerState((prev) =>
                              prev
                                ? {
                                    ...prev,
                                    draft: {
                                      ...prev.draft,
                                      targetDesc: e.target.value,
                                    },
                                  }
                                : prev
                            )
                          }
                          className="pg-input font-mono-num text-[12.5px]"
                        />
                      </div>

                      {/* 4. 启用状态开关 */}
                      <div className="pt-3 border-t border-[var(--line)] flex items-center justify-between">
                        <div>
                          <div className="text-[13px] font-[620] text-[var(--ink)]">
                            启用该事件绑定
                          </div>
                          <div className="text-[11.5px] text-[var(--ink-soft)]">
                            停用后保留配置但不在运行态触发
                          </div>
                        </div>
                        <button
                          type="button"
                          role="switch"
                          aria-checked={draft.enabled}
                          data-checked={draft.enabled}
                          onClick={() =>
                            setEventDrawerState((prev) =>
                              prev
                                ? {
                                    ...prev,
                                    draft: {
                                      ...prev.draft,
                                      enabled: !prev.draft.enabled,
                                    },
                                  }
                                : prev
                            )
                          }
                          className="pg-switch"
                        >
                          <span className="pg-switch-thumb" />
                        </button>
                      </div>

                      {/* 5. 动作声明预览与仿真测试 */}
                      <div className="space-y-2 pt-2">
                        <div className="flex items-center justify-between text-[12px] font-[600] text-[var(--ink-muted)]">
                          <span>事件处理器声明预览</span>
                          <button
                            type="button"
                            onClick={() =>
                              pushToast(
                                `仿真触发「${draft.triggerName}」：${draft.targetDesc}`,
                                'ok'
                              )
                            }
                            className="text-[12px] font-[620] text-[var(--blue)] hover:underline cursor-pointer"
                          >
                            测试触发效果
                          </button>
                        </div>
                        <pre
                          style={{
                            background: '#131619',
                            color: '#d9e1e4',
                            fontSize: '12px',
                            lineHeight: 1.65,
                            padding: '14px 16px',
                            borderRadius: '16px',
                          }}
                          className="font-mono-num overflow-x-auto"
                        >
{`{
  "trigger": "${draft.triggerCode}",
  "action": "${draft.actionType}",
  "target": "${draft.targetDesc}",
  "enabled": ${draft.enabled}
}`}
                        </pre>
                      </div>
                    </div>

                    {/* 抽屉脚 */}
                    <div className="px-[22px] py-[14px] border-t border-[var(--line)] flex items-center justify-between gap-2">
                      {mode === 'edit' ? (
                        <button
                          type="button"
                          onClick={() => {
                            if (isNodeScope && eventDrawerState.nodeId) {
                              const next = mapNodesRecursive(
                                nodes,
                                eventDrawerState.nodeId,
                                (n) => ({
                                  ...n,
                                  events: (
                                    n.events || getDefaultEventsForKind(n.kind)
                                  ).filter((item) => item.id !== draft.id),
                                })
                              );
                              commitNodes(next);
                            } else {
                              setPageConfig((prev) => ({
                                ...prev,
                                events: prev.events.filter(
                                  (item) => item.id !== draft.id
                                ),
                              }));
                            }
                            closeTopOverlay();
                            pushToast('已删除该事件绑定', 'info');
                          }}
                          className="pg-btn pg-btn-ghost !text-[var(--red-text)]"
                        >
                          <IconTrash size={14} />
                          删除事件
                        </button>
                      ) : (
                        <span />
                      )}
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          onClick={closeTopOverlay}
                          className="pg-btn pg-btn-ghost"
                        >
                          取消
                        </button>
                        <button
                          type="button"
                          onClick={handleSaveEventDrawer}
                          className="pg-btn pg-btn-primary"
                        >
                          保存事件配置
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              );
            })()}
          </div>
        )}
      </div>
    </div>
  );
}
