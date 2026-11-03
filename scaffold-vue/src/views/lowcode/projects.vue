<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Field, StatusTag, Switch, Tag, Textarea, TextInput, type Column } from '@/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@/components/business'
import { platformApi, type ProjectRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { maxLength, pattern, required, validate } from '@/utils/validate'

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
    <template #cell-syncStatusText="{ row }"><span class="muted">{{ row.syncStatusText }}</span></template>
    <template #cell-lastPublishTime="{ row }"><span class="tnum soft">{{ row.lastPublishTime || '—' }}</span></template>
    <template #cell-status="{ row }"><StatusTag :status="row.status" /></template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="lowcode:project:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
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
