import type { RouteRecordRaw } from 'vue-router'

/**
 * 应用壳之外的静态路由怎么由宿主申报（方案 §5.2 壳包化）。
 *
 * 与 `views.ts` 同一条口径：先申报的一方占住那个路由名，后来的不静默替换。
 * 分开两个模块是因为认领的键不一样 —— 视图按菜单 `component` 标识，路由按 `name`。
 */

let entries: Array<RouteRecordRaw> = []
const owners = new Map<string, string>()

/**
 * @param owner 申报方，用于同名被拒时说得清是谁被谁挡了
 * @returns 被拒的那几条路由
 */
export function registerShellRoutes(owner: string, routes: RouteRecordRaw[]): RouteRecordRaw[] {
  const refused: RouteRecordRaw[] = []

  routes.forEach((route) => {
    const name = typeof route.name === 'string' ? route.name : ''
    if (name && owners.has(name)) {
      refused.push(route)
      return
    }
    if (name) owners.set(name, owner)
    entries.push(route)
  })

  return refused
}

/** 装配处构造 router 时取这一份，顺序就是申报顺序 */
export function shellRoutes(): RouteRecordRaw[] {
  return [...entries]
}

/** 这个名字被谁占着：被拒的那一方要能在界面上说出是谁 */
export function shellRouteOwner(name: string): string | undefined {
  return owners.get(name)
}

export function resetShellRoutes(): void {
  entries = []
  owners.clear()
}
