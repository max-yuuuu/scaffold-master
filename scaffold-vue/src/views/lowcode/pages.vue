<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Field, Select, StatusTag, TextInput, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { pattern, required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'
import { platformApi, type FieldRow, type PageDetail, type PageRow, type ProjectOption } from '@/api'
import DesignerWorkspace from './designer/DesignerWorkspace.vue'
import { initFromModel } from './designer/design.ts'

/* ============================================================
   页面设计（PR 6C）。列表这一层只管「有哪些页面」，画布在 DesignerWorkspace 里。
   新建页面时先按模型的标志位生成一份初始设计（is_query → 查询区、is_list → 列、
   is_insert / is_edit → 表单），再进画布调：空白一页要手工拖满五块是重复劳动，
   而初始化用的判据与后端是同一份，不会出现「拖出来的存不下」。
   ============================================================ */

const projects = ref<ProjectOption[]>([])
const models = ref<Array<{ id: number, modelCode: string, modelName: string }>>([])

const list = useListPage<PageRow, { projectId: string, keyword: string }>({
  query: { projectId: '', keyword: '' },
  fetch: params => platformApi.pages.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'pageName', title: '页面名称', minWidth: 160 },
  { key: 'pageCode', title: '页面编码', minWidth: 150 },
  { key: 'projectName', title: '归属项目', minWidth: 140 },
  { key: 'modelCode', title: '绑定模型', minWidth: 140 },
  { key: 'routePath', title: '路由落点', minWidth: 160 },
  { key: 'refFieldCount', title: '引用字段', width: 96, align: 'right' },
  { key: 'status', title: '状态', width: 90 },
  { key: 'updateTime', title: '更新时间', width: 150 },
  { key: 'actions', title: '操作', width: 150, className: 'col-actions' },
]

const projectOptions = computed(() => [{ label: '全部项目', value: '' }, ...projects.value.map(item => ({ label: item.projectName, value: String(item.id) }))])
const projectFormOptions = computed(() => projects.value.map(item => ({ label: `${item.projectName}（${item.projectCode}）`, value: String(item.id) })))
const modelFormOptions = computed(() => models.value.map(item => ({ label: `${item.modelName}（${item.modelCode}）`, value: item.modelCode })))

async function loadProjects() {
  projects.value = await platformApi.projects.options() as any
}

onMounted(loadProjects)

/* ── 新建页面：项目 → 模型 → 按模型初始化一份设计 ── */
const dialog = ref(false)
const saving = ref(false)
const form = reactive({ projectId: '', modelCode: '', pageCode: '', pageName: '' })
const errors = ref<Record<string, string>>({})

async function pickProject(value: unknown) {
  form.projectId = String(value ?? '')
  form.modelCode = ''
  models.value = await platformApi.models.options(Number(form.projectId))
}

function openCreate() {
  Object.assign(form, { projectId: '', modelCode: '', pageCode: '', pageName: '' })
  models.value = []
  errors.value = {}
  dialog.value = true
}

async function submitCreate() {
  errors.value = validate(form, {
    projectId: [required('请选择归属项目')],
    modelCode: [required('请选择绑定模型')],
    pageCode: [required('请输入页面编码'), pattern(/^[a-z][a-z0-9_]{1,63}$/, '小写字母开头，仅含小写字母、数字、下划线')],
    pageName: [required('请输入页面名称')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  try {
    // 初始化用的字段清单就是生成器吃的那一份：两处都从模型读，不会漂
    const fields = await platformApi.models.fields(models.value.find(item => item.modelCode === form.modelCode)!.id)
    const design = initFromModel(fields as any)
    design.modelRef = form.modelCode
    const created = await platformApi.pages.create({
      projectId: Number(form.projectId),
      modelCode: form.modelCode,
      pageCode: form.pageCode,
      pageName: form.pageName,
      design: design as unknown as Record<string, unknown>,
    })
    dialog.value = false
    await list.load()
    await openDesigner(Number((created as { id: number }).id))
  } catch (error) {
    toastError(error, '新建页面失败')
  } finally {
    saving.value = false
  }
}

/* ── 画布：整屏接管，返回时刷新列表 ── */
const editing = ref<PageDetail | null>(null)
const editFields = ref<FieldRow[]>([])

async function openDesigner(id: number) {
  try {
    const detail = await platformApi.pages.detail(id)
    editFields.value = await platformApi.models.fields(detail.modelId)
    editing.value = detail
  } catch (error) {
    toastError(error, '打开设计器失败')
  }
}

function closeDesigner() {
  editing.value = null
  list.load()
}

async function toggle(row: PageRow) {
  const next = row.status === 1 ? 0 : 1
  try {
    await platformApi.pages.updateStatus(row.id, next)
    pushToast(next === 1 ? '页面已启用' : '页面已停用，不参与发布')
    await list.load()
  } catch (error) {
    toastError(error, '状态更新失败')
  }
}

async function remove(row: PageRow) {
  const ok = await confirm({
    title: `删除页面 ${row.pageName}`,
    message: '删除后这份设计不再参与发布；已发布到项目仓库里的页面不会自动回滚。',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await platformApi.pages.remove(row.id)
    pushToast('页面已删除')
    await list.load()
  } catch (error) {
    toastError(error, '删除失败')
  }
}
</script>

<template>
  <DesignerWorkspace v-if="editing" :page="editing" :fields="editFields" @close="closeDesigner" />

  <ListPage
    v-else
    title="页面设计"
    desc="页面是形、模型是料：设计器里摆的每一格都要在模型里有对应的字段。"
    table-title="页面列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="lowcode:page:add" variant="primary" icon="plus" @click="openCreate">新增页面</PermissionButton>
    </template>

    <template #search>
      <Field label="归属项目">
        <Select v-model="list.query.projectId" :options="projectOptions" @update:model-value="list.search()" />
      </Field>
      <Field label="关键字">
        <TextInput v-model="list.query.keyword" placeholder="页面编码 / 名称" clearable @enter="list.search()" />
      </Field>
    </template>

    <template #cell-refFieldCount="{ row }"><span class="tnum">{{ row.refFieldCount }}</span></template>
    <template #cell-status="{ row }"><StatusTag :status="row.status" /></template>
    <template #cell-updateTime="{ row }"><span class="tnum soft">{{ row.updateTime }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="lowcode:page:edit" icon-only icon="edit" label="设计" @click="openDesigner(row.id)" />
        <PermissionButton perm="lowcode:page:edit" icon-only icon="refresh" :label="row.status === 1 ? '停用' : '启用'" @click="toggle(row)" />
        <PermissionButton perm="lowcode:page:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>
  </ListPage>

  <FormDialog :open="dialog" title="新增页面" :saving="saving" @close="dialog = false" @submit="submitCreate">
    <Field label="归属项目" required :error="errors.projectId">
      <Select v-model="form.projectId" :options="projectFormOptions" @update:model-value="pickProject" />
    </Field>
    <Field label="绑定模型" required :error="errors.modelCode">
      <Select v-model="form.modelCode" :options="modelFormOptions" placeholder="先选项目" />
    </Field>
    <Field label="页面编码" required :error="errors.pageCode">
      <TextInput v-model="form.pageCode" placeholder="sales_order_list" />
    </Field>
    <Field label="页面名称" required :error="errors.pageName">
      <TextInput v-model="form.pageName" placeholder="销售订单列表" />
    </Field>
    <p class="form-note">保存后按模型的 is_query / is_list / is_insert 生成初始布局，进画布再调。</p>
  </FormDialog>
</template>

<style scoped>
.form-note {
  margin: 2px 0 0;
  color: var(--ink-faint);
  font-size: 12px;
}
</style>
