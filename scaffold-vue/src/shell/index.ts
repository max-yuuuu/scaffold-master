import type { RouteRecordRaw } from 'vue-router'
import type { Component } from 'vue'
import { registerViews, type ViewContribution, type ViewLoader } from './views'
import { registerShellRoutes, shellRouteOwner } from './routes'
import { BASE_STATIC_ROUTES, createScaffoldRouter } from './router'

/**
 * 应用壳的装配入口（方案 §5.2 壳包化）。
 *
 * 一句话形状：**底座先登记自己那一页一张表，宿主在后面补自己的**。
 * 顺序在这里是语义，不是风格 —— 先登记的一方占住标识，所以底座不会因为宿主后交了一张同名表
 * 就被换掉页面（`views.ts` 里那条不静默覆盖的规则）。
 *
 * 交付项目拿到的是同一份装配代码，只是没有宿主贡献：`createScaffoldApp()` 不带参数就是它。
 */

/**
 * 底座的视图表。glob 只能写在装配处：它是唯一知道自己目录在哪里的地方，
 * 而 `views.ts` 故意不认识 glob，才能在 node 下被直接测。
 */
const BASE_VIEW_MODULES = import.meta.glob('../views/**/*.vue') as Record<string, ViewLoader>

/** 历史命名的兜底：菜单写的 component 与文件名不一致的那几格（复数、大小写不齐） */
export const BASE_COMPONENT_ALIAS: Record<string, string> = {
  'dashboard/index': '../views/Dashboard.vue',
  'system/user/index': '../views/system/Users.vue',
  'system/role/index': '../views/system/Roles.vue',
  'system/menu/index': '../views/system/Menus.vue',
  'system/dept/index': '../views/system/Depts.vue',
  'system/post/index': '../views/system/Posts.vue',
  'system/dict/index': '../views/system/Dict.vue',
  'system/config/index': '../views/system/Configs.vue',
  'monitor/operlog/index': '../views/monitor/OperLog.vue',
  'monitor/loginlog/index': '../views/monitor/LoginLog.vue',
  'tool/gen/index': '../views/tool/Generator.vue',
}

export interface ShellContribution {
  /** 申报方名字，出冲突时要说清是谁被谁挡了 */
  owner: string
  views?: ViewContribution
  /** 挂在应用壳之外的路由（画布类页面占满视口，套进导航轨就等于切掉两块） */
  routes?: RouteRecordRaw[]
}

export interface ShellInstallResult {
  router: ReturnType<typeof createScaffoldRouter>
  /** 被底座挡掉的申报：装配处要显示出来，不能让人以为宿主页面生效了 */
  warnings: string[]
}

export function createScaffoldApp(contributions: ShellContribution[] = []): ShellInstallResult {
  registerViews('base', { modules: BASE_VIEW_MODULES, alias: BASE_COMPONENT_ALIAS })
  registerShellRoutes('base', BASE_STATIC_ROUTES)

  const warnings: string[] = []

  contributions.forEach((contribution) => {
    const { owner, views, routes } = contribution

    if (views) {
      registerViews(owner, views).forEach((conflict) => {
        warnings.push(`视图标识 ${conflict.component} 已由 ${conflict.owner} 登记，${owner} 的申报未生效`)
      })
    }

    const refusedRoutes = registerShellRoutes(owner, (routes ?? []) as RouteRecordRaw[])
    refusedRoutes.forEach((route) => {
      const name = typeof route.name === 'string' ? route.name : String(route.name ?? route.path)
      warnings.push(`路由名 ${name} 已由 ${shellRouteOwner(name) ?? 'base'} 申报，${owner} 的这条未生效`)
    })
  })

  return { router: createScaffoldRouter(), warnings }
}

export type { Component, ViewContribution }
