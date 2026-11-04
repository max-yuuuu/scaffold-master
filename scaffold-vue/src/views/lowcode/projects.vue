<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Descriptions, DescriptionsItem, Drawer, Field, StatusTag, Switch, Tag, Textarea, TextInput, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { platformApi, type InitializeResult, type ProjectRow, type RepoCheck, type WorkspaceStatus } from '@/api'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { maxLength, pattern, required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'

/* ============================================================
   项目管理。项目 = 租户名下的一个 Git 仓库，模型与页面都挂在项目下。
   平台发布只写「发布分支」，主干分支由人在平台内发起合并请求改动（方案 §5.1 / §5.3）。
   ============================================================ */

const list = useListPage<ProjectRow, { keyword: string; status: string }>({
  query: { keyword: '', status: '' },
  fetch: params => platformApi.projects.list(params) as Promise<any>,
  exporter: params => platformApi.projects.export(params),
  exportName: '项目列表.csv',
})

const columns: Column[] = [
  { key: 'projectCode', title: '项目编码', minWidth: 140 },
  { key: 'projectName', title: '项目名称', minWidth: 150 },
  { key: 'repoUrl', title: '仓库地址', minWidth: 240 },
  { key: 'branches', title: '分支', minWidth: 190 },
  { key: 'syncStatusText', title: '同步状态', width: 110 },
  { key: 'lastPublishTime', title: '最后发布', width: 150 },
  { key: 'status', title: '状态', width: 96 },
  { key: 'createTime', title: '创建时间', width: 150 },
  { key: 'actions', title: '操作', width: 112, className: 'col-actions' },
]

interface ProjectForm {
  id?: number
  projectCode: string
  projectName: string
  projectDesc: string
  repoUrl: string
  mainBranch: string
  genBranch: string
  status: number
  remark: string
}
const emptyForm = (): ProjectForm => ({
  projectCode: '', projectName: '', projectDesc: '', repoUrl: '',
  mainBranch: 'main', genBranch: 'platform-generated', status: 1, remark: '',
})
const dialog = ref(false)
const saving = ref(false)
const form = reactive<ProjectForm>(emptyForm())
const errors = ref<Partial<Record<keyof ProjectForm, string>>>({})
const editing = () => !!form.id

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: ProjectRow) {
  Object.assign(form, {
    id: row.id,
    projectCode: row.projectCode,
    projectName: row.projectName,
    projectDesc: row.projectDesc ?? '',
    repoUrl: row.repoUrl,
    mainBranch: row.mainBranch,
    genBranch: row.genBranch,
    status: row.status,
    remark: row.remark ?? '',
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    projectName: [required('请输入项目名称'), maxLength(64, '长度不能超过 64')],
    projectCode: editing() ? [] : [required('请输入项目编码'), pattern(/^[a-z][a-z0-9-]{1,31}$/, '小写字母开头，可含数字与短横线，长度 2-32')],
    repoUrl: [required('请输入仓库地址'), pattern(/^(https:\/\/|git@)/, '仓库地址要以 https:// 或 git@ 开头')],
    mainBranch: [required('请输入主干分支'), pattern(/^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$/, '字母数字开头，不能含空格')],
    genBranch: [required('请输入发布分支'), pattern(/^[A-Za-z0-9][A-Za-z0-9._/-]{0,63}$/, '字母数字开头，不能含空格')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = {
    projectName: form.projectName.trim(),
    projectDesc: form.projectDesc.trim() || undefined,
    repoUrl: form.repoUrl.trim(),
    mainBranch: form.mainBranch.trim(),
    genBranch: form.genBranch.trim(),
    status: form.status,
    remark: form.remark.trim() || undefined,
  }
  try {
    if (form.id) {
      await platformApi.projects.update(form.id, payload)
      pushToast('项目已更新')
    } else {
      await platformApi.projects.create({ ...payload, projectCode: form.projectCode.trim() })
      pushToast('项目已创建')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '项目保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: ProjectRow) {
  const next = row.status === 1 ? 0 : 1
  const ok = await confirm({
    title: next === 0 ? '停用项目' : '启用项目',
    message: next === 0
      ? `停用后「${row.projectName}」不再允许发布，已有仓库内容不受影响。确认停用吗？`
      : `确认启用项目「${row.projectName}」吗？`,
    confirmText: next === 0 ? '停用' : '启用',
    danger: next === 0,
  })
  if (!ok) return
  try {
    await platformApi.projects.updateStatus(row.id, next)
    pushToast(next === 0 ? '项目已停用' : '项目已启用')
    await list.load()
  } catch (error) {
    toastError(error, '项目状态更新失败')
  }
}

/* ============================================================
   仓库与工作区（PR 4）。项目能不能开始建模，取决于固定代码有没有推到发布分支：
   检查只读远端引用，初始化才会写工作区并推送；重建在有未推送提交时会被后端拒绝。

   动作按生命周期状态给，不是全铺：就绪之后「初始化固定代码」已无意义（平台不覆盖 A 档），
   工作区还没建时「清空工作区」也只是让人点了拿一句报错，所以都不显示。
   ============================================================ */
const repo = ref<ProjectRow | null>(null)
const checking = ref(false)
const initializing = ref(false)
const resetting = ref(false)
const check = ref<RepoCheck | null>(null)
const workspace = ref<WorkspaceStatus | null>(null)

async function loadWorkspace() {
  if (!repo.value) return
  try {
    workspace.value = await platformApi.projects.workspace(repo.value.id) as WorkspaceStatus
  } catch (error) {
    workspace.value = null
    toastError(error, '工作区状态读取失败')
  }
}

async function openRepo(row: ProjectRow) {
  repo.value = row
  check.value = null
  workspace.value = null
  await loadWorkspace()
}

async function runCheck() {
  if (!repo.value) return
  checking.value = true
  try {
    check.value = await platformApi.projects.checkRepo(repo.value.id) as RepoCheck
    await loadWorkspace()
  } catch (error) {
    toastError(error, '仓库检查失败')
  } finally {
    checking.value = false
  }
}

async function runInitialize() {
  if (!repo.value) return
  const ok = await confirm({
    title: '初始化固定代码',
    message: `平台会按裁剪清单把底座固定代码提交到发布分支「${repo.value.genBranch}」，主干分支不动。`
      + '仓库凭据走服务器 SSH 密钥，需要目标主机指纹已登记。确认开始吗？',
    confirmText: '开始初始化',
  })
  if (!ok) return
  initializing.value = true
  try {
    const result = await platformApi.projects.initialize(repo.value.id) as InitializeResult
    pushToast(`固定代码已推送：${result.files} 个文件`)
    await list.load()
    const fresh = list.rows.value.find(item => item.id === repo.value?.id)
    if (fresh) repo.value = fresh
    await loadWorkspace()
  } catch (error) {
    toastError(error, '初始化失败')
    await list.load()
    await loadWorkspace()
  } finally {
    initializing.value = false
  }
}

async function runReset() {
  if (!repo.value) return
  const ok = await confirm({
    title: '清空工作区',
    message: '只删平台本机的工作区副本，仓库里的内容不受影响。'
      + '若本地还有未提交的改动或未推送的提交，后端会拒绝执行，请先自行搬走。确认清空吗？',
    confirmText: '清空',
    danger: true,
  })
  if (!ok) return
  resetting.value = true
  try {
    await platformApi.projects.resetWorkspace(repo.value.id)
    pushToast('工作区已清空，可重新初始化')
    await list.load()
    await loadWorkspace()
  } catch (error) {
    toastError(error, '清空工作区失败')
  } finally {
    resetting.value = false
  }
}

const syncState = computed(() => repo.value?.syncStatus ?? 0)

/** 未初始化与上次不可达都可以推固定代码；就绪与初始化中不行 */
const canInitialize = computed(() => syncState.value === 0 || syncState.value === 5)

/** 只有工作区真的存在才给「清空工作区」入口 */
const canResetWorkspace = computed(() => Boolean(workspace.value?.exists))

/** 顶部一句话说清当前能做什么，比让人对着按钮猜 */
const statusHint = computed(() => {
  if (syncState.value === 1) return '正在读写远端仓库，这一步完成前不要再触发其他仓库动作'
  if (syncState.value === 2) return '固定代码已在发布分支上；平台不再覆盖 A 档文件，要重来先清空工作区'
  if (syncState.value === 3) return '平台工作区有未提交改动，先处理掉再推，平台不会覆盖它们'
  if (syncState.value === 4) return '远端与本地不一致（分支保护或历史分叉），需在仓库侧处理后重试'
  if (syncState.value === 5) return '上次没能连上仓库，按下面的原因处理后重试'
  return '平台只写发布分支，主干由您在仓库侧掌控；绑定后可先检查连通性'
})

function syncTone(status: number) {
  if (status === 2) return 'ok'
  if (status === 1) return 'info'
  if (status === 0) return 'neutral'
  return 'danger'
}

function shortSha(sha?: string | null) {
  return sha ? sha.slice(0, 10) : '—'
}

async function remove(row: ProjectRow) {
  const ok = await confirm({
    title: '删除项目',
    message: `删除后平台不再管理「${row.projectName}」，其在 Git 仓库里已推送的内容不会被改动。确认删除吗？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await platformApi.projects.remove(row.id)
    pushToast('项目已删除')
    await list.load()
  } catch (error) {
    toastError(error, '项目删除失败')
  }
}
</script>

<template>
  <ListPage
    title="项目管理"
    desc="一个项目对应一个 Git 仓库。平台发布只写「发布分支」，主干分支由人工在合并请求里确认后才改动，仓库凭据走服务器 SSH 密钥、不入库。"
    table-title="项目列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="lowcode:project:add" variant="primary" icon="plus" @click="openCreate">新增项目</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="项目编码 / 名称" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <DictSelect v-model="list.query.status" type="sys_normal_disable" all-label="全部状态" />
      </Field>
    </template>

    <template #cell-projectCode="{ row }"><span class="code-chip">{{ row.projectCode }}</span></template>
    <template #cell-projectName="{ row }">
      <span class="cell-title">{{ row.projectName }}</span>
      <span v-if="row.projectDesc" class="muted project-desc"> · {{ row.projectDesc }}</span>
    </template>
    <template #cell-repoUrl="{ row }"><span class="cell-sub repo-url" :title="row.repoUrl">{{ row.repoUrl }}</span></template>
    <template #cell-branches="{ row }">
      <span class="branch-pair">
        <Tag tone="neutral">{{ row.mainBranch }}</Tag>
        <span class="branch-arrow" aria-hidden="true">←</span>
        <Tag tone="info">{{ row.genBranch }}</Tag>
      </span>
    </template>
    <template #cell-syncStatusText="{ row }">
      <span :title="row.syncMessage || undefined" class="sync-cell">
        <Tag :tone="syncTone(row.syncStatus)">{{ row.syncStatusText }}</Tag>
        <Icon v-if="row.syncMessage" name="alert" :size="14" class="sync-alert" />
      </span>
    </template>
    <template #cell-lastPublishTime="{ row }"><span class="tnum soft">{{ row.lastPublishTime || '—' }}</span></template>
    <template #cell-status="{ row }"><StatusTag :status="row.status" /></template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="lowcode:project:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="lowcode:project:sync" icon-only icon="code" label="仓库与工作区" @click="openRepo(row)" />
        <PermissionButton
          perm="lowcode:project:edit"
          icon-only
          :icon="row.status === 1 ? 'ban' : 'check'"
          :label="row.status === 1 ? '停用' : '启用'"
          :danger="row.status === 1"
          @click="toggleStatus(row)"
        />
        <PermissionButton perm="lowcode:project:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <Drawer
      :open="Boolean(repo)"
      :title="repo?.projectName"
      :sub="repo ? `${repo.projectCode} · ${repo.syncStatusText}` : ''"
      :width="760"
      @close="repo = null"
    >
      <div class="drawer-toolbar">
        <span class="repo-state">
          <Tag :tone="syncTone(syncState)">{{ repo?.syncStatusText }}</Tag>
          <span class="soft repo-hint">{{ statusHint }}</span>
        </span>
        <span class="drawer-toolbar-actions">
          <PermissionButton
            perm="lowcode:project:sync"
            size="sm"
            icon="refresh"
            :loading="checking"
            :disabled="initializing"
            @click="runCheck"
          >检查连通性</PermissionButton>
          <PermissionButton
            v-if="canInitialize"
            perm="lowcode:project:sync"
            variant="primary"
            size="sm"
            icon="upload"
            :loading="initializing"
            :disabled="checking"
            @click="runInitialize"
          >初始化固定代码</PermissionButton>
          <PermissionButton
            v-if="canResetWorkspace"
            perm="lowcode:project:sync"
            size="sm"
            icon="trash"
            :loading="resetting"
            danger
            @click="runReset"
          >清空工作区</PermissionButton>
        </span>
      </div>
      <p v-if="repo?.syncMessage" class="repo-action" :class="{ 'is-error': syncState === 4 || syncState === 5 }">
        <Icon :name="syncState === 4 || syncState === 5 ? 'alert' : 'info'" :size="14" />
        <span>{{ repo.syncMessage }}</span>
      </p>

      <Descriptions :column="2">
        <DescriptionsItem label="仓库地址" :span="2">{{ repo?.repoUrl }}</DescriptionsItem>
        <DescriptionsItem label="主干分支">{{ repo?.mainBranch }}</DescriptionsItem>
        <DescriptionsItem label="发布分支">{{ repo?.genBranch }}</DescriptionsItem>
        <DescriptionsItem label="初始化时间">{{ repo?.initTime || '—' }}</DescriptionsItem>
        <DescriptionsItem label="底座提交">{{ shortSha(repo?.baseScaffoldSha) }}</DescriptionsItem>
        <DescriptionsItem label="最后推送提交">{{ shortSha(repo?.lastCommitSha) }}</DescriptionsItem>
      </Descriptions>

      <Descriptions :column="2">
        <DescriptionsItem label="工作区路径" :span="2">{{ workspace?.path || '—' }}</DescriptionsItem>
        <DescriptionsItem label="是否已建">{{ workspace?.exists ? '已建立' : '未建立' }}</DescriptionsItem>
        <DescriptionsItem label="未提交改动">{{ workspace?.exists ? (workspace.dirty ? '有' : '无') : '—' }}</DescriptionsItem>
        <DescriptionsItem label="本地提交">{{ shortSha(workspace?.localSha) }}</DescriptionsItem>
        <DescriptionsItem label="远端提交">{{ shortSha(workspace?.remoteSha) }}</DescriptionsItem>
        <DescriptionsItem label="未推送提交">{{ workspace?.exists ? (workspace.unpushed ? '有，禁止清空工作区' : '无') : '—' }}</DescriptionsItem>
      </Descriptions>
      <p v-if="workspace?.message" class="muted drawer-note">{{ workspace.message }}</p>

      <p v-if="!check" class="hint">还没检查过：检查连通性只读远端引用，不改动项目状态，也不推任何东西。</p>
      <Descriptions v-else :column="2">
        <DescriptionsItem label="能否访问">{{ check.reachable ? '可访问' : '不可访问' }}</DescriptionsItem>
        <DescriptionsItem label="仓库是否为空">{{ check.empty === undefined ? '—' : (check.empty ? '空仓库' : '已有内容') }}</DescriptionsItem>
        <DescriptionsItem label="远端分支" :span="2">{{ check.branches?.length ? check.branches.join('、') : '—' }}</DescriptionsItem>
        <DescriptionsItem label="下一步">{{ check.message || check.action || '—' }}</DescriptionsItem>
      </Descriptions>
    </Drawer>

    <FormDialog :open="dialog" :title="editing() ? '编辑项目' : '新增项目'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="项目编码" required hint="租户内唯一，决定生成代码的包名与目录名，创建后不可修改" :error="errors.projectCode">
        <TextInput v-model="form.projectCode" :disabled="editing()" placeholder="如：order-service" :invalid="!!errors.projectCode" />
      </Field>
      <Field label="项目名称" required :error="errors.projectName">
        <TextInput v-model="form.projectName" placeholder="如：订单中台" :invalid="!!errors.projectName" />
      </Field>
      <Field label="仓库地址" required hint="仅支持 https:// 或 git@host:path，不要内嵌账号或口令" :error="errors.repoUrl">
        <TextInput v-model="form.repoUrl" icon="code" placeholder="git@github.com:acme/order-service.git" :invalid="!!errors.repoUrl" />
      </Field>
      <Field label="主干分支" required hint="人工合并的目标，平台永不直接写">
        <TextInput v-model="form.mainBranch" placeholder="main" />
      </Field>
      <Field label="发布分支" required hint="平台发布只推这条分支">
        <TextInput v-model="form.genBranch" placeholder="platform-generated" />
      </Field>
      <Field label="项目状态" group>
        <Switch :model-value="form.status === 1" :label="form.status === 1 ? '正常' : '停用'" @update:model-value="form.status = $event ? 1 : 0" />
      </Field>
      <Field label="项目描述" span>
        <Textarea v-model="form.projectDesc" :rows="2" placeholder="这个项目做什么" :maxlength="512" />
      </Field>
      <Field label="备注" span>
        <Textarea v-model="form.remark" :rows="2" placeholder="交付范围、仓库归属等" :maxlength="500" />
      </Field>
    </FormDialog>
  </ListPage>
</template>

<style scoped>
.sync-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.sync-alert {
  color: var(--warn);
}
/* 三块读数之间留口气：Descriptions 的根是 .desc（.kv-grid 在它里面），单行提示是 .hint */
.desc + .desc,
.desc + .hint,
.drawer-note + .desc,
.drawer-note + .hint {
  margin-top: 16px;
}
.repo-state {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.repo-hint {
  font-size: 12px;
}
.drawer-note {
  margin: 8px 0 0;
  font-size: 12px;
}
.repo-action {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: -4px 0 14px;
  padding: 9px 11px;
  border-radius: var(--r-sm);
  border: 1px solid var(--line-strong);
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.5;
  word-break: break-word;
}
.repo-action.is-error {
  border-color: transparent;
  background: var(--red-soft);
  color: var(--red-text);
}
.repo-action svg {
  flex: none;
  margin-top: 2px;
}
.project-desc {
  font-size: 12px;
}
.repo-url {
  /* 长仓库地址在单元格内截断，完整值看 title；字号与颜色沿用 .cell-sub */
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--font-mono);
}
.branch-pair {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.branch-arrow {
  color: var(--ink-soft);
  font-size: 12px;
}
</style>
