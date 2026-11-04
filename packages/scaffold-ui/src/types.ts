/* ============================================================
   物料层共享类型：分页与查询形状、后端契约行结构、导航树。
   品牌与站点配置不在包内（属宿主 app），这里只放组件与 composable 用到的形状。
   ============================================================ */

/* ── 统一请求与分页形状（宿主 api 层从这里 re-export，全仓只有一份） ── */

/** 查询参数：undefined / null / 空串在发请求前被剔除 */
export type QueryParams = Record<string, string | number | boolean | null | undefined>

/** 服务端分页结果：rows 当页数据，total 符合条件的总条数 */
export interface PageResult<T> {
  pageNum: number
  pageSize: number
  total: number
  rows: T[]
}

/** 字典项：label 显示文本，value 存储值，tone 颜色语义，isDefault 是否默认选中 */
export interface DictOption {
  label: string
  value: string
  tone?: string
  isDefault?: boolean
}

/* ── 导航 ─────────────────────────────────────────────────── */
export interface NavLeaf {
  key: string
  label: string
  icon: string
  path: string
}

export interface NavGroup {
  key: string
  label: string
  icon: string
  /** 顶层单页菜单（如工作台）没有分组标题 */
  single?: boolean
  items: NavLeaf[]
}

/* ── 业务行类型（与后端返回字段对应） ─────────────────────── */
export interface BaseRow {
  id: number
  status: number
  createTime?: string
  [key: string]: any
}

/** 菜单行：sys_menu 的一行，type 区分目录 / 菜单 / 按钮 */
export interface Menu extends BaseRow {
  name: string
  parentId: number
  type: 'dir' | 'menu' | 'btn'
  icon?: string
  path?: string
  /** 前端页面组件标识，由宿主路由解析成 views 下的 .vue */
  component?: string
  permission?: string
  visible?: number
  sort?: number
  children?: Menu[]
}

/** 部门行：sys_dept 的一行，children 为下级部门 */
export interface Dept extends BaseRow {
  name: string
  parentId: number
  leader?: string
  phone?: string
  email?: string
  sort?: number
  memberCount?: number
  children?: Dept[]
}

/** 用户行：sys_user 的一行，deptName / postName / roleName 由后端联查带出 */
export interface UserRow {
  id: number
  username: string
  nickname: string
  phone: string
  email: string
  deptName: string
  postName: string
  roleName: string
  status: number
  createTime: string
  lastLogin: string
}

/** 泳道块：startSec / endSec 为当日秒数，count 笔数，failed 失败笔数，maxCost 最大耗时（毫秒），ids 关联日志 ID */
export interface TimelineBlock {
  startSec: number
  endSec: number
  count: number
  failed: number
  maxCost: number
  ids: number[]
}

/**
 * 登录热力：cells 星期（0 周一 … 6 周日）× 小时的登录 / 失败次数；
 * anomalies 失败次数达到 threshold 的 IP（kind=ip，spread 尝试账号数）或账号（kind=user，spread 来源 IP 数）
 */
export interface LoginHeatmap {
  days: number
  since: string
  threshold: number
  cells: Array<{ dow: number; hour: number; total: number; failed: number }>
  anomalies: Array<{ kind: 'ip' | 'user'; key: string; failed: number; total: number; spread: number; firstTime: string; lastTime: string }>
}

/** 权限关系图：users 用户节点（无用户查询权限时为空），roles 角色（menuIds 授予的菜单），menus 菜单，userRoles [用户ID, 角色ID] 关联 */
export interface PermissionGraph {
  users: Array<{ id: number; username: string; nickname: string; deptName: string | null; status: number }>
  roles: Array<{ id: number; name: string; code: string; dataScope: string; status: number; builtin: boolean; userCount: number; menuIds: number[] }>
  menus: Array<{ id: number; parentId: number; name: string; type: 'dir' | 'menu' | 'btn'; icon: string | null; path: string | null; permission: string | null; sort: number; visible: number; status: number }>
  userRoles: Array<[number, number]>
  usersVisible: boolean
}
