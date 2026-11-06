import test from 'node:test'
import assert from 'node:assert/strict'
import {
  registerShellRoutes,
  resetShellRoutes,
  shellRoutes,
} from '../routes.ts'

/**
 * 挂在应用壳之外的静态路由怎么由宿主申报（方案 §5.2）。
 *
 * 设计器是这一格的真实形状：画布要占满视口，套在 AppShell 的导航轨与顶栏里就等于把人干活的
 * 地方切掉两块 —— 它不属于动态菜单，只能走静态路由。而静态路由今天写死在 `router/index.ts`
 * 里，靠一对 `scaffold:platform-only` 标记块从交付快照里挖掉：底座得知道一条只有平台才有的路由。
 * 换成申报之后，底座只认「有人贡献了几条路由」这件事。
 */

interface RouteLike {
  path: string
  name: string
}

const designer: RouteLike = { path: '/lowcode/designer/:id', name: 'designer' }
const login: RouteLike = { path: '/login', name: 'login' }

test.beforeEach(() => resetShellRoutes())

test('没人申报时只剩底座自己的那几条', () => {
  registerShellRoutes('base', [login])
  assert.deepEqual(shellRoutes().map(route => route.name), ['login'])
})

test('宿主申报的路由追加在底座之后，不动底座的顺序', () => {
  registerShellRoutes('base', [login])
  registerShellRoutes('platform', [designer])

  assert.deepEqual(shellRoutes().map(route => route.name), ['login', 'designer'])
})

test('同名路由不让后来者静默替换', () => {
  const hijack: RouteLike = { path: '/lowcode/designer/:id', name: 'designer' }
  registerShellRoutes('platform', [designer])
  registerShellRoutes('other', [hijack])

  assert.equal(shellRoutes().length, 1, '同名只留一条')
  assert.equal(shellRoutes()[0].path, designer.path, '先申报的那方占住这个名字')
})

test('申报空表不产生任何条目', () => {
  registerShellRoutes('base', [login])
  registerShellRoutes('platform', [])

  assert.equal(shellRoutes().length, 1)
})
