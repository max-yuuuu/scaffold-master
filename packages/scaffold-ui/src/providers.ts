import type { Ref } from 'vue'
import type { QueryParams } from './types'

/* ============================================================
   注入点：接口、权限、字典三项能力由宿主 app 提供，包内组件与 composable
   一律经 scaffoldUi() 取用，不再 import 宿主模块（这样同一个包才能被
   平台和每个生成项目共用）。
   ============================================================ */

export interface ScaffoldHttp {
  /** 接口根地址，含 /api 前缀；上传与直连下载在这里拼绝对路径 */
  baseUrl(): string
  /** 当前访问令牌，未登录返回 null */
  token(): string | null
  get<T>(path: string, query?: QueryParams): Promise<T>
  put<T>(path: string, body?: unknown): Promise<T>
  /** 把异常翻译成人话：401 提示与后端信封 message 的口径由宿主决定 */
  errorMessage(error: unknown, fallback: string): string
}

export interface ScaffoldPermission {
  /** 是否拥有权限码：入参为空表示不限制，'*' 表示全部放行 */
  has(permission?: string | string[]): boolean
  /** 菜单组件标识 → 当前账号可访问的路由路径，无权限返回 null */
  pathOf(component: string): string | null
  /** 会话版本号：登录、退出、切换租户时递增，包内缓存据此失效 */
  sessionVersion: Ref<number>
}

export interface ScaffoldDict {
  /** 某个字典类型的原始行（label / value / status / tone / def），数据源由宿主决定 */
  load(type: string): Promise<Array<Record<string, unknown>>>
}

export interface ScaffoldUiOptions {
  http: ScaffoldHttp
  permission: ScaffoldPermission
  dict: ScaffoldDict
}

let installed: ScaffoldUiOptions | null = null

/** 包内取注入能力的唯一入口：未安装直接报错，不静默回退到宿主模块 */
export function scaffoldUi(): ScaffoldUiOptions {
  if (!installed) {
    throw new Error('@scaffold/ui 未安装：请在宿主 main.ts 调用 installScaffoldUi(app, { http, permission, dict })')
  }
  return installed
}

/**
 * 宿主 app 的唯一安装入口：把接口、权限、字典三项能力交给包。
 * 必须在 app.mount() 之前调用，包内 composable 首次取数时就会读取。
 */
export function installScaffoldUi(options: ScaffoldUiOptions) {
  installed = options
}
