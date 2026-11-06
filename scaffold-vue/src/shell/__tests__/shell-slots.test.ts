import test from 'node:test'
import assert from 'node:assert/strict'
import {
  SHELL_SLOT_NAMES,
  registerShellSlot,
  resetShellSlots,
  shellSlotItems,
} from '../slots.ts'

/**
 * 外壳扩展位（方案 §5.2 壳包化）。
 *
 * 为什么不用 `scaffold:platform-only` 标记块解决：Topbar 里那处平台交互（「回到平台租户」）
 * 散在六行上 —— 一条 import、一个 `useRouter`、toast 那行、函数体、模板里那一项。用标记块就得
 * 在一个交付文件里埋六对，改的人只要漏一对，裁完不是编译不过就是留下半个引用。
 * 所以这里要的是「外壳只渲染有人申报过的东西」，平台的代码整体离开交付文件。
 */

const Fake = { name: 'TenantSwitchMenuItem' }
const Other = { name: 'AuditBadge' }

test.beforeEach(() => resetShellSlots())

test('没人申报时扩展位是空的，外壳照常常空', () => {
  assert.deepEqual(shellSlotItems('user-menu'), [])
})

test('位置名不在清单里就拒收，防止申报到一个没人渲染的地方', () => {
  assert.ok(!SHELL_SLOT_NAMES.includes('sidebar-footer' as never), '这一版只开一个扩展位')
  assert.equal(registerShellSlot('platform', 'sidebar-footer', Fake), false)
})

test('多项共存且按申报顺序排', () => {
  registerShellSlot('platform', 'user-menu', Fake)
  registerShellSlot('other', 'user-menu', Other)

  assert.deepEqual(shellSlotItems('user-menu'), [Fake, Other])
})

test('同一位置重复申报同一组件不产生两份', () => {
  registerShellSlot('platform', 'user-menu', Fake)
  registerShellSlot('platform', 'user-menu', Fake)

  assert.equal(shellSlotItems('user-menu').length, 1, '装配被跑第二次（dev 重载）时不能冒出两个同名菜单项')
})
