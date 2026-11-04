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

/** 模型行（lc_model）。表名 / 类名 / 包名新建时按模型编码派生，fieldCount 是已配置字段数 */
export interface ModelRow {
  id: number
  projectId: number
  projectCode: string
  projectName: string
  modelCode: string
  modelName: string
  modelDesc: string | null
  tableName: string
  className: string
  moduleName: string
  isTree: number
  enableLogicDelete: number
  enableDataScope: number
  enableAuditField: number
  status: number
  fieldCount: number
  createTime: string
  updateTime: string
}

export interface ModelWriteRequest {
  projectId?: number
  modelCode?: string
  modelName?: string
  modelDesc?: string
  tableName?: string
  className?: string
  moduleName?: string
  isTree?: number
  enableLogicDelete?: number
  enableDataScope?: number
  enableAuditField?: number
  status?: number
}

/** 字段行（lc_field）。javaType / columnType 由后端按 fieldType 推导后落库，前端只读展示 */
export interface FieldRow {
  id: number
  modelId: number
  fieldCode: string
  fieldName: string
  fieldDesc: string | null
  fieldType: string
  javaType: string
  columnName: string
  columnType: string
  columnLength: number | null
  columnPrecision: number | null
  columnScale: number | null
  isPrimary: number
  isAutoIncrement: number
  isNullable: number
  isUnique: number
  defaultValue: string | null
  isList: number
  isQuery: number
  queryType: string
  isInsert: number
  isEdit: number
  isRequired: number
  formType: string
  formOptions: FormOption[]
  dictType: string | null
  refModelId: number | null
  refFieldId: number | null
  sortOrder: number
}

export interface FormOption {
  label: string
  value: string
}

export interface FieldWriteRequest {
  fieldCode?: string
  fieldName?: string
  fieldDesc?: string
  fieldType: string
  columnLength?: number
  columnPrecision?: number
  columnScale?: number
  isPrimary?: number
  isAutoIncrement?: number
  isNullable?: number
  isUnique?: number
  defaultValue?: string
  isList?: number
  isQuery?: number
  queryType?: string
  isInsert?: number
  isEdit?: number
  isRequired?: number
  formType?: string
  formOptions?: FormOption[]
  dictType?: string
  refModelId?: number
  refFieldId?: number
  sortOrder?: number
}

/** 类型映射表：字段类型 → Java 类型 / 列类型 / 默认控件，唯一事实源在后端 FieldType 枚举 */
export interface FieldTypeMeta {
  code: string
  label: string
  javaType: string
  columnType: string
  formType: string
  defaultLength: number | null
  defaultPrecision: number | null
  defaultScale: number | null
  requiresDict: boolean
  requiresReference: boolean
}

export interface ModelMeta {
  fieldTypes: FieldTypeMeta[]
  formTypes: Array<{ code: string, label: string }>
  queryTypes: Array<{ code: string, operator: string, label: string }>
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
    models: {
      ...crudPage<ModelRow, ModelWriteRequest>(client, '/lowcode/models'),
      meta: () => client.get<ModelMeta>('/lowcode/models/meta'),
      options: (projectId?: number) => client.get<Array<{ id: number, modelCode: string, modelName: string, tableName: string, className: string }>>('/lowcode/models/options', projectId ? { projectId } : undefined),
      updateStatus: (id: number, status: number) => client.put<void>(`/lowcode/models/${id}/status`, { status }),
      fields: (modelId: number) => client.get<FieldRow[]>(`/lowcode/models/${modelId}/fields`),
      fieldOptions: (modelId: number) => client.get<Array<{ id: number, fieldCode: string, fieldName: string, fieldType: string }>>(`/lowcode/models/${modelId}/field-options`),
      createField: (modelId: number, body: FieldWriteRequest) => client.post<{ id: number }>(`/lowcode/models/${modelId}/fields`, body),
      updateField: (fieldId: number, body: FieldWriteRequest) => client.put<void>(`/lowcode/fields/${fieldId}`, body),
      removeField: (fieldId: number) => client.delete<void>(`/lowcode/fields/${fieldId}`),
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
