<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { Field, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@/components/business'
import { platformApi, type TenantConfigRow } from '@/api'
import { currentUser } from '@/composables/auth'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { pattern, required, validate } from '@/utils/validate'

/* ============================================================
   租户参数（lc_tenant_config）：按租户存放的键值参数，读写都限定在
   「当前会话租户」内 —— 后端接口不收 tenant_id 入参，条件由租户拦截器注入，
   要改别的租户先在「租户管理」里进入该租户。
   ============================================================ */

const isSwitched = computed(() => {
  const user = currentUser.value
  return !!user && user.homeTenantId !== undefined && user.tenantId !== user.homeTenantId
})
const tenantLabel = computed(() => currentUser.value?.tenantName || '当前租户')

const list = useListPage<TenantConfigRow, { keyword: string }>({
  query: { keyword: '' },
  fetch: params => platformApi.tenantConfigs.list(params) as Promise<any>,
  exporter: params => platformApi.tenantConfigs.export(params),
  exportName: '租户参数.csv',
})

const columns: Column[] = [
  { key: 'configKey', title: '参数键', minWidth: 200 },
  { key: 'configName', title: '参数名称', minWidth: 160 },
  { key: 'configValue', title: '参数值', minWidth: 180 },
  { key: 'builtin', title: '来源', width: 100 },
  { key: 'remark', title: '备注', minWidth: 200 },
  { key: 'updateTime', title: '更新时间', width: 150 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

interface ConfigForm { id?: number; configKey: string; configValue: string; remark: string }
const emptyForm = (): ConfigForm => ({ configKey: '', configValue: '', remark: '' })
const dialog = ref(false)
const saving = ref(false)
const form = reactive<ConfigForm>(emptyForm())
const errors = ref<Partial<Record<keyof ConfigForm, string>>>({})

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: TenantConfigRow) {
  Object.assign(form, { id: row.id, configKey: row.configKey, configValue: row.configValue ?? '', remark: row.remark ?? '' })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    configKey: [required('请输入参数键'), pattern(/^[A-Za-z][A-Za-z0-9_.-]{1,127}$/, '字母开头，可含字母、数字、点、下划线、短横线')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    await platformApi.tenantConfigs.save({
      id: form.id,
      configKey: form.configKey.trim(),
      configValue: form.configValue.trim(),
      remark: form.remark.trim() || undefined,
    })
    pushToast('参数已保存')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '参数保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: TenantConfigRow) {
  const ok = await confirm({
    title: '删除参数',
    message: `确认删除参数「${row.configKey}」吗？生成链路如果依赖这个键，删除后该项目会回落到平台默认值。`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await platformApi.tenantConfigs.remove(row.id)
    pushToast('参数已删除')
    await list.load()
  } catch (error) {
    toastError(error, '参数删除失败')
  }
}
</script>

<template>
  <ListPage
    title="租户参数"
    :desc="`按租户存放的键值参数，生成项目时的分支等默认值来自这里。当前编辑的是「${tenantLabel}」${isSwitched ? '（已切换查看）' : ''}的参数；要改别的租户，请先在租户管理中进入该租户。`"
    table-title="参数列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="platform:config:edit" variant="primary" icon="plus" @click="openCreate">新增参数</PermissionButton>
    </template>

    <template #search>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="参数键 / 名称 / 备注" clearable @enter="list.search()" />
      </Field>
    </template>

    <template #cell-configKey="{ row }"><span class="code-chip">{{ row.configKey }}</span></template>
    <template #cell-configName="{ row }"><span class="cell-title">{{ row.configName || '—' }}</span></template>
    <template #cell-configValue="{ row }"><span class="tnum">{{ row.configValue || '—' }}</span></template>
    <template #cell-builtin="{ row }"><Tag :tone="row.builtin ? 'info' : 'neutral'">{{ row.builtin ? '系统内置' : '自定义' }}</Tag></template>
    <template #cell-remark="{ row }"><span class="muted">{{ row.remark || '—' }}</span></template>
    <template #cell-updateTime="{ row }"><span class="tnum soft">{{ row.updateTime }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="platform:config:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton
          v-if="!row.builtin"
          perm="platform:config:edit"
          icon-only
          icon="trash"
          label="删除"
          danger
          @click="remove(row)"
        />
      </div>
    </template>

    <FormDialog :open="dialog" :title="form.id ? '编辑参数' : '新增参数'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="参数键" required :hint="form.id ? '参数键被生成链路按名读取，改名等于新增一个参数' : '如 project.gen_branch'" :error="errors.configKey">
        <TextInput v-model="form.configKey" placeholder="如 project.gen_branch" :invalid="!!errors.configKey" />
      </Field>
      <Field label="参数值" span>
        <TextInput v-model="form.configValue" placeholder="参数值" />
      </Field>
      <Field label="备注" span>
        <TextInput v-model="form.remark" multiline :rows="2" placeholder="这个参数给谁用、取值范围" :maxlength="200" />
      </Field>
    </FormDialog>
  </ListPage>
</template>
