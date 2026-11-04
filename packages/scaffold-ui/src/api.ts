import { scaffoldUi } from './providers.ts'
import type { Dept, LoginHeatmap, PageResult, PermissionGraph, QueryParams, UserRow } from './types'

/* ============================================================
   物料自带的数据入口。
   路径就是底座后端的固定接口（A 档代码，平台与生成项目一致），
   宿主只负责把 http 能力注进来，因此这些调用不依赖宿主的 api 层。
   ============================================================ */

export const materialApi = {
  /** 部门树：/system/depts/tree，返回带 children 的层级结构 */
  deptTree: (query?: QueryParams) => scaffoldUi().http.get<Dept[]>('/system/depts/tree', query),
  /** 某字典类型的全部数据行：/system/dict/data/{type} */
  dictRows: (type: string) => scaffoldUi().http.get<Record<string, unknown>[]>(`/system/dict/data/${type}`),
  /** 修改字典数据行：PUT /system/dict/data/{id} */
  updateDictRow: (id: number, body: Record<string, unknown>) => scaffoldUi().http.put<void>(`/system/dict/data/${id}`, body),
  /** 用户分页：/system/users，按 query 过滤（postId、status、keyword 等） */
  usersPage: (query?: QueryParams) => scaffoldUi().http.get<PageResult<UserRow>>('/system/users', query),
  /** 权限关系图：/system/permissions/graph */
  permissionGraph: () => scaffoldUi().http.get<PermissionGraph>('/system/permissions/graph'),
  /** 登录热力：/monitor/login-logs/heatmap，days 统计天数 */
  loginHeatmap: (days: 7 | 30 = 7) => scaffoldUi().http.get<LoginHeatmap>('/monitor/login-logs/heatmap', { days }),
  /** 参数配置分页：/system/configs */
  configsPage: (query?: QueryParams) => scaffoldUi().http.get<PageResult<Record<string, unknown>>>('/system/configs', query),
}
