import test from 'node:test'
import assert from 'node:assert/strict'
import {
  registerViews,
  resetViewRegistry,
  resolveView,
  viewConflicts,
  type ViewContribution,
} from '../views.ts'

/**
 * 视图注册表（方案 §5.2 壳包化的第一半）。
 *
 * 钉的是这件事：菜单里的 `component` 标识要能解析到组件，而解析表**不止底座一份**。
 * 今天它写死在 `composables/auth.ts` 里（一张对 `views` 目录的 glob 结果加一张 alias 表），
 * 所以平台自己的页面、以及将来交付项目二开的页面，只要不在这个目录里就永远解析不到 ——
 * 那不是"少一个功能"，是宿主必须把页面文件塞进底座目录才能被看见，方向刚好反了。
 *
 * 这里不碰 vite 的 glob：注册表只吃调用方递进来的表，所以能在纯 node 下测。
 * （注释里不要写那个 glob 的字面量：里面的星号斜杠会提前关掉本注释块，这次就撞过。）
 */

const loader = (name: string) => {
  const marked = async () => ({ default: { __name: name } })
  return Object.assign(marked, { __name: name })
}

const asLoader = (value: unknown) => value as { __name?: string }

test.beforeEach(() => resetViewRegistry())

test('注册表为空时任何标识都解析不到', () => {
  assert.equal(resolveView('system/user/index'), undefined)
  assert.equal(resolveView(undefined), undefined)
})

test('底座表按 alias 命中，alias 没有时按前缀加约定拼', () => {
  const base: ViewContribution = {
    prefix: '../views/',
    modules: {
      '../views/Dashboard.vue': loader('Dashboard'),
      '../views/system/Users.vue': loader('Users'),
      '../views/system/notice/index.vue': loader('Notice'),
    },
    alias: { 'dashboard/index': '../views/Dashboard.vue', 'system/user/index': '../views/system/Users.vue' },
  }
  registerViews('base', base)

  assert.equal(asLoader(resolveView('dashboard/index'))?.__name, 'Dashboard', 'alias 要生效')
  assert.equal(asLoader(resolveView('system/user/index'))?.__name, 'Users')
  // 没有 alias 的那条走约定：views/<component>.vue
  assert.equal(asLoader(resolveView('system/notice/index'))?.__name, 'Notice')
})

test('宿主贡献的表能解析底座根本没有的标识', () => {
  registerViews('base', { prefix: '../views/', modules: { '../views/Dashboard.vue': loader('Dashboard') }, alias: { 'dashboard/index': '../views/Dashboard.vue' } })
  registerViews('platform', {
    prefix: '../views/',
    modules: {
      '../views/lowcode/Projects.vue': loader('Projects'),
      '../views/lowcode/designer/DesignerView.vue': loader('DesignerView'),
    },
    alias: { 'lowcode/projects/index': '../views/lowcode/Projects.vue' },
  })

  assert.equal(asLoader(resolveView('dashboard/index'))?.__name, 'Dashboard')
  assert.equal(asLoader(resolveView('lowcode/projects/index'))?.__name, 'Projects', '宿主页面的标识必须能被解析到')
})

test('同标识不让后来者静默覆盖，冲突要留在清单里', () => {
  registerViews('base', { prefix: '../views/', modules: { '../views/Dashboard.vue': loader('Dashboard') }, alias: { 'dashboard/index': '../views/Dashboard.vue' } })
  registerViews('platform', { prefix: '../views/', modules: { '../views/Override.vue': loader('Override') }, alias: { 'dashboard/index': '../views/Override.vue' } })

  assert.equal(asLoader(resolveView('dashboard/index'))?.__name, 'Dashboard', '与 C 档「存在即跳过」同一口径：不静默盖掉先注册的')
  const conflicts = viewConflicts()
  assert.equal(conflicts.length, 1)
  assert.equal(conflicts[0].component, 'dashboard/index')
  assert.equal(conflicts[0].owner, 'base')
  assert.equal(conflicts[0].refused, 'platform')
})

test('拼不出键时返回空而不是留一个指向不存在文件的解析结果', () => {
  registerViews('base', { prefix: '../views/', modules: { '../views/Dashboard.vue': loader('Dashboard') } })

  assert.equal(resolveView('system/ghost/index'), undefined)
  assert.equal(resolveView(''), undefined)
})
