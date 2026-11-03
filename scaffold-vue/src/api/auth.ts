import type { HttpClient } from './http'

export interface LoginRequest {
  username: string
  password: string
  /** 租户编码（lc_tenant.tenant_code）：账号在多个租户下重名时必填，单租户可留空 */
  tenantCode?: string
  captcha: string
  captchaUuid: string
}

/** 切换租户（仅平台管理员）：tenantId 为空表示回到自己所属的平台租户 */
export interface SwitchTenantRequest {
  tenantId: number | null
}

export interface CaptchaResponse {
  /** 后端验证码开关，关闭时前端不渲染验证码输入 */
  captchaEnabled: string
  uuid: string
  /** base64 PNG，已带 data:image/png;base64, 前缀；明文答案只存服务端 */
  img: string | null
}

export interface LoginResponse {
  token: string
  expiresIn: number
}

/** 租户自助注册（登录页的注册表单）：提交即开通租户 + 管理员账号 + 菜单 */
export interface RegisterRequest {
  /** 租户编码（lc_tenant.tenant_code）：小写字母开头，可含数字与短横线 */
  tenantCode: string
  /** 租户名称（lc_tenant.tenant_name） */
  tenantName: string
  /** 管理员账号（sys_user.username）：注册要求全局唯一 */
  adminUsername: string
  /** 管理员密码：8-32 位且同时含字母与数字 */
  password: string
  captcha: string
  captchaUuid: string
}

/** 注册成功后端直接回登录令牌，前端无需再走一次登录 */
export interface RegisterResponse extends LoginResponse {
  tenantId: number
  tenantCode: string
  menuCount: number
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface CurrentUser {
  id?: number
  username: string
  nickname: string
  deptId?: number
  deptName?: string
  /** 当前会话租户 ID / 编码 / 名称（平台管理员切换后为目标租户） */
  tenantId?: number
  tenantCode?: string
  tenantName?: string
  /** 登录账号所属租户 ID；与 tenantId 不等即处于跨租户查看状态 */
  homeTenantId?: number
  roles: string[]
  permissions: string[]
}

export interface RouteItem {
  id?: number
  parentId?: number
  name: string
  routeName?: string
  routePath?: string
  path?: string
  component?: string
  icon?: string
  permission?: string
  children?: RouteItem[]
}

export interface RoutesResponse {
  routes: RouteItem[]
}

export function createAuthApi(client: HttpClient) {
  return {
    captcha: () => client.get<CaptchaResponse>('/auth/captcha'),
    login: (body: LoginRequest) => client.post<LoginResponse>('/auth/login', body),
    register: (body: RegisterRequest) => client.post<RegisterResponse>('/auth/register', body),
    switchTenant: (body: SwitchTenantRequest) => client.post<LoginResponse>('/auth/switch-tenant', body),
    me: () => client.get<CurrentUser>('/auth/me'),
    routes: () => client.get<RoutesResponse>('/auth/routes'),
    changePassword: (body: ChangePasswordRequest) => client.put<void>('/auth/password', body),
    logout: () => client.post<void>('/auth/logout'),
  }
}
