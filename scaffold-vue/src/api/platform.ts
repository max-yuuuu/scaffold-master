import { crudPage, type PageResult } from './system.ts'
import type { QueryParams } from './http.ts'
import type { HttpClient } from './http.ts'

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
  /** 项目负责人（sys_user.id）：合并请求只有他或平台超管能确认合并；没设时只有超管能合 */
  ownerUserId: number | null
  ownerName: string | null
  baseScaffoldSha: string | null
  lastCommitSha: string | null
  syncStatus: number
  syncStatusText: string
  /** 最近一次仓库动作的失败原因或进度说明，界面直接显示，不让人去翻后端日志 */
  syncMessage: string | null
  /** 工作区相对路径（{租户 ID}/{项目编码}），未初始化时为空 */
  workspacePath: string | null
  initTime: string | null
  lastPublishTime: string | null
  status: number
  createTime: string
  remark: string | null
}

/** 仓库连通性检查结果（POST /lowcode/projects/{id}/repo/check） */
export interface RepoCheck {
  repoUrl: string
  reachable: boolean
  /** 空仓库：初始化时直接在发布分支上创建第一次提交 */
  empty?: boolean
  branches?: string[]
  mainBranch?: string
  genBranch?: string
  mainBranchExists?: boolean
  genBranchExists?: boolean
  /** 下一步该做什么，后端给中文说明 */
  action?: string
  message?: string
}

/** 工作区读数（GET /lowcode/projects/{id}/workspace） */
export interface WorkspaceStatus {
  path: string
  exists: boolean
  genBranch: string
  syncStatus: number
  syncStatusText: string
  dirty?: boolean
  localSha?: string | null
  remoteSha?: string | null
  unpushed?: boolean
  /** 交付时的底座提交号；为空说明当时读不到，该项目做不了三方底座升级 */
  baseScaffoldSha?: string | null
  /** 项目仓库里的底座标记 tag 名（yuanda/platform-base/{底座提交号}） */
  baseTag?: string | null
  /** 标记 tag 是否已在远端：true 有、false 缺、null 未核对（工作区不在时不去问远端） */
  baseTagExists?: boolean | null
  message?: string
}

/** 初始化固定代码的结果 */
export interface InitializeResult {
  projectId: number
  path: string
  syncStatus: number
  syncStatusText: string
  /** 本次写入交付清单的文件数 */
  files: number
  commitSha: string | null
  /** 本次交付对应的底座提交号与打好的底座标记 tag；读不到底座提交号时两者都为空 */
  baseScaffoldSha?: string | null
  baseTag?: string | null
  message: string
}

/** 项目新增与编辑请求体（projectCode 创建后不可改） */
export interface ProjectWriteRequest {
  projectCode?: string
  projectName?: string
  projectDesc?: string
  repoUrl?: string
  mainBranch?: string
  genBranch?: string
  /** 负责人可以留空（还没定由谁审），但不能指一个不存在或已停用的账号，后端会拒 */
  ownerUserId?: number | null
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

/** 页面列表行：不带 design_json，一屏几十行各带一份设计会把内存吃光，而列表上没有一处要读它 */
export interface PageRow {
  id: number
  projectId: number
  projectName: string | null
  modelId: number
  modelCode: string
  modelName: string
  pageCode: string
  pageName: string
  pageDesc: string | null
  pageType: string
  routePath: string | null
  menuIcon: string | null
  menuSort: number | null
  refFieldCount: number
  status: number
  createTime: string
  updateTime: string
}

export interface PageDetail extends PageRow {
  design: Record<string, unknown>
  refFields: string[]
  /** 已成功发布的版本号：库里 NOT NULL DEFAULT 0，0 就是没发过（tag 末尾那个数就是它） */
  currentVersion: number
  /** 最近一次发布结果：0 未发布 1 已发布 2 内容与远端一致 3 上次失败（lc_page.publish_status） */
  publishStatus: number
}

/** 发布请求（PagePublishRequest）：版本号由后端按 current_version 算，不让前端填 */
export interface PagePublishRequest {
  /** 引用体检有命中时人显式放行；默认 false，命中会阻断并回一份清单 */
  force: boolean
  changeDesc: string
}

/** 发布结果里设计器要读的那几格（后端还带 files / warnings / branch，弹窗按需要再看） */
export interface PagePublishResult {
  pageId: number
  version: number | null
  status: number
  tagName: string | null
  message: string
}

/** 版本历史一行：lc_page_version 配上台账里那次成功动作的 tag 与类型，回滚就按这一列点 */
export interface PageVersionRow {
  id: number
  version: number
  commitSha: string
  tagName: string | null
  /** CREATE 首次发布 / UPDATE 再次发布 / ROLLBACK 回滚产出的那一版 */
  publishType: string | null
  changeDesc: string | null
  createBy: string | null
  createTime: string | null
  /** 产物份数：SQL 里 JSON_LENGTH 现算，库里不存计数列 */
  fileCount: number | null
}

/** 单版的产物一行。档位由路径反推，C 档不参与回滚 —— 平台不生成它，也就无权还原人写在里面的代码 */
export interface PageVersionFileRow {
  path: string
  sha256: string
  tier: 'GENERATED' | 'EXTENSION'
  rollbackable: boolean
}

export interface PageVersionDetail {
  pageId: number
  version: number
  commitSha: string
  tagName: string | null
  publishType: string | null
  changeDesc: string | null
  files: PageVersionFileRow[]
}

/** 回滚请求：version 必填（退回哪一版），files 为空就是整页回滚，给了就是只还原这几个 B 档文件 */
export interface PageRollbackRequest {
  version: number
  files?: string[]
}

export interface PageRollbackResult {
  pageId: number
  pageCode: string
  /** 回滚产出的是「新的一版」，不是把 current_version 改回旧号 */
  version: number
  rolledBackTo: number
  publishType: string
  status: number
  commitSha: string
  tagName: string | null
  branch: string
  files: { path: string, tier: string, action: string, sha256: string }[]
  message: string
}

/** 页面新增与编辑请求（lc_page）：design 送对象而不是字符串，后端 PageWriteRequest 收 JsonNode */
export interface PageWriteRequest {
  projectId?: number
  modelCode?: string
  pageCode?: string
  pageName: string
  pageDesc?: string | null
  routePath?: string | null
  menuIcon?: string | null
  menuSort?: number | null
  status?: number
  design: Record<string, unknown>
}

/**
 * 合并请求的一行（lc_merge_request）。
 *
 * 文案（statusText / checkStatusText）由后端给：状态词的口径只在 `MergeRequestState.text()` 一处，
 * 前端拿数字自己拼词，两处就会开始各说各话。
 */
export interface MergeRequestRow {
  id: number
  projectId: number
  title: string
  description: string | null
  sourceBranch: string
  targetBranch: string
  sourceSha: string
  targetSha: string
  mergeBaseSha: string | null
  /** 0 待审 1 陈旧 2 已合并 3 已关闭 */
  status: number
  statusText: string
  /** 0 未预检 1 可自动合并 2 有冲突 */
  checkStatus: number
  checkStatusText: string
  checkMessage: string | null
  checkTime: string | null
  /** 确认合并的人与时间：只有项目负责人或平台超管写得进来 */
  mergedBy: string | null
  mergedTime: string | null
  projectName: string | null
  ownerUserId: number | null
  /** 这两个数是按文件表现算的，库里不存计数 */
  fileCount: number
  conflictCount: number
  createBy: string | null
  createTime: string
  updateTime: string
}

/** 变更清单里的一行（lc_merge_request_file）：diff 全文不入库，要看到的具体改动由后端按需算 */
export interface MergeRequestFileRow {
  id: number
  requestId: number
  path: string
  oldPath: string | null
  changeType: 'ADD' | 'MODIFY' | 'DELETE' | 'RENAME'
  isConflict: number
  additions: number
  deletions: number
}

export interface MergeRequestDetail extends MergeRequestRow {
  files: MergeRequestFileRow[]
}

/** 重算入参：只要项目 ID，清单与结论全部从仓库现算，前端不带任何期望值 */
export interface MergeRequestRefreshRequest {
  projectId: number
  /** 来源分支，默认是项目的发布分支；底座升级推的是 platform-upgrade/*，走同一套审与合 */
  sourceBranch?: string
}

/** 最近一次底座升级的结论（lc_base_upgrade 一条） */
export interface BaseUpgradeRecord {
  id: number
  projectId: number
  baseScaffoldSha: string
  targetScaffoldSha: string
  /** 没产生升级分支时为 null：算下来一格可自动合的都没有 */
  upgradeBranch: string | null
  /** 0 待合并 / 1 已合入主干 / 2 主干又前进（陈旧）/ 3 没产生升级分支 */
  status: number
  message: string | null
  createBy: string | null
  createTime: string | null
}

/** 底座落后读数。behindCommits 为 null 是「数不出来」，界面要显示「未知」而不是 0 */
export interface ProjectBaseStatus {
  baseScaffoldSha: string | null
  scaffoldSha: string | null
  upToDate: boolean
  behindCommits: number | null
  latest: BaseUpgradeRecord | null
}

/** 发起升级的结果：三档计数 + 冲突与撤掉的清单 + 下一步去哪 */
export interface BaseUpgradeResult {
  upgradeId: number | null
  projectCode: string
  baseScaffoldSha: string
  targetScaffoldSha: string
  upgradeBranch: string | null
  commitSha: string | null
  clean: number
  conflict: number
  dropped: number
  unchanged: number
  binary: number
  conflictPaths: string[]
  droppedPaths: string[]
  status: number
  message: string
}

/** 补打底座标记 tag 的回执：打在哪一笔、那笔是怎么定出来的 —— 界面必须把后者说给人看 */
export interface BaseTagResult {
  tagName: string
  commitSha: string
  locatedBy: string
  message: string
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
      /** 只读远端引用：不改项目生命周期，失败也回 200，用 reachable 表达 */
      checkRepo: (id: number) => client.post<RepoCheck>(`/lowcode/projects/${id}/repo/check`),
      /** 首次初始化：写固定代码并提交推送发布分支，同步执行，可能要几十秒 */
      initialize: (id: number) => client.post<InitializeResult>(`/lowcode/projects/${id}/initialize`),
      workspace: (id: number) => client.get<WorkspaceStatus>(`/lowcode/projects/${id}/workspace`),
      /** 清空工作区：有未提交改动或未推送提交时后端会拒绝 */
      resetWorkspace: (id: number) => client.delete<{ path: string, cleared: boolean }>(`/lowcode/projects/${id}/workspace`),
      /** 底座落后读数：只读库里的账与本地底座 checkout，一步不碰远端 */
      baseStatus: (id: number) => client.get<ProjectBaseStatus>(`/lowcode/projects/${id}/base-status`),
      /** 发起底座升级（lowcode:project:upgrade）：推一条 platform-upgrade/* 分支，合不合由人在合并请求里定 */
      baseUpgrade: (id: number) => client.post<BaseUpgradeResult>(`/lowcode/projects/${id}/base-upgrade`),
      /**
       * 人工补打底座标记 tag（lowcode:project:sync）：存量项目远端没有这个 tag 时，底座升级做不了
       * （三方合并缺 base 侧），而 initialize 在已就绪的项目上会拒绝重来 —— 这一步就是补那个缺口。
       * commitSha 留空表示让平台按发布分支上的 .scaffold-project.json 认那一笔交付，认不出会拒绝。
       */
      stampBaseTag: (id: number, commitSha?: string) => client.post<BaseTagResult>(`/lowcode/projects/${id}/base-tag`,
        commitSha ? { commitSha } : {}),
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
    pages: {
      ...crudPage<PageRow, PageWriteRequest>(client, '/lowcode/pages'),
      /** 详情带整份 design：设计器打开页面吃的就是这一份，列表接口不给（一屏几十行各带一份设计） */
      detail: (id: number) => client.get<PageDetail>(`/lowcode/pages/${id}`),
      updateStatus: (id: number, status: number) => client.put<void>(`/lowcode/pages/${id}/status`, { status }),
      /** 发布是独立权限点（lowcode:page:publish）：能编辑设计不等于能往租户仓库推代码 */
      publish: (id: number, body: PagePublishRequest) => client.post<PagePublishResult>(`/lowcode/pages/${id}/publish`, body),
      /** 版本历史读的是库里的账，不碰仓库，所以权限沿用 lowcode:page:list */
      versions: (id: number, query?: QueryParams) => client.get<PageResult<PageVersionRow>>(`/lowcode/pages/${id}/versions`, query),
      /** 单版详情带产物清单：文件级回滚就是在这份清单里挑 B 档那几个 */
      version: (id: number, version: number) => client.get<PageVersionDetail>(`/lowcode/pages/${id}/versions/${version}`),
      /** 回滚（lowcode:page:rollback）：以一笔新提交把那一版的内容推回发布分支，不碰主干、不倒带历史 */
      rollback: (id: number, body: PageRollbackRequest) => client.post<PageRollbackResult>(`/lowcode/pages/${id}/rollback`, body),
    },
    mergeRequests: {
      /** 列表是一次读库：不重读远端，一屏几十条各打一次 ls-remote 会把「看一眼列表」变成网络风暴 */
      list: (query?: QueryParams) => client.get<PageResult<MergeRequestRow>>('/lowcode/merge-requests', query),
      /** 详情带变更清单（冲突的排在最前），并在打开时重读一次远端判陈旧 */
      detail: (id: number) => client.get<MergeRequestDetail>(`/lowcode/merge-requests/${id}`),
      /** 重算是独立权限点（lowcode:merge-request:sync）：它会真连远端仓库，不该挂在「看列表」那个码上 */
      refresh: (body: MergeRequestRefreshRequest) => client.post<MergeRequestDetail>('/lowcode/merge-requests/refresh', body),
      /** 单个文件的行内差异：抽屉里点开一行才拉一次，不一次把整份补丁搬回浏览器 */
      fileDiff: (id: number, path: string) => client.get<{ path: string, diff: string }>(`/lowcode/merge-requests/${id}/file-diff`, { path }),
      /** 确认合并（lowcode:merge-request:merge）：服务里还要再判一次「是不是项目负责人或平台超管」 */
      merge: (id: number) => client.post<MergeRequestDetail>(`/lowcode/merge-requests/${id}/merge`),
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
