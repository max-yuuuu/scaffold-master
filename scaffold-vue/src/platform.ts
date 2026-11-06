import type { Component } from 'vue'
import type { ShellContribution } from '@/shell'

/**
 * 平台侧对应用壳的贡献（本文件属平台，不进交付快照 —— 见 `ScaffoldTrimPolicy` 的排除表）。
 *
 * 这两笔贡献各自解决一处「底座本来不认识平台」：
 * 1. 视图：平台那几个页面住在 `views/lowcode/**`，标识是菜单里写的 `lowcode/projects` 这一类。
 * 2. 静态路由：页面设计器要占满视口，套在 AppShell 的导航轨与顶栏里就等于把人干活的地方切掉两块，
 *    所以它挂在壳外、但仍走同一个登录守卫（不是 public），深链靠路径带页面 ID。
 *
 * 为什么现在这份文件还在底座目录里：① 这一刀只做「贡献点机制」，把平台页面搬到
 * `scaffold-platform/vue/` 是同刀的下一步（①b）。搬完之后这里的前缀与 glob 才会真正起作用 ——
 * 在此之前底座的 glob 恰好也覆盖得到这些文件，所以界面上看不出差别，别把「能跑」当成「已隔离」。
 */
export const platformContribution: ShellContribution = {
  owner: 'platform',
  views: {
    prefix: './views/',
    modules: import.meta.glob('./views/lowcode/**/*.vue') as Record<string, () => Promise<{ default: Component }>>,
  },
  routes: [
    {
      path: '/lowcode/designer/:id',
      name: 'designer',
      component: () => import('./views/lowcode/designer/DesignerView.vue'),
      meta: { title: '页面设计器' },
    },
  ],
}
