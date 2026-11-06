import { ref } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { ApiError, authApi, authSession, type CurrentUser, type LoginRequest, type LoginResponse, type RegisterRequest, type RouteItem } from '@/api'
import { setPermissions } from '@/composables/permission'
import type { NavGroup, NavLeaf } from '@/data'
import { resolveView } from '@/shell/views'
import { clearDictCache } from '@yuanda-lowcode-ai/scaffold-ui/composables/dict'
import { clearDeptCache } from '@yuanda-lowcode-ai/scaffold-ui/composables/dept'

/* ============================================================
   登录态、权限与动态菜单。
   菜单与路由完全来自后端 /api/auth/routes（sys_menu 表），
   「组件标识 → 页面组件」这张表由装配处登记进 shell/views.ts，这里只查不建。
   ============================================================ */

export const authed = ref<boolean>(authSession.isAuthenticated())
export const currentUser = ref<CurrentUser | null>(authSession.user() as CurrentUser | null)
export const navGroups = ref<NavGroup[]>([])
export const dynamicRoutes = ref<RouteRecordRaw[]>([])
export const sessionVersion = ref(0)
/** 页面组件标识 → 当前账号可访问的路由路径（如 monitor/operlog/index → /monitor/operlog） */
export const componentPaths = ref<Record<string, string>>({})

/** 当前账号可访问时返回该页面路径，否则 null；跨页跳转统一走这里，避免写死菜单路径 */
export function pathOf(component: string) {
  return componentPaths.value[component] ?? null
}
let resetSessionRoutes: (() => void) | undefined

/** 由路由入口注册，外壳只通过 logout 清理会话，避免循环导入路由。 */
export function setSessionRouteReset(handler: () => void) {
  resetSessionRoutes = handler
}

// 刷新页面后先用本地缓存的权限，随后由 refreshUser() 以服务端为准
setPermissions(currentUser.value?.permissions ?? [])

/**
 * sys_menu.component 的解析规则：
 * 1. 底座既有页面的文件名与 component 值不同名，保留显式别名；
 * 2. 其余页面按 `views/<component>.vue` 约定自动解析（例如 system/notice/index
 *    对应 views/system/notice/index.vue），新增页面无需再改动本文件。
 */
/**
 * 菜单里的 component 标识 → 组件：解析表住在 shell/views.ts 的注册表里，由装配处按顺序登记。
 * 这里不再自己 glob 一次 —— 那样只能覆盖底座自己那一个 views 目录，宿主（平台前端、以及将来
 * 二开的交付项目）的页面就永远解析不到，只能把文件塞进底座目录，方向刚好反了。
 */
function resolveComponent(component: string | undefined) {
  return resolveView(component)
}

/** 路由名由路径推导：/system/user → system-user */
export function routeNameOf(path: string) {
  return path.replace(/^\/+|\/+$/g, '').replace(/\//g, '-') || 'home'
}

function normalizePath(path?: string) {
  if (!path) return ''
  return path.startsWith('/') ? path : `/${path}`
}

function leafOf(item: RouteItem): NavLeaf | null {
  const path = normalizePath(item.path)
  if (!path || !resolveComponent(item.component)) return null
  return { key: routeNameOf(path), label: item.name, icon: item.icon || 'file', path }
}

function buildNav(tree: RouteItem[]): NavGroup[] {
  const groups: NavGroup[] = []
  for (const node of tree) {
    const children = node.children ?? []
    if (!children.length) {
      const leaf = leafOf(node)
      if (leaf) groups.push({ key: leaf.key, label: leaf.label, icon: leaf.icon, single: true, items: [leaf] })
      continue
    }
    const items = children.map(leafOf).filter((leaf): leaf is NavLeaf => Boolean(leaf))
    if (items.length) groups.push({ key: `group-${node.id ?? node.name}`, label: node.name, icon: node.icon || 'file', items })
  }
  return groups
}

function buildRoutes(groups: NavGroup[], tree: RouteItem[]): RouteRecordRaw[] {
  const componentByPath = new Map<string, string>()
  const walk = (nodes: RouteItem[]) => nodes.forEach(node => {
    if (node.path && node.component) componentByPath.set(normalizePath(node.path), node.component)
    walk(node.children ?? [])
  })
  walk(tree)
  return groups.flatMap(group => group.items.flatMap(item => {
    const loader = resolveComponent(componentByPath.get(item.path))
    if (!loader) return []
    return [{
      path: item.path.replace(/^\//, ''),
      name: item.key,
      component: loader,
      meta: { title: item.label, group: group.single ? undefined : group.label },
    }]
  }))
}

export async function loadRoutes() {
  const version = sessionVersion.value
  const data = await authApi.routes()
  if (version !== sessionVersion.value) return
  const groups = buildNav(data.routes)
  navGroups.value = groups
  dynamicRoutes.value = buildRoutes(groups, data.routes)
  const visible = new Set(groups.flatMap(group => group.items.map(item => item.path)))
  const paths: Record<string, string> = {}
  const walk = (nodes: RouteItem[]) => nodes.forEach(node => {
    const path = normalizePath(node.path)
    if (node.component && visible.has(path)) paths[node.component] = path
    walk(node.children ?? [])
  })
  walk(data.routes)
  componentPaths.value = paths
}

/** 以服务端为准刷新当前用户与权限码 */
export async function refreshUser() {
  const version = sessionVersion.value
  const user = await authApi.me()
  if (version !== sessionVersion.value) return user
  currentUser.value = user
  setPermissions(user.permissions ?? [])
  const token = authSession.token()
  if (token) authSession.save({ token, expiresIn: remainingSeconds() }, user, authSession.isRemembered())
  return user
}

function remainingSeconds() {
  return Math.max(60, Math.round((authSession.expiresAt() - Date.now()) / 1000))
}

/** 拿到令牌后的统一开场：先落令牌与占位用户，再以服务端返回的当前用户为准 */
async function openSession(login: LoginResponse, remember: boolean, seed: { username: string; nickname: string }, version: number) {
  authSession.save(login, seed, remember)
  const user = await authApi.me()
  if (version !== sessionVersion.value) throw new Error('登录状态已变化，请重新登录')
  authSession.save(login, user, remember)
  currentUser.value = user
  setPermissions(user.permissions ?? [])
  authed.value = true
  return user
}

export async function login(credentials: LoginRequest, remember: boolean) {
  clearSession()
  const version = sessionVersion.value
  try {
    const loginResponse = await authApi.login(credentials)
    if (version !== sessionVersion.value) throw new Error('登录状态已变化，请重新登录')
    return await openSession(loginResponse, remember, { username: credentials.username, nickname: credentials.username }, version)
  } catch (error) {
    if (version === sessionVersion.value) clearSession()
    throw error
  }
}

/**
 * 租户自助注册：后端建租户 + 管理员账号 + 菜单并直接回令牌，所以注册成功即等于登录成功。
 * 顺带把租户编码记进 sessionStorage，下次回到登录页时预填。
 */
export async function registerTenant(credentials: RegisterRequest, remember: boolean) {
  clearSession()
  const version = sessionVersion.value
  try {
    const result = await authApi.register(credentials)
    if (version !== sessionVersion.value) throw new Error('注册状态已变化，请重新提交')
    rememberTenantCode(result.tenantCode)
    return await openSession(result, remember, { username: credentials.adminUsername, nickname: credentials.adminUsername }, version)
  } catch (error) {
    if (version === sessionVersion.value) clearSession()
    throw error
  }
}

function rememberTenantCode(tenantCode: string) {
  try { sessionStorage.setItem('scaffold.login.tenantCode', tenantCode) } catch { /* 存储不可用时忽略 */ }
}

/**
 * 平台管理员切换会话租户（POST /api/auth/switch-tenant）。
 * 后端吊销旧令牌并签发 tenantId 指向目标租户的新令牌；这里落新令牌并清空菜单、权限与用户缓存，
 * 由调用方导航到 '/'，路由守卫按新会话重建路由 —— 与刷新页面走同一条已验证的路径。
 */
export async function switchTenant(tenantId: number | null) {
  const login = await authApi.switchTenant({ tenantId })
  authSession.save(login, currentUser.value ?? { username: '', nickname: '' }, authSession.isRemembered())
  sessionVersion.value += 1
  currentUser.value = null
  navGroups.value = []
  dynamicRoutes.value = []
  componentPaths.value = {}
  setPermissions([])
  resetSessionRoutes?.()
}

export function clearSession() {
  sessionVersion.value += 1
  authed.value = false
  resetSessionRoutes?.()
  currentUser.value = null
  navGroups.value = []
  dynamicRoutes.value = []
  componentPaths.value = {}
  setPermissions([])
  authSession.clear()
  clearDictCache()
  clearDeptCache()
}

export async function logout() {
  // 先捕获旧 token 发出退出请求，再同步清理；慢请求不能留下旧菜单或清掉新会话。
  const task = authSession.isAuthenticated() ? authApi.logout() : Promise.resolve()
  clearSession()
  try {
    await task
  } catch (error) {
    if (!(error instanceof ApiError && error.code === 401)) console.warn('logout failed', error)
  }
}
