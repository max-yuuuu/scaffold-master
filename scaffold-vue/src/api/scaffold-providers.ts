import type { QueryParams } from '@scaffold/ui/types'
import type { ScaffoldDict, ScaffoldHttp, ScaffoldPermission } from '@scaffold/ui'
import { UploadError } from '@scaffold/ui/composables/upload'
import { apiBaseUrl, ApiError, httpClient } from './http'
import { authSession } from './session'
import { systemApi } from './index'
import { hasPermission } from '@/composables/permission'
import { pathOf, sessionVersion } from '@/composables/auth'

/* ============================================================
   物料包注入点。
   @scaffold/ui 不 import 宿主模块，接口、权限、字典三项能力在这里交给它 ——
   平台自身与每个生成项目的 main.ts 都按这一份口径接线。
   ============================================================ */

export const scaffoldHttp: ScaffoldHttp = {
  baseUrl: apiBaseUrl,
  token: () => authSession.token(),
  get<T>(path: string, query?: QueryParams) {
    return httpClient.get<T>(path, query)
  },
  put<T>(path: string, body?: unknown) {
    return httpClient.put<T>(path, body)
  },
  /** 后端信封的业务消息与上传失败原因优先，其余异常给兜底文案 */
  errorMessage: (error, fallback) => (error instanceof ApiError || error instanceof UploadError ? error.message : fallback),
}

export const scaffoldPermission: ScaffoldPermission = {
  has: hasPermission,
  pathOf,
  sessionVersion,
}

export const scaffoldDict: ScaffoldDict = {
  /** 字典原始行：label / value / status / tone / def，由包内 useDict 统一转成下拉项 */
  load: (type) => systemApi.dictData.options(type),
}
