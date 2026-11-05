import test from 'node:test'
import assert from 'node:assert/strict'
import { createDesigner, initFromModel, propsOf, defaultPropsFor, PROPS, PALETTE, CONTROL_TYPES, BLOCKS, type ModelField } from '../design.ts'

/**
 * 设计器状态层（PR 6C）。
 *
 * <p>这一层是纯 TS、不碰 Vue：画布与属性面板只负责把「拖到哪、改了哪一格」翻译成功令，
 * 树的操作、撤销重做、序列化的口径全在这里，能脱离浏览器跑就跑 —— UI 一裹住，
 * 「move 之后原容器还剩几个」这种问题就只能靠人去点了。
 *
 * <p>落点合法性在前端也拦一份是为了手感（拖不动比报错好），<b>权威判定在后端 `PageDesign`</b>：
 * 两边都收紧的话，前端放过去的存库照样被拒，而那时人已经在设计器里画完了。
 */

const fields: ModelField[] = [
  { fieldCode: 'id', fieldName: '主键', javaType: 'Long', formType: 'INPUT', dictType: null, isPrimary: 1, isAutoIncrement: 1, isList: 1, isQuery: 0, queryType: '=', isInsert: 0, isEdit: 0, isRequired: 0 },
  { fieldCode: 'orderNo', fieldName: '订单编号', javaType: 'String', formType: 'INPUT', dictType: null, isPrimary: 0, isAutoIncrement: 0, isList: 1, isQuery: 1, queryType: 'LIKE', isInsert: 1, isEdit: 1, isRequired: 1 },
  { fieldCode: 'amount', fieldName: '订单金额', javaType: 'BigDecimal', formType: 'NUMBER', dictType: null, isPrimary: 0, isAutoIncrement: 0, isList: 1, isQuery: 0, queryType: '=', isInsert: 1, isEdit: 1, isRequired: 0 },
  { fieldCode: 'status', fieldName: '状态', javaType: 'Integer', formType: 'SELECT', dictType: 'order_status', isPrimary: 0, isAutoIncrement: 0, isList: 1, isQuery: 1, queryType: '=', isInsert: 1, isEdit: 1, isRequired: 0 },
  { fieldCode: 'createTime', fieldName: '创建时间', javaType: 'LocalDateTime', formType: 'DATETIME', dictType: null, isPrimary: 0, isAutoIncrement: 0, isList: 0, isQuery: 0, queryType: '=', isInsert: 0, isEdit: 0, isRequired: 0 },
]

const blocksOf = (json: any) => json.layout.children.map((node: any) => node.type)

test('空白设计只有一个页面容器，区块要拖进来才有', () => {
  const store = createDesigner()
  const json = store.json()
  assert.equal(json.version, '1.0')
  assert.equal(json.pageType, 'list')
  assert.equal(json.layout.type, 'PageContainer')
  assert.deepEqual(json.layout.children, [])
})

test('按模型初始化时标志位决定初始摆位，自增主键不进表单', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  assert.equal(store.json().modelRef, 'sales_order')
  assert.deepEqual(blocksOf(store.json()), ['SearchForm', 'Toolbar', 'DataTable', 'RowActions', 'FormContainer'])

  const search = store.childrenOf('SearchForm').map((node: any) => node.props.field)
  assert.deepEqual(search, ['orderNo', 'status'])
  // 列不拖拽，是 DataTable 上的声明：顺序照模型，主键不进列（5G 那条口径）
  assert.deepEqual(store.columns().map((column: any) => column.field), ['orderNo', 'amount', 'status'])
  const form = store.childrenOf('FormContainer').map((node: any) => node.props.field)
  assert.deepEqual(form, ['orderNo', 'amount', 'status'])
  // 创建时间在模型里不可写也不进列表，一处都不该出现
  const referenced = store.referencedFields()
  assert.equal(referenced.includes('createTime'), false)
  assert.equal(referenced.includes('id'), false)
})

test('控件插进容器按位置放，index 越界就夹到边界', () => {
  const store = createDesigner(initFromModel(fields))
  const [first] = store.childrenOf('SearchForm')
  store.insert('SearchForm', { type: 'NumberInput', props: { label: '金额下限', field: 'amount', queryType: '>=' } }, 0)
  assert.deepEqual(store.childrenOf('SearchForm').map((node: any) => node.props.field), ['amount', 'orderNo', 'status'])

  store.insert('SearchForm', { type: 'TextInput', props: { label: '不存在的插法', field: 'orderNo' } }, 99)
  assert.equal(store.childrenOf('SearchForm').length, 4)
  assert.equal(store.childrenOf('SearchForm')[3].props.field, 'orderNo')
  assert.equal(first.props.field, 'orderNo')
})

test('非法落点当场拒绝并说清该放哪儿', () => {
  const store = createDesigner(initFromModel(fields))
  // 按钮不是查询条件
  assert.throws(() => store.insert('SearchForm', { type: 'Button', props: { label: '查询', action: 'refresh' } }),
    /SearchForm 里不能放 Button/)
  // 查询控件不能摆进工具栏
  assert.throws(() => store.insert('Toolbar', { type: 'TextInput', props: { label: '订单编号', field: 'orderNo' } }),
    /Toolbar 里只能放 Button/)
  // 日期区间是查询条件，表单只写一个值
  assert.throws(() => store.insert('FormContainer', { type: 'DateRangePicker', props: { label: '区间', field: 'createTime' } }),
    /FormContainer 里不能放 DateRangePicker/)
  // 列不是组件：往表格上拖一个控件，等于让生成器猜这是列还是控件
  assert.throws(() => store.insert('DataTable', { type: 'TextInput', props: { label: '备注', field: 'remark' } }),
    /DataTable 只用 columns 声明列/)
  // 白名单外的组件也进不来
  assert.throws(() => store.insert('FormContainer', { type: 'Upload', props: { field: 'remark' } }), /不支持的组件 Upload/)
  assert.equal(store.childrenOf('SearchForm').length, 2)
})

test('跨容器移动是移走再插入，不是复制', () => {
  const store = createDesigner(initFromModel(fields))
  const amount = store.childrenOf('FormContainer').find((node: any) => node.props.field === 'amount')!
  store.move(amount.key, 'SearchForm', 0)
  assert.equal(store.childrenOf('SearchForm')[0].props.field, 'amount')
  assert.deepEqual(store.childrenOf('FormContainer').map((node: any) => node.props.field), ['orderNo', 'status'])

  // 同容器内换位
  const status = store.childrenOf('FormContainer').find((node: any) => node.props.field === 'status')!
  store.move(status.key, 'FormContainer', 0)
  assert.deepEqual(store.childrenOf('FormContainer').map((node: any) => node.props.field), ['status', 'orderNo'])
})

test('删容器连带子节点，撤销能整块还原', () => {
  const store = createDesigner(initFromModel(fields))
  // 先把节点对象拿到手：childrenOf 返回的是树上那个活数组，splice 之后它自己就变了
  const [firstControl, secondControl] = store.childrenOf('SearchForm')
  const formBefore = store.childrenOf('FormContainer').length
  store.remove(firstControl.key)
  assert.equal(store.childrenOf('SearchForm').length, 1)
  store.remove(secondControl.key)
  assert.equal(store.childrenOf('SearchForm').length, 0)
  store.undo()
  store.undo()
  assert.equal(store.childrenOf('SearchForm').length, 2)
  assert.equal(store.childrenOf('SearchForm')[0].props.field, 'orderNo')
  assert.equal(store.childrenOf('FormContainer').length, formBefore)
  // 属性面板按父容器决定给不给改，所以父节点要问得到
  assert.equal(store.parentOf(store.childrenOf('SearchForm')[0].key)?.type, 'SearchForm')
  assert.equal(store.parentOf(store.root.key), null)
})

test('撤销重做是一进一出，新操作清空重做栈', () => {
  const store = createDesigner()
  store.insert('PageContainer', { type: 'DataTable', props: {} })
  store.insert('PageContainer', { type: 'SearchForm', props: {} })
  assert.throws(() => store.insert('DataTable', { type: 'SearchForm', props: {} }), /不能再套容器/)
  // 区块本身是容器，往区块里再塞一个区块就是画布上点不到的第三层
  store.addColumn({ field: 'orderNo', title: '订单编号' })
  assert.equal(store.json().layout.children.length, 2)
  assert.equal(store.canUndo, true)

  store.undo()
  store.undo()
  assert.deepEqual(store.json().layout.children.map((node: any) => node.type), ['DataTable'])
  assert.equal(store.canRedo, true)
  store.redo()
  assert.equal(store.json().layout.children.length, 2)

  store.insert('PageContainer', { type: 'Toolbar', props: {} })
  assert.equal(store.canRedo, false, '新操作之后重做栈要清空，否则会跳回一个已经不在那条历史上的形状')
})

test('属性面板只渲染这个组件支持的那几格', () => {
  assert.deepEqual(propsOf('PageContainer'), ['title', 'desc'])
  assert.deepEqual(propsOf('DictSelect'), ['label', 'field', 'required', 'placeholder', 'queryType'])
  // 按钮没有「绑定字段」这一说，字典下拉没有「小数位」这一说
  assert.equal(propsOf('Button').includes('field'), false)
  assert.equal(propsOf('NumberInput').includes('precision'), false)
  // 每个组件都在表里，面板遇到没登记的类型会退化成「一格都不给改」，那是最难查的失败
  ;[...BLOCKS, 'PageContainer', ...Object.keys(PROPS)].forEach(type => {
    assert.ok(Object.prototype.hasOwnProperty.call(PROPS, type), `${type} 没登记属性清单`)
  })
  assert.deepEqual(propsOf('Toolbar'), [])
})

test('改属性只动那一格，空属性序列化时省略', () => {
  const store = createDesigner(initFromModel(fields))
  const [first] = store.childrenOf('SearchForm')
  store.setProp(first.key, 'label', '单据编号')
  store.setProp(first.key, 'placeholder', '')
  assert.equal(store.childrenOf('SearchForm')[0].props.label, '单据编号')
  assert.equal('placeholder' in (store.json().layout.children[0].children[0].props ?? {}), false)

  store.setTitle('销售订单台账')
  store.setDesc('只看已支付的')
  const json = store.json()
  assert.deepEqual(json.layout.props, { title: '销售订单台账', desc: '只看已支付的' })
})

test('序列化出去的形状不带内部 key，且读回来再出去是幂等的', () => {
  const store = createDesigner(initFromModel(fields))
  const first = store.json()
  const text = JSON.stringify(first)
  assert.equal(text.includes('"key"'), false, '内部 key 不该漏进 design_json：它是画布用的，不是协议的一部分')

  const again = createDesigner(JSON.parse(text)).json()
  assert.deepEqual(again, first, '第二次保存不能把形状改漂：库里存的应该是同一份')
})

test('绑定模型分两种：初始化那一下不进撤销栈，人选模型要进', () => {
  const seed = initFromModel(fields)
  seed.modelRef = 'sales_order'
  const store = createDesigner(seed)

  // 打开一份已有设计时要把库里的 modelCode 绑上，但那不是人做的一步：
  // 走 setModel 的话刚进来「撤销」就是可点的，点一下模型绑定就被抹成空，而画布看起来什么都没变
  store.bindModel('sales_order')
  assert.equal(store.canUndo, false, '刚打开的设计不该有一步可撤销')
  store.bindModel('other_model')
  assert.equal(store.canUndo, false, 'bindModel 任何时候都不产生历史')

  // 人在设计器里换模型是另一回事，得能撤回去
  store.setModel('sales_order')
  assert.equal(store.canUndo, true, '人选模型是人的动作，要能撤销')
  store.undo()
  assert.equal(store.json().modelRef, 'other_model', '撤销回到上一次的绑定，而不是抹掉')
})

test('引用清单在前端也算得出来，属性面板要能提示字段被哪几格用着', () => {
  const store = createDesigner(initFromModel(fields))
  assert.deepEqual(store.referencedFields(), ['orderNo', 'status', 'amount'])
  // 删掉列不等于不再引用：查询区与表单里还在用这个字段
  store.removeColumn(1)
  assert.deepEqual(store.referencedFields(), ['orderNo', 'status', 'amount'])
  const amount = store.childrenOf('FormContainer').find((node: any) => node.props.field === 'amount')!
  store.remove(amount.key)
  assert.deepEqual(store.referencedFields(), ['orderNo', 'status'])
})

test('物料库与白名单同源：每个类型正好出现一次，且都带图标与说明', () => {
  const items = PALETTE.flatMap(group => group.items)
  const types = items.map(item => item.type)
  assert.equal(new Set(types).size, types.length, '同一类型不该出现在两个分组里')
  assert.deepEqual([...types].sort(), [...BLOCKS, ...CONTROL_TYPES].sort(),
    '物料库必须正好是白名单里那些类型：多一格就是让人搭一个保存时才报 400 的页面')
  items.forEach(item => {
    assert.ok(item.icon && item.desc, `${item.type} 要带图标与说明，物料卡不能只有一行字`)
  })
})

test('落点默认属性：按钮必须带动作，控件带标签，区块不带东西', () => {
  // 动作是权限点的来源：空动作的按钮存进去，生成器只能猜这颗按钮要不要种菜单权限
  assert.deepEqual(defaultPropsFor('Button'), { label: '按钮', action: 'refresh' })
  assert.deepEqual(defaultPropsFor('SearchForm'), {})
  assert.equal(defaultPropsFor('TextInput').label, '文本输入')
})

/**
 * 画布工具条那三颗（复制 / 隐藏 / 锁定）背后的模型动作（PR 8C）。
 *
 * <p>visible 与 locked 是**画布上的会话状态**，不是页面的内容：它们和内部 key 一样
 * 不进 design_json —— 生成的那份页面里没有「设计器当前不想看这一格」这种概念。
 * 既然不影响产物，就绝不能被当成内容改动存进库，但必须能被撤销。
 */

const firstBlock = (store: any) => store.root.children[0].key

test('复制控件：紧跟在原件后面、属性照抄、撤销回到一个', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  const original = store.childrenOf('SearchForm')[0]

  const copyKey = store.duplicate(original.key)
  assert.notEqual(copyKey, original.key, '复制出来的必须是一个新节点')
  const siblings = store.childrenOf('SearchForm')
  const at = siblings.findIndex(node => node.key === copyKey)
  assert.equal(at, siblings.indexOf(original) + 1, '副本就该排在原件后面，插到末尾等于让人再拖一次')
  assert.equal(siblings[at].type, original.type)
  assert.deepEqual(siblings[at].props, original.props, '复制要连填好的属性一起带走，只复制一个空壳没意义')
  assert.equal(store.selected, copyKey, '复制完就该选中副本，接着改的是刚建出来的那一个')

  store.undo()
  assert.equal(store.childrenOf('SearchForm').length, siblings.length - 1)
  assert.ok(!store.childrenOf('SearchForm').some(node => node.key === copyKey))
})

test('区块不许复制：容器是按类型定位的，多出一个 SearchForm 整个模型就找不到它', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  const searchForm = store.root.children.find(node => node.type === 'SearchForm')!
  assert.throws(() => store.duplicate(searchForm.key), /按类型/,
    'locate() 靠 type 找容器，复制出第二个 SearchForm 后 insert/move/columns 全都会指到第一个上 —— '
    + '与其让画布出现一个改不动的幽灵区块，不如当场说清楚')
})

test('锁定：画布上拒绝再动这一格，但不写进 design_json', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  const key = firstBlock(store)

  store.setFlag(key, 'locked', true)
  assert.equal(store.find(key)!.locked, true)
  assert.ok(!JSON.stringify(store.json()).includes('"locked"'),
    '锁定是设计器自己的状态，存进库就是一列生成器不认的字段')
  assert.throws(() => store.remove(key), /锁定/)
  assert.throws(() => store.duplicate(key), /锁定/)
  assert.throws(() => store.move(key, 'Toolbar', 0), /锁定/)

  // 关掉就整个删掉，不留 locked: false —— 与 applyProp「空串就是没填」同一口径，
  // 否则撤销栈里会攒一堆「把不存在的东西改成 false」的空步，读回来的 JSON 也多了噪音
  store.setFlag(key, 'locked', false)
  assert.equal(store.find(key)!.locked, undefined)
  store.undo()
  assert.equal(store.find(key)!.locked, true, '取消锁定也要能撤回来')
})

test('隐藏：只影响画布预览，产物里那一格还在', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  const key = firstBlock(store)

  store.setFlag(key, 'visible', false)
  assert.equal(store.find(key)!.visible, false)
  assert.deepEqual(blocksOf(store.json()), ['SearchForm', 'Toolbar', 'DataTable', 'RowActions', 'FormContainer'],
    '画布上藏起来不等于从页面里删掉 —— 藏了还能存出去，存出去的东西必须还是那一套')

  store.setFlag(key, 'visible', true)
  assert.equal(store.find(key)!.visible, undefined, '显示是默认态，不该在节点上留一个 visible: true')
  store.undo()
  assert.equal(store.find(key)!.visible, false, '显示回去也要能撤回来')
})

test('标志位翻动不算内容改动：脏比对只看 design_json', () => {
  const store = createDesigner(initFromModel(fields))
  store.setModel('sales_order')
  const snapshot = JSON.stringify(store.json())
  store.setFlag(firstBlock(store), 'locked', true)
  store.setFlag(firstBlock(store), 'visible', false)
  assert.equal(JSON.stringify(store.json()), snapshot,
    '锁一下藏一下就提示「有未保存改动」，会让人为了关掉提示去点保存，那是把人往错的方向推')
})
