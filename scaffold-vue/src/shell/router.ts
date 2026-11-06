import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { setUnauthorizedHandler } from '@/api'
import { authed, clearSession, dynamicRoutes, loadRoutes, navGroups, refreshUser, sessionVersion, setSessionRouteReset } from '@/composables/auth'
import { pushToast } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { APP } from '@/data'
import AppShell from '@/components/layout/AppShell.vue'
import { shellRoutes } from './routes'

/**
 * 路由工厂（方案 §5.2 壳包化）。
 *
 * 与拆出去之前的 `router/index.ts` 唯一的行为差别：静态路由不再只有一份写死的表，
 * 而是先由底座登记、再等宿主补 —— 谁先登记谁占住那个路由名，见 `routes.ts`。
 * 登记表由 `createScaffoldApp` 按顺序填，这里只读结果，避免装配与构造两笔动作的先后被搞混。
 * 守卫、落点计算、401 处理这些逻辑一格都没改。
 */

export const BASE_STATIC_ROUTES: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    name: 'root',
    component: AppShell,
    children: [
      { path: 'no-access', name: 'no-access', component: () => import('@/views/NoAccess.vue'), meta: { title: '暂未分配菜单' } },
      { path: ':pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFound.vue'), meta: { title: '页面不存在' } },
    ],
  },
]

export function createScaffoldRouter() {
  const router = createRouter({
    history: createWebHistory(),
    routes: shellRoutes() as RouteRecordRaw[],
  })

  let routesReady = false
  let routeLoad: Promise<boolean> | null = null
  let cancelRouteLoad: (() => void) | null = null
  const removeRoutes: Array<() => void> = []

  /** 登录后的落点：有工作台权限去工作台，否则去第一个可见菜单 */
  function homePath() {
    return navGroups.value[0]?.items[0]?.path ?? '/no-access'
  }

  function resetRoutes() {
    removeRoutes.splice(0).forEach(remove => remove())
    routesReady = false
    cancelRouteLoad?.()
    cancelRouteLoad = null
    routeLoad = null
  }
  setSessionRouteReset(resetRoutes)

  async function ensureRoutes() {
    if (routesReady) return true
    if (routeLoad) return routeLoad
    const version = sessionVersion.value
    const cancelled = new Promise<boolean>(resolve => { cancelRouteLoad = () => resolve(false) })
    const task = Promise.race([cancelled, (async () => {
      await Promise.all([loadRoutes(), refreshUser()])
      if (version !== sessionVersion.value || !authed.value) return false
      dynamicRoutes.value.forEach(route => removeRoutes.push(router.addRoute('root', route)))
      routesReady = true
      return true
    })()])
    routeLoad = task
    try {
      return await task
    } finally {
      if (routeLoad === task) {
        routeLoad = null
        cancelRouteLoad = null
      }
    }
  }

  router.beforeEach(async (to) => {
    if (to.meta.public) {
      return authed.value && to.name === 'login' ? { path: '/' } : true
    }
    if (!authed.value) {
      return { name: 'login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
    }
    if (!routesReady) {
      const version = sessionVersion.value
      try {
        if (!await ensureRoutes()) return false
      } catch {
        if (version !== sessionVersion.value) return false
        clearSession()
        pushToast('菜单加载失败，请重新登录', 'danger')
        return { name: 'login' }
      }
      // 动态路由刚注册：按原地址重新解析，保留查询参数（深链如 ?trace=user:1）
      return to.path === '/' ? { path: homePath(), replace: true } : { path: to.path, query: to.query, hash: to.hash, replace: true }
    }
    if (to.path === '/') return { path: homePath(), replace: true }
    return true
  })

  router.afterEach((to) => {
    const title = to.meta.title as string | undefined
    document.title = title ? `${title} · ${APP.name}` : APP.name
  })

  // 任一接口返回 401：清理登录态并回到登录页
  let redirecting = false
  setUnauthorizedHandler(() => {
    if (redirecting || !authed.value) return
    redirecting = true
    clearSession()
    pushToast('登录已过期，请重新登录', 'info')
    router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } }).finally(() => { redirecting = false })
  })

  return router
}
