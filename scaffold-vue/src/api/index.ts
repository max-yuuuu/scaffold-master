import { httpClient } from './http.ts'
import { createAuthApi } from './auth.ts'
import { createSystemApi } from './system.ts'
// scaffold:platform-only:start 平台接口客户端只服务宿主页面
import { createPlatformApi } from './platform.ts'
// scaffold:platform-only:end
import { createPortalApi } from './portal.ts'

export * from './http.ts'
export * from './auth.ts'
export * from './session.ts'
export * from './system.ts'
// scaffold:platform-only:start 同上
export * from './platform.ts'
// scaffold:platform-only:end
export * from './portal.ts'

export const authApi = createAuthApi(httpClient)
export const systemApi = createSystemApi(httpClient)
// scaffold:platform-only:start 同上
export const platformApi = createPlatformApi(httpClient)
// scaffold:platform-only:end
export const portalApi = createPortalApi(httpClient)
