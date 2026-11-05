import React from 'react';
import {
  CanvasNode,
  CATEGORY_TONE_STYLES,
  TableDensity,
  TableRecord,
} from '../data/designerData';
import {
  IconArrowDown,
  IconArrowUp,
  IconCheck,
  IconComponentKind,
  IconCopy,
  IconEye,
  IconEyeOff,
  IconLock,
  IconPlus,
  IconTrash,
  IconUnlock,
} from './Icons';

interface CanvasNodeRendererProps {
  node: CanvasNode;
  isSelected: boolean;
  selectedNodeId: string | null;
  previewMode: boolean;
  isGhostPreview?: boolean;
  isBeingDragged?: boolean;
  draggingNodeId?: string | null;
  dragPreviewNode?: CanvasNode | null;
  canMoveUp: boolean;
  canMoveDown: boolean;
  tableRecords: TableRecord[];
  activeContainerTarget: { nodeId: string; colIndex?: number } | null;
  onSelect: (id: string) => void;
  onMoveUp: (id: string) => void;
  onMoveDown: (id: string) => void;
  onDuplicate: (id: string) => void;
  onToggleLock?: (id: string) => void;
  onToggleVisible?: (id: string) => void;
  onDelete: (id: string) => void;
  onToggleRowCheck: (rowId: string) => void;
  onToggleAllRows: () => void;
  onChangeTableDensity: (nodeId: string, density: TableDensity) => void;
  onNodeDragStart?: (nodeId: string, e: React.DragEvent) => void;
  onNodeDragEnd?: () => void;
  onContainerDragOver?: (nodeId: string, colIndex: number | undefined, e: React.DragEvent) => void;
  onContainerDragLeave?: (nodeId: string, colIndex: number | undefined, e: React.DragEvent) => void;
  onContainerDrop?: (nodeId: string, colIndex: number | undefined, e: React.DragEvent) => void;
  onQuickAddInsideSlot?: () => void;
}

export const CanvasNodeRenderer: React.FC<CanvasNodeRendererProps> = ({
  node,
  isSelected,
  selectedNodeId,
  previewMode,
  isGhostPreview = false,
  isBeingDragged = false,
  draggingNodeId = null,
  dragPreviewNode = null,
  canMoveUp,
  canMoveDown,
  tableRecords,
  activeContainerTarget,
  onSelect,
  onMoveUp,
  onMoveDown,
  onDuplicate,
  onToggleLock,
  onToggleVisible,
  onDelete,
  onToggleRowCheck,
  onToggleAllRows,
  onChangeTableDensity,
  onNodeDragStart,
  onNodeDragEnd,
  onContainerDragOver,
  onContainerDragLeave,
  onContainerDrop,
  onQuickAddInsideSlot,
}) => {
  if (!node.visible && previewMode) {
    return null;
  }

  const isContainerHighlighted = (colIdx?: number) =>
    Boolean(
      !isGhostPreview &&
        draggingNodeId !== node.id &&
      activeContainerTarget &&
        activeContainerTarget.nodeId === node.id &&
        activeContainerTarget.colIndex === colIdx
    );

  const renderGhostPreviewBlock = () => {
    if (!dragPreviewNode) return null;
    return (
      <div data-ghost-preview="true" className="pg-ghost-preview-wrap my-1">
        <div className="pg-ghost-badge">
          <IconComponentKind kind={dragPreviewNode.kind} size={12} />
          <span>预览落位 · {dragPreviewNode.name}</span>
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
    );
  };

  return (
    <div
      data-node-id={node.id}
      draggable={!previewMode && !isGhostPreview && !node.locked}
      onDragStart={(e) => {
        if (isGhostPreview) return;
        e.stopPropagation();
        onNodeDragStart?.(node.id, e);
      }}
      onDragEnd={(e) => {
        if (isGhostPreview) return;
        e.stopPropagation();
        onNodeDragEnd?.();
      }}
      onClick={(e) => {
        if (isGhostPreview) return;
        e.stopPropagation();
        onSelect(node.id);
      }}
      style={{
        borderRadius: `${node.radiusPx}px`,
        padding: `${node.paddingPx}px`,
        marginBottom: node.marginBottomPx ? `${node.marginBottomPx}px` : undefined,
        background:
          node.surfaceStyle === 'glass'
            ? 'var(--pill-bg)'
            : node.surfaceStyle === 'subtle'
              ? 'var(--input-bg)'
              : undefined,
        opacity: node.visible ? 1 : 0.52,
      }}
      className={`pg-node-card group cursor-pointer ${
        isSelected && !previewMode && !isGhostPreview ? 'is-selected' : ''
      } ${isBeingDragged ? 'is-dragging-source' : ''}`}
    >
      {/* 仅在选中该组件时，于选中框右上角悬浮显示精巧的组件 ToolBar */}
      {isSelected && !previewMode && !isGhostPreview && (
        <div
          onClick={(e) => e.stopPropagation()}
          className="absolute -top-3.5 right-3 z-20 h-[28px] px-1.5 rounded-[999px] bg-[var(--blue)] text-white shadow-[var(--e-accent)] flex items-center gap-0.5"
        >
          <span className="px-2 text-[11.5px] font-[620] inline-flex items-center gap-1.5 select-none">
            <IconComponentKind kind={node.kind} size={12} />
            <span>{node.name}</span>
          </span>

          <span className="w-[1px] h-3 bg-white/25 mx-0.5" />

          <button
            type="button"
            aria-label="上移组件"
            title="上移组件"
            disabled={!canMoveUp || node.locked}
            onClick={() => onMoveUp(node.id)}
            className="w-[22px] h-[22px] rounded-full inline-flex items-center justify-center hover:bg-white/20 disabled:opacity-35 transition-colors cursor-pointer"
          >
            <IconArrowUp size={12} />
          </button>
          <button
            type="button"
            aria-label="下移组件"
            title="下移组件"
            disabled={!canMoveDown || node.locked}
            onClick={() => onMoveDown(node.id)}
            className="w-[22px] h-[22px] rounded-full inline-flex items-center justify-center hover:bg-white/20 disabled:opacity-35 transition-colors cursor-pointer"
          >
            <IconArrowDown size={12} />
          </button>
          <button
            type="button"
            aria-label="复制组件"
            title="复制组件"
            onClick={() => onDuplicate(node.id)}
            className="w-[22px] h-[22px] rounded-full inline-flex items-center justify-center hover:bg-white/20 transition-colors cursor-pointer"
          >
            <IconCopy size={12} />
          </button>
          <button
            type="button"
            aria-label={node.visible ? '隐藏组件' : '显示组件'}
            title={node.visible ? '隐藏组件' : '组件已隐藏（点击显示）'}
            onClick={() => onToggleVisible?.(node.id)}
            className={`w-[22px] h-[22px] rounded-full inline-flex items-center justify-center transition-colors cursor-pointer ${
              !node.visible ? 'bg-white/30 text-white' : 'hover:bg-white/20'
            }`}
          >
            {node.visible ? <IconEye size={12} /> : <IconEyeOff size={12} />}
          </button>
          <button
            type="button"
            aria-label={node.locked ? '解锁组件' : '锁定组件'}
            title={node.locked ? '组件已锁定（点击解锁）' : '锁定组件'}
            onClick={() => onToggleLock?.(node.id)}
            className={`w-[22px] h-[22px] rounded-full inline-flex items-center justify-center transition-colors cursor-pointer ${
              node.locked ? 'bg-white/30 text-white' : 'hover:bg-white/20'
            }`}
          >
            {node.locked ? <IconLock size={12} /> : <IconUnlock size={12} />}
          </button>
          <button
            type="button"
            aria-label="删除组件"
            title={node.locked ? '组件已锁定' : '删除组件'}
            disabled={node.locked}
            onClick={() => onDelete(node.id)}
            className="w-[22px] h-[22px] rounded-full inline-flex items-center justify-center hover:bg-[#ff5a50] disabled:opacity-35 transition-colors cursor-pointer"
          >
            <IconTrash size={12} />
          </button>
        </div>
      )}

      {/* 1. 页面主标题栏 */}
      {node.kind === 'header' && (
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div className="max-w-[680px]">
            <div className="flex items-center gap-2.5">
              <h1
                style={{
                  fontSize: '28px',
                  fontWeight: 740,
                  lineHeight: 1.15,
                  letterSpacing: '-0.03em',
                }}
                className="text-[var(--ink)]"
              >
                {node.props.title}
              </h1>
              <span className="pg-tag pg-tag-ok">
                <span className="pg-tag-dot" />
                产线联机正常
              </span>
            </div>
            <p className="mt-2 text-[13px] text-[var(--ink-muted)] leading-[1.55]">
              {node.props.subtitle}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" className="pg-btn pg-btn-secondary">
              {node.props.secondaryActionText || '导出工艺留档'}
            </button>
            <button type="button" className="pg-btn pg-btn-primary">
              {node.props.primaryActionText || '批量核准放行'}
            </button>
          </div>
        </div>
      )}

      {/* 2. 良率仪器读数台（单屏视觉主角 + 斜线慢操作填充 + 刻度线 + 深色规格铭牌） */}
      {node.kind === 'readout' && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <span className="pg-section-label">{node.props.title}</span>
              <span className="text-[12px] text-[var(--ink-soft)]">{node.props.subtitle}</span>
            </div>
            {node.props.showSpecPlate !== false && (
              <div className="pg-spec-plate px-3 py-1.5 flex items-center gap-3">
                <span style={{ letterSpacing: '0.08em' }} className="text-[var(--plate-muted)]">
                  工位铭牌
                </span>
                <span>ND-P3-FORM-02</span>
                <span className="text-[var(--plate-muted)]">|</span>
                <span className="text-[#9be35a]">采样周期 {node.refreshInterval}</span>
              </div>
            )}
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4 items-stretch">
            {/* 左侧主角大读数（40px / 720 / -0.045em + 上标单位 + 环比胶囊） */}
            <div
              className="lg:col-span-5 rounded-[16px] p-4 border border-[var(--line)] flex flex-col justify-between"
              style={{
                background:
                  'linear-gradient(135deg, rgba(130, 221, 45, 0.085), rgba(255, 255, 255, 0.72))',
              }}
            >
              <div className="flex items-center justify-between">
                <span className="text-[12px] font-[600] text-[var(--ink-muted)]">
                  实时直通良率读数
                </span>
                <span className="pg-tag pg-tag-ok">
                  <span className="pg-tag-dot" />
                  环比 {node.props.heroMetricDelta || '+0.28%'}
                </span>
              </div>
              <div className="my-2 flex items-baseline gap-1">
                <span
                  style={{
                    fontSize: '40px',
                    fontWeight: 720,
                    letterSpacing: '-0.045em',
                    lineHeight: 1.05,
                  }}
                  className="font-mono-num text-[var(--ink)]"
                >
                  {node.props.heroMetricValue || '99.42'}
                </span>
                <sup className="font-mono-num text-[14px] font-[650] text-[var(--ink-muted)] -top-4">
                  {node.props.heroMetricUnit || '%'}
                </sup>
              </div>
              <div className="flex items-center justify-between text-[12px] text-[var(--ink-soft)] pt-2 border-t border-[var(--line)]">
                <span>在制电芯总数</span>
                <span className="font-mono-num font-[640] text-[var(--ink)]">18,420 支</span>
              </div>
            </div>

            {/* 右侧仪器刻度柱与只读读数 .kv-grid */}
            <div className="lg:col-span-7 flex flex-col justify-between gap-3">
              {/* 刻度柱与斜线填充 --hatch */}
              <div className="rounded-[16px] p-3.5 border border-[var(--line)] bg-[var(--input-bg)]">
                <div className="flex items-center justify-between text-[12px] mb-2">
                  <span className="font-[600] text-[var(--ink-muted)]">
                    化成恒流充放电区间负荷（当前班次推进线）
                  </span>
                  <span className="font-mono-num text-[11.5px] text-[var(--leaf-text)] font-[620]">
                    现在 14:28 · 负荷 84.6%
                  </span>
                </div>
                {/* 仪器刻度条 */}
                <div className="relative h-7 rounded-[8px] border border-[var(--line-strong)] overflow-hidden bg-[var(--surface-solid)] flex items-center">
                  <div
                    className="h-full transition-all"
                    style={{
                      width: '68%',
                      background: 'rgba(130, 221, 45, 0.22)',
                    }}
                  />
                  {node.props.showHatchBar !== false && (
                    <div
                      className="h-full border-l border-r border-[var(--line-strong)]"
                      style={{
                        width: '16.6%',
                        background: 'var(--hatch)',
                      }}
                      title="当前慢操作静置老化区间"
                    />
                  )}
                  {/* 现在线 */}
                  <div
                    className="h-full w-[2px]"
                    style={{
                      background: 'var(--lime)',
                      boxShadow: '0 0 0 3px rgba(130, 221, 45, 0.24)',
                    }}
                  />
                </div>
                {/* 底部刻度线 --tick */}
                <div className="mt-1.5 flex items-center justify-between font-mono-num text-[11px] text-[var(--ink-faint)]">
                  <span>08:00</span>
                  <span>10:00</span>
                  <span>12:00</span>
                  <span className="text-[var(--ink-muted)] font-[620]">14:28（当前柱）</span>
                  <span>16:00</span>
                </div>
              </div>

              {/* 只读读数 .kv-grid 两列 gap 1px */}
              <div className="pg-kv-grid">
                <div className="pg-kv-cell">
                  <div className="text-[11px] font-[600] text-[var(--ink-soft)]">
                    OCV 压降标准差
                  </div>
                  <div className="mt-0.5 font-mono-num text-[13px] font-[640] text-[var(--ink)]">
                    0.42 <sup className="text-[10.5px] text-[var(--ink-soft)]">mV</sup>
                  </div>
                </div>
                <div className="pg-kv-cell">
                  <div className="text-[11px] font-[600] text-[var(--ink-soft)]">
                    化成舱均温偏差
                  </div>
                  <div className="mt-0.5 font-mono-num text-[13px] font-[640] text-[var(--ink)]">
                    ±0.35 <sup className="text-[10.5px] text-[var(--ink-soft)]">°C</sup>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 3. 多维条件查询区 */}
      {node.kind === 'filter' && (
        <div>
          <div className="flex flex-wrap items-end justify-between gap-3">
            <div className="flex flex-wrap items-end gap-3">
              {(node.props.filterFields || []).map((field) => (
                <div
                  key={field.fieldKey}
                  style={{ width: `${field.widthPx}px` }}
                  className="flex flex-col gap-1.5"
                >
                  <label
                    htmlFor={`field-${node.id}-${field.fieldKey}`}
                    className="text-[12px] font-[600] text-[var(--ink-muted)]"
                  >
                    {field.label}
                  </label>
                  <input
                    id={`field-${node.id}-${field.fieldKey}`}
                    type="text"
                    readOnly={!previewMode}
                    placeholder={field.placeholder}
                    className="pg-input"
                  />
                </div>
              ))}
            </div>
            <div className="flex items-center gap-2 ml-auto">
              <button type="button" className="pg-btn pg-btn-ghost">
                重置条件
              </button>
              <button type="button" className="pg-btn pg-btn-secondary">
                执行查询
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 4. 主数据批次表格（表头 40 吸顶实色底、三档行高 40/48/56、吸左勾选列 48 宽、吸右操作列） */}
      {node.kind === 'table' && (() => {
        const density: TableDensity = node.props.density || 'standard';
        const rowHeight = density === 'compact' ? 40 : density === 'relaxed' ? 56 : 48;
        const cellPx = density === 'compact' ? 14 : density === 'relaxed' ? 18 : 16;
        const allChecked = tableRecords.length > 0 && tableRecords.every((r) => r.checked);

        return (
          <div>
            {/* 面板头：最低 60 高风格 */}
            <div className="flex flex-wrap items-center justify-between gap-3 pb-3.5">
              <div>
                <div className="pg-section-label">{node.props.title}</div>
                <p className="text-[12px] text-[var(--ink-soft)] mt-0.5">
                  {node.props.subtitle}
                </p>
              </div>

              {/* 分段控件：紧凑 40 / 标准 48 / 宽松 56 */}
              <div className="flex items-center gap-2">
                <span className="text-[12px] text-[var(--ink-soft)]">行高密度</span>
                <div
                  className="m-groove inline-flex items-center gap-0.5"
                  onClick={(e) => e.stopPropagation()}
                >
                  {(
                    [
                      { key: 'compact', label: '紧凑 40' },
                      { key: 'standard', label: '标准 48' },
                      { key: 'relaxed', label: '宽松 56' },
                    ] as { key: TableDensity; label: string }[]
                  ).map((item) => {
                    const active = density === item.key;
                    return (
                      <button
                        key={item.key}
                        type="button"
                        onClick={() => onChangeTableDensity(node.id, item.key)}
                        className={`h-[28px] px-2.5 rounded-[8px] text-[12.5px] font-[600] transition-all cursor-pointer ${
                          active
                            ? 'm-pill text-[var(--blue)]'
                            : 'text-[var(--ink-muted)] hover:text-[var(--ink)]'
                        }`}
                      >
                        {item.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* 表格容器（隐藏横向滚动条） */}
            <div className="overflow-x-auto no-scrollbar rounded-[16px] border border-[var(--line)]">
              <table className="w-full border-collapse text-left">
                <thead>
                  <tr
                    style={{ height: '40px', background: 'var(--surface-solid)' }}
                    className="border-b border-[var(--line)] text-[12px] font-[620] text-[var(--ink-soft)]"
                  >
                    <th
                      style={{ width: '48px', background: 'var(--surface-solid)' }}
                      className="sticky left-0 z-10 text-center"
                    >
                      <button
                        type="button"
                        aria-label="全选所有批次行"
                        data-checked={allChecked}
                        onClick={(e) => {
                          e.stopPropagation();
                          onToggleAllRows();
                        }}
                        className="pg-checkbox mx-auto"
                      >
                        {allChecked && <IconCheck size={11} />}
                      </button>
                    </th>
                    <th style={{ padding: `0 ${cellPx}px` }}>电芯批次与产线柜位</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>产品分类</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>当班责任人</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>一次直通率</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>化成耗时</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>放行状态</th>
                    <th style={{ padding: `0 ${cellPx}px` }}>权限标识</th>
                    <th
                      style={{
                        padding: `0 ${cellPx}px`,
                        background: 'var(--surface-solid)',
                      }}
                      className="sticky right-0 z-10 text-right"
                    >
                      工艺操作
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {tableRecords.map((row) => {
                    const rowCat = CATEGORY_TONE_STYLES[row.categoryTone];
                    return (
                      <tr
                        key={row.id}
                        style={{
                          height: `${rowHeight}px`,
                          background: row.checked ? 'var(--blue-soft)' : 'transparent',
                        }}
                        className="border-b last:border-b-0 border-[var(--line)] hover:bg-[var(--hover-bg)] transition-colors"
                      >
                        <td
                          style={{
                            width: '48px',
                            background: row.checked
                              ? 'var(--blue-soft)'
                              : 'var(--surface-solid)',
                          }}
                          className="sticky left-0 z-10 text-center"
                        >
                          <button
                            type="button"
                            aria-label={`选择批次 ${row.batchCode}`}
                            data-checked={Boolean(row.checked)}
                            onClick={(e) => {
                              e.stopPropagation();
                              onToggleRowCheck(row.id);
                            }}
                            className="pg-checkbox mx-auto"
                          >
                            {row.checked && <IconCheck size={11} />}
                          </button>
                        </td>
                        <td style={{ padding: `0 ${cellPx}px` }}>
                          <div className="font-mono-num text-[13px] font-[620] text-[var(--ink)]">
                            {row.batchCode}
                          </div>
                          <div className="text-[12px] text-[var(--ink-soft)] mt-[1px]">
                            {row.lineName}
                          </div>
                        </td>
                        <td style={{ padding: `0 ${cellPx}px` }}>
                          <span
                            style={{ backgroundColor: rowCat.bg, color: rowCat.fg }}
                            className="h-[22px] px-2.5 rounded-[999px] text-[12px] font-[600] inline-flex items-center"
                          >
                            {row.categoryLabel}
                          </span>
                        </td>
                        <td style={{ padding: `0 ${cellPx}px` }} className="text-[13px]">
                          {row.operator}
                        </td>
                        <td
                          style={{ padding: `0 ${cellPx}px` }}
                          className="font-mono-num text-[13px] font-[640]"
                        >
                          {row.yieldRate}
                        </td>
                        <td
                          style={{ padding: `0 ${cellPx}px` }}
                          className="font-mono-num text-[12.5px] text-[var(--ink-muted)]"
                        >
                          {row.cycleMs}
                        </td>
                        <td style={{ padding: `0 ${cellPx}px` }}>
                          <span className={`pg-tag pg-tag-${row.status}`}>
                            <span className="pg-tag-dot" />
                            {row.statusText}
                          </span>
                        </td>
                        <td style={{ padding: `0 ${cellPx}px` }}>
                          <span className="pg-code-chip">{row.permissionCode}</span>
                        </td>
                        <td
                          style={{
                            padding: `0 ${cellPx}px`,
                            background: row.checked
                              ? 'var(--blue-soft)'
                              : 'var(--surface-solid)',
                          }}
                          className="sticky right-0 z-10 text-right"
                        >
                          <button
                            type="button"
                            onClick={(e) => e.stopPropagation()}
                            className="pg-btn pg-btn-sm pg-btn-text"
                          >
                            复核曲线
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            {/* 表格底部统计与分页 */}
            <div className="pt-3 flex items-center justify-between text-[12px] text-[var(--ink-soft)]">
              <span>
                共 <strong className="font-mono-num text-[var(--ink)]">{tableRecords.length}</strong> 个待审批次 · 已勾选{' '}
                <strong className="font-mono-num text-[var(--blue)]">
                  {tableRecords.filter((r) => r.checked).length}
                </strong>{' '}
                项
              </span>
              <div className="flex items-center gap-1.5 font-mono-num">
                <button type="button" className="pg-btn pg-btn-sm pg-btn-ghost" disabled>
                  上一页
                </button>
                <span className="px-2.5 py-1 rounded-[8px] m-pill text-[var(--ink)] font-[640]">
                  1 / 4
                </span>
                <button type="button" className="pg-btn pg-btn-sm pg-btn-ghost">
                  下一页
                </button>
              </div>
            </div>
          </div>
        );
      })()}

      {/* 5. 产线工序流向图谱（1.2px 连线 + 分类色节点 + 脉冲实心点与 18% 光环） */}
      {node.kind === 'topology' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="pg-section-label">{node.props.title}</span>
            <span className="text-[12px] text-[var(--ink-soft)]">{node.props.subtitle}</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3 items-center relative">
            {[
              {
                step: '正负极合浆涂布',
                code: 'STEP-COAT-A',
                tone: 'sand' as const,
                metric: '99.81%',
                stateText: '节拍稳定',
              },
              {
                step: '注液与高温静置',
                code: 'STEP-INJ-B',
                tone: 'sky' as const,
                metric: '99.54%',
                stateText: '主工序激活',
                active: true,
              },
              {
                step: '负压化成与分容',
                code: 'STEP-FORM-C',
                tone: 'mint' as const,
                metric: '99.42%',
                stateText: '实时采集中',
              },
            ].map((item, idx) => {
              const st = CATEGORY_TONE_STYLES[item.tone];
              return (
                <div
                  key={item.code}
                  className={`rounded-[16px] p-3.5 border transition-all ${
                    item.active
                      ? 'border-[var(--blue)] bg-[var(--surface-solid)] shadow-[0_0_0_3px_var(--blue-soft)]'
                      : 'border-[var(--line)] bg-[var(--input-bg)]'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <span
                      style={{ backgroundColor: st.bg, color: st.fg }}
                      className="h-[22px] px-2.5 rounded-[999px] text-[12px] font-[600] inline-flex items-center gap-1.5"
                    >
                      <span
                        className="w-2 h-2 rounded-full"
                        style={{
                          backgroundColor: st.fg,
                          boxShadow: `0 0 0 3px ${st.bg}`,
                        }}
                      />
                      {item.step}
                    </span>
                    <span className="pg-code-chip">{item.code}</span>
                  </div>
                  <div className="mt-3 flex items-baseline justify-between">
                    <span className="text-[12px] text-[var(--ink-soft)]">{item.stateText}</span>
                    <span className="font-mono-num text-[18px] font-[680] text-[var(--ink)]">
                      {item.metric}
                    </span>
                  </div>
                  {idx < 2 && (
                    <div className="mt-2 pt-2 border-t border-[var(--line)] flex items-center justify-between text-[11.5px] text-[var(--ink-faint)]">
                      <span>工序连线损耗</span>
                      <span className="font-mono-num">0.04%</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 6. 插槽容器（可直接拖入组件原地渲染） */}
      {node.kind === 'emptySlot' && (
        <div
          onDragOver={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            onContainerDragOver?.(node.id, undefined, e);
          }}
          onDragLeave={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            if (e.currentTarget.contains(e.relatedTarget as Node)) return;
            onContainerDragLeave?.(node.id, undefined, e);
          }}
          onDrop={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            onContainerDrop?.(node.id, undefined, e);
          }}
          style={{
            border: isContainerHighlighted(undefined)
              ? '1.5px dashed var(--blue)'
              : '1px dashed var(--line-strong)',
            outlineOffset: '-3px',
            background: isContainerHighlighted(undefined)
              ? 'var(--blue-soft)'
              : undefined,
          }}
          className="rounded-[16px] p-4 transition-all"
        >
          {node.children && node.children.length > 0 ? (
            <div className="space-y-2.5">
              <div className="flex items-center justify-between text-[12px] text-[var(--ink-soft)] px-1">
                <span className="pg-section-label">{node.props.title || '插槽容器'}</span>
                <span>可继续拖入组件</span>
              </div>
              {node.children.map((child, cIdx) => (
                <CanvasNodeRenderer
                  key={child.id}
                  node={child}
                  isSelected={selectedNodeId === child.id}
                  selectedNodeId={selectedNodeId}
                  previewMode={previewMode}
                  isBeingDragged={draggingNodeId === child.id}
                  draggingNodeId={draggingNodeId}
                  dragPreviewNode={dragPreviewNode}
                  canMoveUp={cIdx > 0}
                  canMoveDown={cIdx < (node.children?.length || 0) - 1}
                  tableRecords={tableRecords}
                  activeContainerTarget={activeContainerTarget}
                  onSelect={onSelect}
                  onMoveUp={onMoveUp}
                  onMoveDown={onMoveDown}
                  onDuplicate={onDuplicate}
                  onToggleLock={onToggleLock}
                  onToggleVisible={onToggleVisible}
                  onDelete={onDelete}
                  onToggleRowCheck={onToggleRowCheck}
                  onToggleAllRows={onToggleAllRows}
                  onChangeTableDensity={onChangeTableDensity}
                  onNodeDragStart={onNodeDragStart}
                  onNodeDragEnd={onNodeDragEnd}
                  onContainerDragOver={onContainerDragOver}
                  onContainerDragLeave={onContainerDragLeave}
                  onContainerDrop={onContainerDrop}
                />
              ))}
              {isContainerHighlighted(undefined) && renderGhostPreviewBlock()}
            </div>
          ) : isContainerHighlighted(undefined) && dragPreviewNode ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between text-[12px] text-[var(--blue)] px-1">
                <span className="pg-section-label">{node.props.title || '插槽容器'}</span>
                <span>松手即可放入此插槽容器</span>
              </div>
              {renderGhostPreviewBlock()}
            </div>
          ) : (
            <div className="py-5 px-4 text-center flex flex-col items-center justify-center gap-2 cursor-copy">
              <div className="w-[42px] h-[42px] rounded-[14px] m-pill flex items-center justify-center text-[var(--blue)]">
                <IconPlus size={18} />
              </div>
              <div className="text-[13px] font-[620] text-[var(--ink-muted)]">
                {node.props.title || '待放置插槽容器'}
              </div>
              <p className="text-[12px] text-[var(--ink-soft)] max-w-[380px]">
                直接将组件拖拽到此容器内松手即可原地放入
              </p>
            </div>
          )}
        </div>
      )}

      {/* 7. 常规基础组件：栅格容器（每一列均可独立接收拖拽组件并原地放入） */}
      {node.kind === 'grid' && (
        <div className="space-y-2.5">
          <div className="flex items-center justify-between">
            <span className="pg-section-label">{node.props.title || '栅格容器'}</span>
            <span className="text-[12px] text-[var(--ink-soft)]">两列均分 · 间距 16px</span>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[0, 1].map((colIdx) => {
              const colNodes = node.columns?.[colIdx] || [];
              const highlighted = isContainerHighlighted(colIdx);
              return (
                <div
                  key={colIdx}
                  onDragOver={(e) => {
                    if (isGhostPreview || draggingNodeId === node.id) return;
                    onContainerDragOver?.(node.id, colIdx, e);
                  }}
                  onDragLeave={(e) => {
                    if (isGhostPreview || draggingNodeId === node.id) return;
                    if (e.currentTarget.contains(e.relatedTarget as Node)) return;
                    onContainerDragLeave?.(node.id, colIdx, e);
                  }}
                  onDrop={(e) => {
                    if (isGhostPreview || draggingNodeId === node.id) return;
                    onContainerDrop?.(node.id, colIdx, e);
                  }}
                  style={{
                    border: highlighted
                      ? '1.5px dashed var(--blue)'
                      : '1px dashed var(--line-strong)',
                    outlineOffset: '-3px',
                    background: highlighted ? 'var(--blue-soft)' : 'var(--input-bg)',
                  }}
                  className="rounded-[12px] p-3 min-h-[84px] transition-all flex flex-col justify-center"
                >
                  {colNodes.length > 0 ? (
                    <div className="space-y-2">
                      {colNodes.map((child, cIdx) => (
                        <CanvasNodeRenderer
                          key={child.id}
                          node={child}
                          isSelected={selectedNodeId === child.id}
                          selectedNodeId={selectedNodeId}
                          previewMode={previewMode}
                          isBeingDragged={draggingNodeId === child.id}
                          draggingNodeId={draggingNodeId}
                          dragPreviewNode={dragPreviewNode}
                          canMoveUp={cIdx > 0}
                          canMoveDown={cIdx < colNodes.length - 1}
                          tableRecords={tableRecords}
                          activeContainerTarget={activeContainerTarget}
                          onSelect={onSelect}
                          onMoveUp={onMoveUp}
                          onMoveDown={onMoveDown}
                          onDuplicate={onDuplicate}
                          onToggleLock={onToggleLock}
                          onToggleVisible={onToggleVisible}
                          onDelete={onDelete}
                          onToggleRowCheck={onToggleRowCheck}
                          onToggleAllRows={onToggleAllRows}
                          onChangeTableDensity={onChangeTableDensity}
                          onNodeDragStart={onNodeDragStart}
                          onNodeDragEnd={onNodeDragEnd}
                          onContainerDragOver={onContainerDragOver}
                          onContainerDragLeave={onContainerDragLeave}
                          onContainerDrop={onContainerDrop}
                        />
                      ))}
                      {highlighted && renderGhostPreviewBlock()}
                    </div>
                  ) : highlighted && dragPreviewNode ? (
                    <div className="space-y-1.5">
                      {renderGhostPreviewBlock()}
                    </div>
                  ) : (
                    <div className="py-3 text-center text-[12px] text-[var(--ink-soft)]">
                      栅格列 {colIdx + 1}（拖入组件至此直接放置）
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 8. 常规基础组件：面板卡片（同样支持拖入子组件并实时展示预览） */}
      {node.kind === 'card' && (
        <div
          onDragOver={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            onContainerDragOver?.(node.id, undefined, e);
          }}
          onDragLeave={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            if (e.currentTarget.contains(e.relatedTarget as Node)) return;
            onContainerDragLeave?.(node.id, undefined, e);
          }}
          onDrop={(e) => {
            if (isGhostPreview || draggingNodeId === node.id) return;
            onContainerDrop?.(node.id, undefined, e);
          }}
          className="space-y-3 rounded-[12px] transition-all"
        >
          <div className="flex items-center justify-between pb-2.5 border-b border-[var(--line)]">
            <span className="pg-section-label">{node.props.title || '面板卡片'}</span>
            <span className="text-[12px] text-[var(--ink-soft)]">{node.props.subtitle}</span>
          </div>
          {node.children && node.children.length > 0 ? (
            <div className="space-y-2.5">
              {node.children.map((child, cIdx) => (
                <CanvasNodeRenderer
                  key={child.id}
                  node={child}
                  isSelected={selectedNodeId === child.id}
                  selectedNodeId={selectedNodeId}
                  previewMode={previewMode}
                  isBeingDragged={draggingNodeId === child.id}
                  draggingNodeId={draggingNodeId}
                  dragPreviewNode={dragPreviewNode}
                  canMoveUp={cIdx > 0}
                  canMoveDown={cIdx < (node.children?.length || 0) - 1}
                  tableRecords={tableRecords}
                  activeContainerTarget={activeContainerTarget}
                  onSelect={onSelect}
                  onMoveUp={onMoveUp}
                  onMoveDown={onMoveDown}
                  onDuplicate={onDuplicate}
                  onToggleLock={onToggleLock}
                  onToggleVisible={onToggleVisible}
                  onDelete={onDelete}
                  onToggleRowCheck={onToggleRowCheck}
                  onToggleAllRows={onToggleAllRows}
                  onChangeTableDensity={onChangeTableDensity}
                  onNodeDragStart={onNodeDragStart}
                  onNodeDragEnd={onNodeDragEnd}
                  onContainerDragOver={onContainerDragOver}
                  onContainerDragLeave={onContainerDragLeave}
                  onContainerDrop={onContainerDrop}
                />
              ))}
              {isContainerHighlighted(undefined) && renderGhostPreviewBlock()}
            </div>
          ) : (
            <>
              <div className="pg-kv-grid">
                <div className="pg-kv-cell">
                  <div className="text-[11px] font-[600] text-[var(--ink-soft)]">关联主记录</div>
                  <div className="mt-0.5 text-[13px] font-[620] text-[var(--ink)]">宁德三期化成工位档案</div>
                </div>
                <div className="pg-kv-cell">
                  <div className="text-[11px] font-[600] text-[var(--ink-soft)]">最近同步时间</div>
                  <div className="mt-0.5 font-mono-num text-[13px] text-[var(--ink)]">14:30:00</div>
                </div>
              </div>
              {isContainerHighlighted(undefined) && renderGhostPreviewBlock()}
            </>
          )}
        </div>
      )}

      {/* 9. 常规基础组件：分段容器 */}
      {node.kind === 'tabs' && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <span className="pg-section-label">{node.props.title || '分段容器'}</span>
            <div className="m-groove inline-flex items-center gap-1">
              <button type="button" className="h-[28px] px-3 rounded-[8px] text-[12.5px] font-[600] m-pill text-[var(--blue)]">
                基本信息
              </button>
              <button type="button" className="h-[28px] px-3 rounded-[8px] text-[12.5px] font-[600] text-[var(--ink-muted)]">
                工艺参数
              </button>
              <button type="button" className="h-[28px] px-3 rounded-[8px] text-[12.5px] font-[600] text-[var(--ink-muted)]">
                审批记录
              </button>
            </div>
          </div>
          <div
            onDragOver={(e) => {
              if (isGhostPreview || draggingNodeId === node.id) return;
              onContainerDragOver?.(node.id, undefined, e);
            }}
            onDragLeave={(e) => {
              if (isGhostPreview || draggingNodeId === node.id) return;
              if (e.currentTarget.contains(e.relatedTarget as Node)) return;
              onContainerDragLeave?.(node.id, undefined, e);
            }}
            onDrop={(e) => {
              if (isGhostPreview || draggingNodeId === node.id) return;
              onContainerDrop?.(node.id, undefined, e);
            }}
            style={{
              border: isContainerHighlighted(undefined)
                ? '1.5px dashed var(--blue)'
                : '1px solid var(--line)',
              background: isContainerHighlighted(undefined)
                ? 'var(--blue-soft)'
                : 'var(--input-bg)',
            }}
            className="rounded-[12px] p-4 text-[13px] text-[var(--ink-muted)] transition-all space-y-2.5"
          >
            {node.children && node.children.length > 0 ? (
              <>
                {node.children.map((child, cIdx) => (
                  <CanvasNodeRenderer
                    key={child.id}
                    node={child}
                    isSelected={selectedNodeId === child.id}
                    selectedNodeId={selectedNodeId}
                    previewMode={previewMode}
                    isBeingDragged={draggingNodeId === child.id}
                    draggingNodeId={draggingNodeId}
                    dragPreviewNode={dragPreviewNode}
                    canMoveUp={cIdx > 0}
                    canMoveDown={cIdx < (node.children?.length || 0) - 1}
                    tableRecords={tableRecords}
                    activeContainerTarget={activeContainerTarget}
                    onSelect={onSelect}
                    onMoveUp={onMoveUp}
                    onMoveDown={onMoveDown}
                    onDuplicate={onDuplicate}
                    onToggleLock={onToggleLock}
                    onToggleVisible={onToggleVisible}
                    onDelete={onDelete}
                    onToggleRowCheck={onToggleRowCheck}
                    onToggleAllRows={onToggleAllRows}
                    onChangeTableDensity={onChangeTableDensity}
                    onNodeDragStart={onNodeDragStart}
                    onNodeDragEnd={onNodeDragEnd}
                    onContainerDragOver={onContainerDragOver}
                    onContainerDragLeave={onContainerDragLeave}
                    onContainerDrop={onContainerDrop}
                  />
                ))}
                {isContainerHighlighted(undefined) && renderGhostPreviewBlock()}
              </>
            ) : isContainerHighlighted(undefined) && dragPreviewNode ? (
              renderGhostPreviewBlock()
            ) : (
              <div>当前展示「基本信息」分段面板内容区域（可直接拖入组件放置）。</div>
            )}
          </div>
        </div>
      )}

      {/* 10. 常规基础组件：输入框 */}
      {node.kind === 'input' && (
        <div className="max-w-[360px] space-y-1.5">
          <label className="block text-[12px] font-[600] text-[var(--ink-muted)]">
            {node.props.title || '文本输入框'}
          </label>
          <input
            type="text"
            readOnly={!previewMode}
            placeholder={node.props.subtitle || '请输入内容...'}
            className="pg-input"
          />
        </div>
      )}

      {/* 11. 常规基础组件：下拉选择 */}
      {node.kind === 'select' && (
        <div className="max-w-[360px] space-y-1.5">
          <label className="block text-[12px] font-[600] text-[var(--ink-muted)]">
            {node.props.title || '下拉选择'}
          </label>
          <select className="pg-input" defaultValue="opt1">
            <option value="opt1">默认选项一</option>
            <option value="opt2">备选选项二</option>
            <option value="opt3">备选选项三</option>
          </select>
        </div>
      )}

      {/* 12. 常规基础组件：开关 */}
      {node.kind === 'switch' && (
        <div className="flex items-center justify-between max-w-[360px]">
          <div>
            <div className="text-[13px] font-[620] text-[var(--ink)]">{node.props.title || '状态开关'}</div>
            <div className="text-[12px] text-[var(--ink-soft)]">{node.props.subtitle || '开启后自动同步状态'}</div>
          </div>
          <button type="button" data-checked="true" className="pg-switch">
            <span className="pg-switch-thumb" />
          </button>
        </div>
      )}

      {/* 13. 常规基础组件：状态标签 */}
      {node.kind === 'tag' && (
        <div className="flex flex-wrap items-center gap-2">
          <span className="pg-tag pg-tag-ok">
            <span className="pg-tag-dot" />
            正常运行
          </span>
          <span className="pg-tag pg-tag-info">
            <span className="pg-tag-dot" />
            处理中
          </span>
          <span className="pg-tag pg-tag-danger">
            <span className="pg-tag-dot" />
            异常告警
          </span>
          <span className="pg-tag pg-tag-neutral">
            <span className="pg-tag-dot" />
            待机未启用
          </span>
        </div>
      )}

      {/* 14. 常规基础组件：按钮组 */}
      {node.kind === 'button' && (
        <div className="flex flex-wrap items-center gap-2.5">
          <button type="button" className="pg-btn pg-btn-primary">
            {node.props.primaryActionText || '主操作按钮'}
          </button>
          <button type="button" className="pg-btn pg-btn-secondary">
            {node.props.secondaryActionText || '次级按钮'}
          </button>
          <button type="button" className="pg-btn pg-btn-ghost">
            幽灵按钮
          </button>
          <button type="button" className="pg-btn pg-btn-danger">
            危险操作
          </button>
        </div>
      )}

      {/* 15. 常规基础组件：分割线 */}
      {node.kind === 'divider' && (
        <div className="py-2 flex items-center gap-3">
          <div className="h-[1px] flex-1 bg-[var(--line-strong)]" />
          <span className="text-[12px] text-[var(--ink-soft)]">{node.props.title || '区块分割线'}</span>
          <div className="h-[1px] flex-1 bg-[var(--line-strong)]" />
        </div>
      )}

      {/* 16. 常规基础组件：数字输入 */}
      {node.kind === 'number' && (
        <div className="max-w-[260px] space-y-1.5">
          <label className="block text-[12px] font-[600] text-[var(--ink-muted)]">
            {node.props.title || '数字输入'}
          </label>
          <div className="relative">
            <input
              type="number"
              defaultValue={100}
              readOnly={!previewMode}
              className="pg-input font-mono-num text-[12.5px] pr-9"
            />
            <sup className="absolute right-3 top-3 font-mono-num text-[10.5px] text-[var(--ink-soft)]">
              件
            </sup>
          </div>
        </div>
      )}

      {/* 17. 常规基础组件：日期选择 */}
      {node.kind === 'date' && (
        <div className="max-w-[300px] space-y-1.5">
          <label className="block text-[12px] font-[600] text-[var(--ink-muted)]">
            {node.props.title || '日期选择'}
          </label>
          <input
            type="date"
            defaultValue="2026-04-19"
            readOnly={!previewMode}
            className="pg-input font-mono-num text-[12.5px]"
          />
        </div>
      )}

      {/* 18. 常规基础组件：复选框 */}
      {node.kind === 'checkbox' && (
        <div className="flex flex-wrap items-center gap-4">
          {['选项一（已启用）', '选项二', '选项三'].map((label, idx) => (
            <label key={label} className="inline-flex items-center gap-2 text-[13px] cursor-pointer">
              <span data-checked={idx === 0} className="pg-checkbox">
                {idx === 0 && <IconCheck size={11} />}
              </span>
              <span>{label}</span>
            </label>
          ))}
        </div>
      )}

      {/* 19. 常规基础组件：分页器 */}
      {node.kind === 'pagination' && (
        <div className="flex items-center justify-between text-[12px] text-[var(--ink-soft)]">
          <span>共 <strong className="font-mono-num text-[var(--ink)]">128</strong> 条记录</span>
          <div className="flex items-center gap-1.5 font-mono-num">
            <button type="button" className="pg-btn pg-btn-sm pg-btn-ghost" disabled>上一页</button>
            <span className="px-2.5 py-1 rounded-[8px] m-pill text-[var(--blue)] font-[640]">1</span>
            <span className="px-2.5 py-1 rounded-[8px] text-[var(--ink-muted)]">2</span>
            <span className="px-2.5 py-1 rounded-[8px] text-[var(--ink-muted)]">3</span>
            <button type="button" className="pg-btn pg-btn-sm pg-btn-ghost">下一页</button>
          </div>
        </div>
      )}

      {/* 20. 常规基础组件：弹窗容器 */}
      {node.kind === 'modal' && (
        <div className="rounded-[16px] p-4 border border-[var(--line)] bg-[var(--input-bg)] flex items-center justify-between">
          <div>
            <div className="text-[13.5px] font-[640] text-[var(--ink)]">{node.props.title || '模态对话框容器'}</div>
            <div className="text-[12px] text-[var(--ink-soft)]">点击右侧按钮可在运行时从触发点展开圆角 24 弹窗</div>
          </div>
          <button type="button" className="pg-btn pg-btn-sm pg-btn-secondary">唤起弹窗预览</button>
        </div>
      )}
    </div>
  );
};
