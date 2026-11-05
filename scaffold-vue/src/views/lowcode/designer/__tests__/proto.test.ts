import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { ICON_PATHS } from '@yuanda-lowcode-ai/scaffold-ui/data/icons'
import { CATEGORY_GROUPS, KIND_ICON, MATERIAL_LIBRARY, NODE_KINDS, createNodeFromMaterial, dropZones, lockIcon, visibleIcon } from '../proto/data.ts'
import { createPrototypeState } from '../proto/state.ts'

const HERE = dirname(fileURLToPath(import.meta.url))
const STYLES_DIR = join(HERE, '../../../../../../packages/scaffold-ui/src/styles')
const DESIGNER_DIR = join(HERE, '..')
const NARROW_TS = join(STYLES_DIR, '../composables/narrow.ts')
const SHELL_FILE = join(DESIGNER_DIR, '../../../components/layout/AppShell.vue')
/**
 * 1:1 原型层的图标与令牌契约。
 *
 * `Icon.vue` 查不到名字时静默画 `file`，物料卡、层级树、选中工具条会整片变成同一个
 * 灰色方块而不报任何错 —— 类型检查与构建都拦不住，只能在这里钉住。
 * CSS 变量写错名字也是同一类静默：整条声明作废，文字落到继承来的颜色上。
 */

test('每种节点类型都有专属图标，且全部在图标注册表里登记过', () => {
  assert.deepEqual([...NODE_KINDS].sort(), Object.keys(KIND_ICON).sort(),
    'KIND_ICON 必须覆盖全部 NodeKind：漏一种，画布工具条上那颗组件就退化成通用文件图标')
  Object.entries(KIND_ICON).forEach(([kind, name]) => {
    assert.ok(ICON_PATHS[name], `${kind} 指向的图标 ${name} 没在 ICON_PATHS 里登记，渲染时会静默兜底成 file`)
  })
})

test('锁定与显隐是成对语义图标，不能拿不相干的图形代替', () => {
  assert.equal(lockIcon(true), 'lock')
  assert.equal(lockIcon(false), 'unlock')
  assert.equal(visibleIcon(true), 'eye')
  assert.equal(visibleIcon(false), 'eyeOff')
  ;[lockIcon(true), lockIcon(false), visibleIcon(true), visibleIcon(false)].forEach(name => {
    assert.ok(ICON_PATHS[name], `${name} 没在 ICON_PATHS 里登记`)
  })
  assert.notEqual(ICON_PATHS.lock, ICON_PATHS.unlock, '锁定态与解锁态必须画得不一样，否则工具条看不出当前状态')
  assert.notEqual(ICON_PATHS.eye, ICON_PATHS.eyeOff, '显示态与隐藏态必须画得不一样')
})

test('面板收起与展开用的是方向相反的成对图标', () => {
  const pairs = [['panelLeftClose', 'panelLeftOpen'], ['panelRightClose', 'panelRightOpen']]
  pairs.flat().forEach(name => {
    assert.ok(ICON_PATHS[name], `${name} 没在 ICON_PATHS 里登记`)
  })
  pairs.forEach(([close, open]) => assert.notEqual(ICON_PATHS[close], ICON_PATHS[open],
    `${close} 与 ${open} 箭头方向必须相反：收起往内指、展开往外指`))
})

test('物料分组默认全展开', () => {
  const proto = createPrototypeState()
  assert.ok(CATEGORY_GROUPS.every(group => !proto.groupCollapsed.value[group]),
    '一进来就该看见全部 19 张物料卡：默认折叠等于把物料藏起来，折叠只能由分组头那颗箭头一格一格控制')
  // 拖拽预览帧必须在拖拽起点建好并整段复用：每帧重建（createNodeFromMaterial 带随机后缀）会让预览抖
  const material = { id: 'x', kind: 'readout', name: '数值指标', category: '数据展示', categoryTone: 'mint', code: 'Statistic', desc: '' } as const
  proto.startMaterialDrag(material)
  const first = proto.dragPreview.value
  assert.ok(first && first.kind === 'readout', '拖物料时必须给出预览节点，落点处才画得出组件本体')
  assert.equal(proto.dragPreview.value?.id, first?.id, '预览节点必须复用同一份，不能每次读都换一颗')
  proto.resetDrag()
  assert.equal(proto.dragPreview.value, null, '松手后预览帧要清掉，否则画布上留一颗幽灵组件')
})

test('只有栅格列与插槽容器有可放置区域，其余组件都不是落点', () => {
  const proto = createPrototypeState()
  const materialOf = (kind: string) => MATERIAL_LIBRARY.find(item => item.kind === kind)!
  assert.deepEqual(dropZones(createNodeFromMaterial(materialOf('grid'))), [0, 1], '栅格两列各自是一个落点')
  assert.deepEqual(dropZones({ ...createNodeFromMaterial(materialOf('grid')), columns: [[], [], []] }), [0, 1, 2],
    '落点数量必须以节点自己的 columns 为准：画三列只让放两列，第三列就是画得出放不进的地方')
  assert.deepEqual(dropZones(createNodeFromMaterial(materialOf('emptySlot'))), [undefined], '插槽容器整腔是一个落点')
  // 画布上现有那四颗（页面标题 / 数值指标 / 查询表单 / 数据表格）压根没有可放置区域
  proto.nodes.value.forEach(node => {
    assert.deepEqual(dropZones(node), [], `${node.kind} 没有可放置区域，在它身上松手不该被当成落点`)
  })
})

test('在没有可放置区域的地方松手，不改节点树也不进撤销栈', () => {
  const proto = createPrototypeState()
  const before = JSON.stringify(proto.nodes.value)
  const historyBefore = proto.history.value.length
  const table = proto.nodes.value.find(node => node.kind === 'table')!
  proto.startMaterialDrag(MATERIAL_LIBRARY.find(item => item.kind === 'readout')!)
  proto.dropInsideContainer(table.id)
  assert.equal(JSON.stringify(proto.nodes.value), before,
    '往数据表格身上放组件必须整件事没发生：它没有 children，塞进去就产出一份渲染器与生成器都不认的树')
  assert.equal(proto.history.value.length, historyBefore, '非法落点不该留一步撤销')
  assert.equal(proto.dragPreview.value, null, '松手后拖拽态一律要清干净')

  // 顶层落点同理：没算出 activeDropIndex（例如 dragover 没落到列表上）就不许插
  const material = MATERIAL_LIBRARY.find(item => item.kind === 'card')!
  proto.startMaterialDrag(material)
  proto.activeDropIndex.value = null
  proto.dropAtTopIndex()
  assert.equal(JSON.stringify(proto.nodes.value), before, '没有落点索引时松手不该往 0 号位塞东西')
})

test('树上拖拽：before / after 换顺序，inside 只给容器，锁定的不动', () => {
  const proto = createPrototypeState()
  const ids = () => proto.nodes.value.map(node => node.id)
  const [header, readout] = ids()

  proto.treeDragNodeId.value = readout
  proto.treeDrop(readout, header, 'before')
  assert.deepEqual(ids(), [readout, header, 'node-filter-1', 'node-table-1'], '拖到第一行的上半区就该排到它前面')

  proto.treeDrop(readout, header, 'after')
  assert.deepEqual(ids(), [header, readout, 'node-filter-1', 'node-table-1'], '拖到下半区就该排到它后面')

  // 表格没有可放置区域，往它身上「放进去」必须整件事没发生
  const table = proto.nodes.value.find(node => node.kind === 'table')!
  const before = JSON.stringify(proto.nodes.value)
  const historyBefore = proto.history.value.length
  proto.treeDragNodeId.value = header
  proto.treeDrop(header, table.id, 'inside')
  assert.equal(JSON.stringify(proto.nodes.value), before, '树里也不能把组件塞进一颗表格')
  assert.equal(proto.history.value.length, historyBefore, '非法落点不该留一步撤销')

  // 锁定：别人可以排到它前面，它自己拖不动
  const filter = proto.nodes.value.find(node => node.kind === 'filter')!
  const tableNode = proto.nodes.value.find(node => node.kind === 'table')!
  proto.toggleLock(filter.id)
  proto.treeDragNodeId.value = tableNode.id
  proto.treeDrop(tableNode.id, filter.id, 'before')
  assert.deepEqual(ids().slice(0, 3), [header, readout, tableNode.id], '未锁定的组件可以排到锁定组件前面')

  const order = JSON.stringify(proto.nodes.value)
  proto.treeDragNodeId.value = filter.id
  proto.treeDrop(filter.id, header, 'before')
  assert.equal(JSON.stringify(proto.nodes.value), order, '锁定组件自己不能被拖走')
})

test('设计器 CSS 里用到的每一个令牌都真的存在，不许有写错名字的静默失效', () => {
  // 平台侧定义的全部自定义属性（含 :root 与 data-theme 两套）
  const platform = new Set<string>()
  for (const file of readdirSync(STYLES_DIR)) {
    if (!file.endsWith('.css')) continue
    for (const m of readFileSync(join(STYLES_DIR, file), 'utf8').matchAll(/(--[a-z][a-z0-9-]*)\s*:/g)) platform.add(m[1])
  }
  assert.ok(platform.size > 80, `没读到平台样式令牌（只 ${platform.size} 个），这条用例的路径已经失效`)

  const files = [join(DESIGNER_DIR, 'proto', 'proto.css')]
    .concat(readdirSync(DESIGNER_DIR).filter(name => name.endsWith('.vue')).map(name => join(DESIGNER_DIR, name)))
    .concat(readdirSync(join(DESIGNER_DIR, 'proto')).filter(name => name.endsWith('.vue')).map(name => join(DESIGNER_DIR, 'proto', name)))
  const problems: string[] = []
  for (const file of files) {
    const css = readFileSync(file, 'utf8')
    // 文件内自己声明的（含 .pf-body 上的 --pf-rail-w 这类局部变量）算已定义
    const local = new Set([...css.matchAll(/(--[a-z][a-z0-9-]*)\s*:/g)].map(m => m[1]))
    for (const m of css.matchAll(/var\((--[a-z][a-z0-9-]*)\)/g)) {
      if (!platform.has(m[1]) && !local.has(m[1])) problems.push(`${file.split('/').pop()}: ${m[1]}`)
    }
  }
  // 带兜底值的 var(--x, y) 不在这条的射程内：它本来就允许令牌缺席
  assert.deepEqual([...new Set(problems)].sort(), [], '这些 var() 引用的令牌不存在，整条声明会作废、颜色落到继承值上')
})

test('窄屏断点全平台只有一个数，手抄的每一处都得跟 narrow.ts 对上', () => {
  // 交付项目按已发布的 npm 版本装物料包，还没发版的导出它 import 不到，
  // 所以外壳与 CSS 只能把同一个数抄一遍。抄的这几处不会报错，只会在窄屏下错开一格。
  const declared = readFileSync(NARROW_TS, 'utf8').match(/NARROW_QUERY = '\(max-width: (\d+)px\)'/)
  assert.ok(declared, 'narrow.ts 里 NARROW_QUERY 的形状变了，这条用例的射程已经失效')
  const px = declared[1]

  const sites: [string, string][] = [
    ['外壳导航 AppShell.vue', SHELL_FILE],
    ['平台样式 layout.css', join(STYLES_DIR, 'layout.css')],
    ['原型层 proto.css', join(DESIGNER_DIR, 'proto', 'proto.css')],
    ['原型顶栏 DesignerTopbar.vue', join(DESIGNER_DIR, 'DesignerTopbar.vue')],
  ]
  for (const [label, file] of sites) {
    const text = readFileSync(file, 'utf8')
    assert.ok(/max-width:\s*\d+px/.test(text), `${label} 里连一条 max-width 都没有，这条用例的射程已经失效`)
    assert.ok(
      new RegExp(`max-width:\\s*${px}px`).test(text),
      `${label} 的窄屏断点不是 ${px}px：JS 与 CSS 会错开一格，抽屉展开着而版式还是三栏`,
    )
  }
})
