<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { DateInput, Field, StatusTag, Switch, Tag, TextInput, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { platformApi, type TenantRow } from '@/api'
import { currentUser, switchTenant } from '@/composables/auth'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { pattern, required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'

/* ============================================================
   租户管理（平台侧）。lc_tenant 是平台表，平台管理员在本租户内直接
   新增 / 编辑 / 启停其它租户；要看某个租户的业务数据用「进入该租户」，
   它只换会话租户，不改管理员身份（见 /api/auth/switch-tenant）。
   ============================================================ */

const router = useRouter()

const list = useListPage<TenantRow, { keyword: string; status: string }>({
  query: { keyword: '', status: '' },
  fetch: params => platformApi.tenants.list(params) as Promise<any>,
  exporter: params => platformApi.tenants.export(params),
  exportName: '租户列表.csv',
})

const columns: Column[] = [
  { key: 'tenantCode', title: '租户编码', minWidth: 120 },
  { key: 'tenantName', title: '租户名称', minWidth: 160 },
  { key: 'contactName', title: '联系人', width: 110 },
  { key: 'contactPhone', title: '联系电话', width: 130 },
  { key: 'maxProjects', title: '项目配额', width: 96, align: 'right' },
  { key: 'userCount', title: '账号数', width: 90, align: 'right' },
  { key: 'expireDate', title: '到期日期', width: 120 },
  { key: 'status', title: '状态', width: 96 },
  { key: 'createTime', title: '创建时间', width: 150 },
  { key: 'actions', title: '操作', width: 132, className: 'col-actions' },
]

interface TenantForm {
  id?: number
  tenantCode: string
  tenantName: string
  contactName: string
  contactPhone: string
  maxProjects: number | null
  expireDate: string
  status: number
  remark: string
  adminUsername: string
  adminPassword: string
}
const emptyForm = (): TenantForm => ({
  tenantCode: '', tenantName: '', contactName: '', contactPhone: '', maxProjects: 5, expireDate: '', status: 1, remark: '',
  adminUsername: '', adminPassword: '',
})
const dialog = ref(false)
const saving = ref(false)
const form = reactive<TenantForm>(emptyForm())
const errors = ref<Partial<Record<keyof TenantForm, string>>>({})
const editing = computed(() => !!form.id)

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: TenantRow) {
  Object.assign(form, {
    id: row.id,
    tenantCode: row.tenantCode,
    tenantName: row.tenantName,
    contactName: row.contactName ?? '',
    contactPhone: row.contactPhone ?? '',
    maxProjects: row.maxProjects,
    expireDate: row.expireDate ?? '',
    status: row.status,
    remark: row.remark ?? '',
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    tenantName: [required('请输入租户名称')],
    tenantCode: editing.value ? [] : [required('请输入租户编码'), pattern(/^[A-Za-z][A-Za-z0-9_-]{1,63}$/, '字母开头，仅含字母、数字、下划线、短横线')],
    adminUsername: editing.value || form.adminUsername.trim() === '' ? [] : [pattern(/^[A-Za-z][A-Za-z0-9_]{2,31}$/, '字母开头，仅含字母、数字、下划线，长度 3-32')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload: any = {
    tenantName: form.tenantName.trim(),
    contactName: form.contactName.trim(),
    contactPhone: form.contactPhone.trim(),
    maxProjects: form.maxProjects ?? 1,
    expireDate: form.expireDate.trim() || undefined,
    status: form.status,
    remark: form.remark.trim(),
  }
  // 管理员账号只在新增时有意义：填了就顺带把租户开通（建角色 + 复制菜单与字典）
  if (!form.id && form.adminUsername.trim()) {
    payload.adminUsername = form.adminUsername.trim()
    payload.adminPassword = form.adminPassword.trim() || undefined
  }
  try {
    if (form.id) {
      await platformApi.tenants.update(form.id, payload)
      pushToast('租户已更新')
    } else {
      await platformApi.tenants.create({ ...payload, tenantCode: form.tenantCode.trim() })
      pushToast('租户已新增')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '租户保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: TenantRow) {
  const next = row.status === 1 ? 0 : 1
  const ok = await confirm({
    title: next === 0 ? '停用租户' : '启用租户',
    message: next === 0
      ? `停用后「${row.tenantName}」下的账号将无法登录，已有登录态不受影响。确认停用吗？`
      : `确认启用租户「${row.tenantName}」吗？`,
    confirmText: next === 0 ? '停用' : '启用',
    danger: next === 0,
  })
  if (!ok) return
  try {
    await platformApi.tenants.updateStatus(row.id, next)
    pushToast(next === 0 ? '租户已停用' : '租户已启用')
    await list.load()
  } catch (error) {
    toastError(error, '租户状态更新失败')
  }
}

/** 平台加了租户可见菜单后（如 V5 的「低代码」段），已有租户要点一次补齐才拿得到 */
async function syncAssets(row: TenantRow) {
  const ok = await confirm({
    title: '补齐租户资产',
    message: `按平台模板为「${row.tenantName}」补齐缺少的菜单、字典与内置参数，并给该租户的内置管理员角色补授权。已有内容不受影响，可重复执行。`,
    confirmText: '补齐',
  })
  if (!ok) return
  try {
    const result = await platformApi.tenantAssets.sync(row.id)
    pushToast(`已补齐：菜单 ${result.menuAdded} 项、字典 ${result.dictTypeAdded} 类 / ${result.dictDataAdded} 条、参数 ${result.configAdded} 条`)
  } catch (error) {
    toastError(error, '补齐租户资产失败')
  }
}

/** 进入某个租户：只切换会话租户，管理员身份与权限仍按本人角色解析 */
async function enterTenant(row: TenantRow) {
  const ok = await confirm({
    title: '进入该租户',
    message: `当前会话将切到「${row.tenantName}」，此后看到的都是该租户的业务数据；顶栏可一键回到平台租户。`,
    confirmText: '进入',
  })
  if (!ok) return
  try {
    await switchTenant(row.id)
    pushToast(`已进入租户「${row.tenantName}」`)
    await router.replace('/')
  } catch (error) {
    toastError(error, '切换租户失败')
  }
}

const isCurrentTenant = (row: TenantRow) => currentUser.value?.tenantId === row.id
</script>

<template>
  <ListPage
    title="租户管理"
    desc="租户是平台的隔离边界：账号、角色、菜单、字典与业务数据都归属于某个租户。停用租户后该租户下的账号无法登录。"
    table-title="租户列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="platform:tenant:add" variant="primary" icon="plus" @click="openCreate">新增租户</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="租户编码 / 名称 / 联系人" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <DictSelect v-model="list.query.status" type="sys_normal_disable" all-label="全部状态" />
      </Field>
    </template>

    <template #cell-tenantCode="{ row }"><span class="code-chip">{{ row.tenantCode }}</span></template>
    <template #cell-tenantName="{ row }">
      <span class="cell-title">{{ row.tenantName }}</span>
      <Tag v-if="isCurrentTenant(row)" tone="info">当前</Tag>
    </template>
    <template #cell-contactName="{ row }"><span class="muted">{{ row.contactName || '—' }}</span></template>
    <template #cell-contactPhone="{ row }"><span class="muted tnum">{{ row.contactPhone || '—' }}</span></template>
    <template #cell-maxProjects="{ row }"><span class="tnum">{{ row.maxProjects }}</span></template>
    <template #cell-userCount="{ row }"><span class="tnum">{{ row.userCount }}</span></template>
    <template #cell-expireDate="{ row }"><span class="tnum muted">{{ row.expireDate || '不限' }}</span></template>
    <template #cell-status="{ row }"><StatusTag :status="row.status" /></template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="platform:tenant:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton
          perm="platform:tenant:edit"
          icon-only
          :icon="row.status === 1 ? 'ban' : 'check'"
          :label="row.status === 1 ? '停用' : '启用'"
          :danger="row.status === 1"
          @click="toggleStatus(row)"
        />
        <PermissionButton
          v-if="!isCurrentTenant(row)"
          perm="platform:tenant:list"
          icon-only
          icon="arrowRight"
          label="进入该租户"
          @click="enterTenant(row)"
        />
        <PermissionButton perm="platform:tenant:edit" icon-only icon="refresh" label="补齐租户资产" @click="syncAssets(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" :title="editing ? '编辑租户' : '新增租户'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="租户编码" required :hint="editing ? '编码创建后不可修改，登录时用于定位租户' : '字母开头，可含数字、下划线、短横线'" :error="errors.tenantCode">
        <TextInput v-model="form.tenantCode" :disabled="editing" placeholder="如：acme" :invalid="!!errors.tenantCode" />
      </Field>
      <Field label="租户名称" required :error="errors.tenantName">
        <TextInput v-model="form.tenantName" placeholder="如：Acme 科技有限公司" :invalid="!!errors.tenantName" />
      </Field>
      <Field label="联系人">
        <TextInput v-model="form.contactName" placeholder="如：张三" />
      </Field>
      <Field label="联系电话">
        <TextInput v-model="form.contactPhone" placeholder="如：13800000000" />
      </Field>
      <Field label="项目配额" hint="该租户最多可创建的项目数">
        <TextInput v-model="form.maxProjects" type="number" />
      </Field>
      <Field label="到期日期" hint="留空表示不限">
        <DateInput v-model="form.expireDate" type="date" placeholder="选择到期日期" clearable />
      </Field>
      <Field label="租户状态" group>
        <Switch :model-value="form.status === 1" :label="form.status === 1 ? '正常' : '停用'" @update:model-value="form.status = $event ? 1 : 0" />
      </Field>
      <Field label="备注" span>
        <TextInput v-model="form.remark" multiline :rows="2" placeholder="交付范围、合同号等" :maxlength="200" />
      </Field>
      <template v-if="!editing">
        <Field label="管理员账号" hint="留空则只建租户，之后可再「补齐资产」并开通账号" :error="errors.adminUsername">
          <TextInput v-model="form.adminUsername" placeholder="如：acmeadmin" :invalid="!!errors.adminUsername" />
        </Field>
        <Field label="管理员初始密码" hint="留空时用系统参数「账号初始密码」">
          <TextInput v-model="form.adminPassword" type="password" placeholder="至少 8 位，含字母与数字" autocomplete="new-password" />
        </Field>
      </template>
    </FormDialog>
  </ListPage>
</template>
