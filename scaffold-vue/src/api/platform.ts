import { crudPage, type PageResult } from './system'
import type { QueryParams } from './http'
import type { HttpClient } from './http'

/** 租户行（lc_tenant）：userCount 为该租户下的账号数 */
export interface TenantRow {
  id: number
  tenantCode: string
  tenantName: string
  contactName: string
  contactPhone: string
  maxProjects: number
  expireDate: string | null
  status: number
  userCount: number
  createTime: string
  remark: string | null
}

/** 租户新增与编辑请求体（tenantCode 创建后不可改） */
export interface TenantWriteRequest {
  tenantCode?: string
  tenantName?: string
  contactName?: string
  contactPhone?: string
  maxProjects?: number
  /** 到期日期 yyyy-MM-dd，空字符串表示不限 */
  expireDate?: string
  status?: number
  remark?: string
}

/** 租户参数行（lc_tenant_config），只反映当前会话租户 */
export interface TenantConfigRow {
  id: number
  configKey: string
  configValue: string
  configName: string | null
  /** 后端已归一化为 boolean */
  builtin: boolean
  remark: string | null
  updateTime: string
}

export interface TenantConfigWriteRequest {
  /** 新增时为空，修改时必填 */
  id?: number
  configKey: string
  configValue?: string
  remark?: string
}

/** 项目行（lc_project）。同步状态是项目生命周期：0 未初始化 1 初始化中 2 就绪 3 工作区脏 4 与远端分叉 5 仓库不可达 */
export interface ProjectRow {
  id: number
  projectCode: string
  projectName: string
  projectDesc: string | null
  repoUrl: string
  mainBranch: string
  genBranch: string
  baseScaffoldSha: string | null
  lastCommitSha: string | null
  syncStatus: number
  syncStatusText: string
  initTime: string | null
  lastPublishTime: string | null
  status: number
  createTime: string
  remark: string | null
}

/** 项目新增与编辑请求体（projectCode 创建后不可改） */
export interface ProjectWriteRequest {
  projectCode?: string
  projectName?: string
  projectDesc?: string
  repoUrl?: string
  mainBranch?: string
  genBranch?: string
  status?: number
  remark?: string
}

/** 项目下拉用的精简项 */
export interface ProjectOption {
  id: number
  projectCode: string
  projectName: string
  mainBranch: string
  genBranch: string
  syncStatus: number
  syncStatusText: string
}

/** 补齐租户资产的返回：各项新增条数 */
export interface AssetSyncResult {
  tenantId: number
  menuAdded: number
  dictTypeAdded: number
  dictDataAdded: number
  configAdded: number
}

export function createPlatformApi(client: HttpClient) {
  return {
    tenants: {
      ...crudPage<TenantRow, TenantWriteRequest>(client, '/platform/tenants'),
      updateStatus: (id: number, status: number) => client.put<void>(`/platform/tenants/${id}/status`, { status }),
      export: (query?: QueryParams) => client.download('/platform/tenants/export', query),
    },
    projects: {
      ...crudPage<ProjectRow, ProjectWriteRequest>(client, '/lowcode/projects'),
      options: () => client.get<ProjectOption[]>('/lowcode/projects/options'),
      updateStatus: (id: number, status: number) => client.put<void>(`/lowcode/projects/${id}/status`, { status }),
      export: (query?: QueryParams) => client.download('/lowcode/projects/export', query),
    },
    tenantAssets: {
      /** 平台加了租户可见菜单后，已有租户靠这一步补齐（幂等，可重复执行） */
      sync: (tenantId: number) => client.post<AssetSyncResult>(`/platform/tenants/${tenantId}/assets/sync`),
    },
    tenantConfigs: {
      list: (query?: QueryParams) => client.get<PageResult<TenantConfigRow>>('/platform/tenant-configs', query),
      export: (query?: QueryParams) => client.download('/platform/tenant-configs/export', query),
      save: (body: TenantConfigWriteRequest) => client.put<Record<string, unknown>>('/platform/tenant-configs', body),
      remove: (id: number) => client.delete<void>(`/platform/tenant-configs/${id}`),
    },
  }
}

export type PlatformApi = ReturnType<typeof createPlatformApi>
