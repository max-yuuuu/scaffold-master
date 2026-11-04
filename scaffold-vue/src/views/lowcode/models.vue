<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DataTable, Drawer, Field, Select, StatusTag, Switch, Tag, Textarea, TextInput, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { platformApi, systemApi, type FieldRow, type FieldTypeMeta, type ModelMeta, type ModelRow, type ProjectOption } from '@/api'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { pattern, required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'

/* ============================================================
   模型管理。模型是代码生成器的输入（方案 §1.4）：一张业务表 + 一套 CRUD 的期望态。
   字段在右侧抽屉里配置；「字段类型 → Java 类型 / 列类型 / 默认控件」的映射表
   由后端 /api/lowcode/models/meta 提供，前端不另存一份，避免两处口径漂移。
   ============================================================ */

const meta = ref<ModelMeta>({ fieldTypes: [], formTypes: [], queryTypes: [] })
const projects = ref<ProjectOption[]>([])
const dictTypes = ref<Array<{ label: string, value: string }>>([])

const list = useListPage<ModelRow, { projectId: string; keyword: string; status: string }>({
  query: { projectId: '', keyword: '', status: '' },
  fetch: params => platformApi.models.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'modelCode', title: '模型编码', minWidth: 140 },
  { key: 'modelName', title: '模型名称', minWidth: 140 },
  { key: 'projectName', title: '归属项目', minWidth: 150 },
  { key: 'tableName', title: '数据表', minWidth: 150 },
  { key: 'className', title: '实体类', minWidth: 140 },
  { key: 'fieldCount', title: '字段数', width: 90, align: 'right' },
  { key: 'flags', title: '生成开关', minWidth: 200 },
  { key: 'status', title: '状态', width: 96 },
  { key: 'updateTime', title: '更新时间', width: 150 },
  { key: 'actions', title: '操作', width: 132, className: 'col-actions' },
]

const projectFilterOptions = computed(() => [
  { label: '全部项目', value: '' },
  ...projects.value.map(item => ({ label: `${item.projectName}（${item.projectCode}）`, value: String(item.id) })),
])
const projectFormOptions = computed(() => projects.value.map(item => ({ label: `${item.projectName}（${item.projectCode}）`, value: String(item.id) })))

watch(() => list.query.projectId, () => list.search())

/* ── 模型表单 ── */
interface ModelForm {
  id?: number
  projectId: string
  modelCode: string
  modelName: string
  modelDesc: string
  tableName: string
  className: string
  moduleName: string
  isTree: number
  enableLogicDelete: number
  enableDataScope: number
  enableAuditField: number
  status: number
}
const emptyModel = (): ModelForm => ({
  projectId: '', modelCode: '', modelName: '', modelDesc: '', tableName: '', className: '', moduleName: '',
  isTree: 0, enableLogicDelete: 1, enableDataScope: 0, enableAuditField: 1, status: 1,
})
const modelDialog = ref(false)
const modelSaving = ref(false)
const modelForm = reactive<ModelForm>(emptyModel())
const modelErrors = ref<Partial<Record<keyof ModelForm, string>>>({})

function openModelCreate() {
  Object.assign(modelForm, emptyModel(), { id: undefined, projectId: list.query.projectId })
  modelErrors.value = {}
  modelDialog.value = true
}

function openModelEdit(row: ModelRow) {
  Object.assign(modelForm, {
    id: row.id,
    projectId: String(row.projectId),
    modelCode: row.modelCode,
    modelName: row.modelName,
    modelDesc: row.modelDesc ?? '',
    tableName: row.tableName,
    className: row.className,
    moduleName: row.moduleName,
    isTree: row.isTree,
    enableLogicDelete: row.enableLogicDelete,
    enableDataScope: row.enableDataScope,
    enableAuditField: row.enableAuditField,
    status: row.status,
  })
  modelErrors.value = {}
  modelDialog.value = true
}

async function submitModel() {
  modelErrors.value = validate(modelForm, {
    modelName: [required('请输入模型名称')],
    projectId: [required('请选择归属项目')],
    modelCode: modelForm.id ? [] : [required('请输入模型编码'), pattern(/^[a-z][a-z0-9_]{1,31}$/, '小写字母开头，可含数字与下划线，长度 2-32')],
  })
  if (Object.keys(modelErrors.value).length) return
  modelSaving.value = true
  const payload = {
    projectId: Number(modelForm.projectId),
    modelCode: modelForm.modelCode.trim() || undefined,
    modelName: modelForm.modelName.trim(),
    modelDesc: modelForm.modelDesc.trim() || undefined,
    tableName: modelForm.tableName.trim() || undefined,
    className: modelForm.className.trim() || undefined,
    moduleName: modelForm.moduleName.trim() || undefined,
    isTree: modelForm.isTree,
    enableLogicDelete: modelForm.enableLogicDelete,
    enableDataScope: modelForm.enableDataScope,
    enableAuditField: modelForm.enableAuditField,
    status: modelForm.status,
  }
  try {
    if (modelForm.id) await platformApi.models.update(modelForm.id, payload)
    else await platformApi.models.create(payload)
    pushToast(modelForm.id ? '模型已更新' : '模型已创建')
    modelDialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '模型保存失败')
  } finally {
    modelSaving.value = false
  }
}

async function toggleModelStatus(row: ModelRow) {
  const next = row.status === 1 ? 0 : 1
  try {
    await platformApi.models.updateStatus(row.id, next)
    pushToast(next === 0 ? '模型已停用' : '模型已启用')
    await list.load()
  } catch (error) {
    toastError(error, '模型状态更新失败')
  }
}

async function removeModel(row: ModelRow) {
  const ok = await confirm({
    title: '删除模型',
    message: `删除「${row.modelName}」会同时移除它的 ${row.fieldCount} 个字段配置。已发布到仓库的代码不会被回滚，但此后发布不再生成这个模型。确认删除吗？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await platformApi.models.remove(row.id)
    pushToast('模型已删除')
    if (current.value?.id === row.id) current.value = null
    await list.load()
  } catch (error) {
    toastError(error, '模型删除失败')
  }
}

/* ── 字段抽屉 ── */
const current = ref<ModelRow | null>(null)
const fields = ref<FieldRow[]>([])
const fieldsLoading = ref(false)
const refModelOptions = ref<Array<{ id: number, modelName: string, modelCode: string }>>([])
const refFieldOptions = ref<Array<{ id: number, fieldCode: string, fieldName: string }>>([])

const fieldColumns: Column[] = [
  { key: 'fieldCode', title: '字段编码', minWidth: 140 },
  { key: 'fieldName', title: '名称', minWidth: 120 },
  { key: 'fieldType', title: '类型', width: 110 },
  { key: 'columnType', title: '列类型', minWidth: 140 },
  { key: 'javaType', title: 'Java 类型', width: 130 },
  { key: 'usage', title: '用途', minWidth: 160 },
  { key: 'sortOrder', title: '排序', width: 80, align: 'right' },
  { key: 'actions', title: '操作', width: 84, className: 'col-actions' },
]

async function openFields(row: ModelRow) {
  current.value = row
  await loadFields()
}

async function loadFields() {
  const model = current.value
  if (!model) return
  fieldsLoading.value = true
  try {
    const [rows, sameProjectModels] = await Promise.all([
      platformApi.models.fields(model.id),
      platformApi.models.options(model.projectId),
    ])
    fields.value = rows
    refModelOptions.value = sameProjectModels
      .filter(item => item.id !== model.id)
      .map(item => ({ id: item.id, modelName: item.modelName, modelCode: item.modelCode }))
  } catch (error) {
    toastError(error, '字段加载失败')
  } finally {
    fieldsLoading.value = false
  }
}

/* ── 字段表单 ── */
interface FieldForm {
  id?: number
  fieldCode: string
  fieldName: string
  fieldDesc: string
  fieldType: string
  columnLength: string
  columnPrecision: string
  columnScale: string
  isPrimary: number
  isAutoIncrement: number
  isNullable: number
  isUnique: number
  defaultValue: string
  isList: number
  isQuery: number
  queryType: string
  isInsert: number
  isEdit: number
  isRequired: number
  formType: string
  optionsText: string
  dictType: string
  refModelId: string
  refFieldId: string
  sortOrder: string
}
const emptyField = (): FieldForm => ({
  fieldCode: '', fieldName: '', fieldDesc: '', fieldType: 'STRING',
  columnLength: '', columnPrecision: '', columnScale: '',
  isPrimary: 0, isAutoIncrement: 0, isNullable: 1, isUnique: 0, defaultValue: '',
  isList: 1, isQuery: 0, queryType: 'EQ', isInsert: 1, isEdit: 1, isRequired: 0,
  formType: '', optionsText: '', dictType: '', refModelId: '', refFieldId: '', sortOrder: '',
})
const fieldDialog = ref(false)
const fieldSaving = ref(false)
const fieldForm = reactive<FieldForm>(emptyField())
const fieldErrors = ref<Partial<Record<keyof FieldForm, string>>>({})

const selectedType = computed<FieldTypeMeta | undefined>(() => meta.value.fieldTypes.find(item => item.code === fieldForm.fieldType))
const showsLength = computed(() => ['STRING', 'ENUM', 'FILE'].includes(fieldForm.fieldType))
const showsDecimal = computed(() => fieldForm.fieldType === 'DECIMAL')
const showsReference = computed(() => fieldForm.fieldType === 'REF')
const effectiveFormType = computed(() => fieldForm.formType || selectedType.value?.formType || 'INPUT')
const showsOptions = computed(() => ['RADIO', 'CHECKBOX'].includes(effectiveFormType.value))
/** 映射预览：整数自增主键会升级为 bigint / Long，其余一律按后端 meta 给的值显示 */
const derivedHint = computed(() => {
  const type = selectedType.value
  if (!type) return ''
  const autoKey = type.code === 'INTEGER' && fieldForm.isPrimary === 1 && fieldForm.isAutoIncrement === 1
  return `生成结果 → ${autoKey ? 'Long' : type.javaType} · ${autoKey ? 'bigint' : type.columnType}`
})

watch(() => fieldForm.refModelId, id => loadRefFields(id ? Number(id) : null))

function openFieldCreate() {
  Object.assign(fieldForm, emptyField(), { id: undefined, sortOrder: String(fields.value.length + 1) })
  fieldErrors.value = {}
  refFieldOptions.value = []
  fieldDialog.value = true
}

function openFieldEdit(row: FieldRow) {
  Object.assign(fieldForm, {
    id: row.id,
    fieldCode: row.fieldCode,
    fieldName: row.fieldName,
    fieldDesc: row.fieldDesc ?? '',
    fieldType: row.fieldType,
    columnLength: row.columnLength == null ? '' : String(row.columnLength),
    columnPrecision: row.columnPrecision == null ? '' : String(row.columnPrecision),
    columnScale: row.columnScale == null ? '' : String(row.columnScale),
    isPrimary: row.isPrimary,
    isAutoIncrement: row.isAutoIncrement,
    isNullable: row.isNullable,
    isUnique: row.isUnique,
    defaultValue: row.defaultValue ?? '',
    isList: row.isList,
    isQuery: row.isQuery,
    queryType: row.queryType,
    isInsert: row.isInsert,
    isEdit: row.isEdit,
    isRequired: row.isRequired,
    formType: row.formType,
    optionsText: (row.formOptions ?? []).map(option => `${option.label}:${option.value}`).join('\n'),
    dictType: row.dictType ?? '',
    refModelId: row.refModelId == null ? '' : String(row.refModelId),
    refFieldId: row.refFieldId == null ? '' : String(row.refFieldId),
    sortOrder: String(row.sortOrder),
  })
  fieldErrors.value = {}
}

async function loadRefFields(refModelId: number | null) {
  if (!refModelId) {
    refFieldOptions.value = []
    return
  }
  try {
    refFieldOptions.value = await platformApi.models.fieldOptions(refModelId)
  } catch (error) {
    toastError(error, '关联模型字段加载失败')
  }
}

/** 选项文本每行一条「标签:值」，与后端 form_options 的 {label,value} 数组互转 */
function parseOptions(text: string) {
  return text.split('\n').map(line => line.trim()).filter(Boolean).map(line => {
    const index = line.indexOf(':')
    return index < 0 ? { label: line, value: line } : { label: line.slice(0, index).trim(), value: line.slice(index + 1).trim() }
  })
}

async function submitField() {
  fieldErrors.value = validate(fieldForm, {
    fieldCode: [required('请输入字段编码'), pattern(/^[a-z][A-Za-z0-9]{1,63}$/, '小写字母开头，驼峰式，仅含字母与数字')],
    fieldName: [required('请输入字段名称')],
    fieldType: [required('请选择字段类型')],
  })
  if (Object.keys(fieldErrors.value).length) return
  const model = current.value
  if (!model) return
  fieldSaving.value = true
  const num = (value: string) => (value.trim() === '' ? undefined : Number(value))
  const payload = {
    fieldCode: fieldForm.fieldCode.trim() || undefined,
    fieldName: fieldForm.fieldName.trim(),
    fieldDesc: fieldForm.fieldDesc.trim() || undefined,
    fieldType: fieldForm.fieldType,
    columnLength: num(fieldForm.columnLength),
    columnPrecision: num(fieldForm.columnPrecision),
    columnScale: num(fieldForm.columnScale),
    isPrimary: fieldForm.isPrimary,
    isAutoIncrement: fieldForm.isAutoIncrement,
    isNullable: fieldForm.isNullable,
    isUnique: fieldForm.isUnique,
    defaultValue: fieldForm.defaultValue.trim() || undefined,
    isList: fieldForm.isList,
    isQuery: fieldForm.isQuery,
    queryType: fieldForm.queryType,
    isInsert: fieldForm.isInsert,
    isEdit: fieldForm.isEdit,
    isRequired: fieldForm.isRequired,
    formType: fieldForm.formType || undefined,
    formOptions: showsOptions.value ? parseOptions(fieldForm.optionsText) : undefined,
    dictType: fieldForm.dictType.trim() || undefined,
    refModelId: fieldForm.refModelId ? Number(fieldForm.refModelId) : undefined,
    refFieldId: fieldForm.refFieldId ? Number(fieldForm.refFieldId) : undefined,
    sortOrder: num(fieldForm.sortOrder),
  }
  try {
    if (fieldForm.id) await platformApi.models.updateField(fieldForm.id, payload)
    else await platformApi.models.createField(model.id, payload)
    pushToast(fieldForm.id ? '字段已更新' : '字段已新增')
    fieldDialog.value = false
    await loadFields()
    await list.load()
  } catch (error) {
    toastError(error, '字段保存失败')
  } finally {
    fieldSaving.value = false
  }
}

async function removeField(row: FieldRow) {
  const ok = await confirm({
    title: '删除字段',
    message: `删除「${row.fieldName}」后，引用了它的页面需要重新配置。确认删除吗？`,
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await platformApi.models.removeField(row.id)
    pushToast('字段已删除')
    await loadFields()
    await list.load()
  } catch (error) {
    toastError(error, '字段删除失败')
  }
}

function usageText(row: FieldRow) {
  const parts: string[] = []
  if (row.isPrimary === 1) parts.push('主键')
  if (row.isList === 1) parts.push('列表')
  if (row.isQuery === 1) parts.push('查询')
  if (row.isInsert === 1) parts.push('新增')
  if (row.isEdit === 1) parts.push('编辑')
  return parts
}

async function bootstrap() {
  const [metaResult, projectResult] = await Promise.all([platformApi.models.meta(), platformApi.projects.options()])
  meta.value = metaResult
  projects.value = projectResult
  try {
    const page = await systemApi.dictTypes.list({ pageNum: 1, pageSize: 200 })
    dictTypes.value = page.rows.map(row => ({ label: `${row.dictName}（${row.dictType}）`, value: String(row.dictType) }))
  } catch {
    // 没有字典管理权限时不挡住字段配置：保存时后端会校验字典是否存在
    dictTypes.value = []
  }
}

onMounted(bootstrap)
</script>

<template>
  <ListPage
    title="模型管理"
    desc="模型描述一张业务表与它的字段，是代码生成器的输入。改模型不会改动已发布到仓库的代码，差异通过发布与合并请求体现。"
    table-title="模型列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="lowcode:model:add" variant="primary" icon="plus" @click="openModelCreate">新增模型</PermissionButton>
    </template>

    <template #search>
      <Field label="归属项目">
        <Select v-model="list.query.projectId" :options="projectFilterOptions" />
      </Field>
      <Field label="关键词" wide>
        <TextInput v-model="list.query.keyword" icon="search" placeholder="模型编码 / 名称" clearable @enter="list.search()" />
      </Field>
      <Field label="状态">
        <DictSelect v-model="list.query.status" type="sys_normal_disable" all-label="全部状态" />
      </Field>
    </template>

    <template #cell-modelCode="{ row }"><span class="code-chip">{{ row.modelCode }}</span></template>
    <template #cell-modelName="{ row }"><span class="cell-title">{{ row.modelName }}</span></template>
    <template #cell-projectName="{ row }"><span class="muted">{{ row.projectName || '项目已删除' }}</span></template>
    <template #cell-tableName="{ row }"><span class="code-chip">{{ row.tableName }}</span></template>
    <template #cell-className="{ row }"><span class="code-chip">{{ row.className }}</span></template>
    <template #cell-fieldCount="{ row }"><span class="tnum">{{ row.fieldCount }}</span></template>
    <template #cell-flags="{ row }">
      <span class="flag-row">
        <Tag v-if="row.isTree === 1" tone="info">树表</Tag>
        <Tag v-if="row.enableLogicDelete === 1" tone="neutral">逻辑删除</Tag>
        <Tag v-if="row.enableDataScope === 1" tone="neutral">数据权限</Tag>
        <Tag v-if="row.enableAuditField === 1" tone="neutral">审计字段</Tag>
      </span>
    </template>
    <template #cell-status="{ row }"><StatusTag :status="row.status" /></template>
    <template #cell-updateTime="{ row }"><span class="tnum soft">{{ row.updateTime }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="lowcode:model:list" icon-only icon="grid" label="字段配置" @click="openFields(row)" />
        <PermissionButton perm="lowcode:model:edit" icon-only icon="edit" label="编辑" @click="openModelEdit(row)" />
        <PermissionButton
          perm="lowcode:model:edit"
          icon-only
          :icon="row.status === 1 ? 'ban' : 'check'"
          :label="row.status === 1 ? '停用' : '启用'"
          :danger="row.status === 1"
          @click="toggleModelStatus(row)"
        />
        <PermissionButton perm="lowcode:model:remove" icon-only icon="trash" label="删除" danger @click="removeModel(row)" />
      </div>
    </template>

    <FormDialog :open="modelDialog" :title="modelForm.id ? '编辑模型' : '新增模型'" :saving="modelSaving" @close="modelDialog = false" @submit="submitModel">
      <Field label="归属项目" required :hint="modelForm.id ? '归属项目创建后不可改：换项目等于换仓库' : ''" :error="modelErrors.projectId">
        <Select v-model="modelForm.projectId" :options="projectFormOptions" :disabled="Boolean(modelForm.id)" placeholder="请选择项目" />
      </Field>
      <Field label="模型编码" required hint="小写下划线，决定数据表与类名的默认值；创建后不可修改" :error="modelErrors.modelCode">
        <TextInput v-model="modelForm.modelCode" :disabled="Boolean(modelForm.id)" placeholder="如：order_detail" :invalid="!!modelErrors.modelCode" />
      </Field>
      <Field label="模型名称" required hint="生成页面的标题与菜单名" :error="modelErrors.modelName">
        <TextInput v-model="modelForm.modelName" placeholder="如：订单明细" />
      </Field>
      <Field label="数据表名" hint="留空按模型编码派生">
        <TextInput v-model="modelForm.tableName" placeholder="如：order_detail" />
      </Field>
      <Field label="实体类名" hint="留空按模型编码派生">
        <TextInput v-model="modelForm.className" placeholder="如：OrderDetail" />
      </Field>
      <Field label="业务子包" hint="生成代码进 scaffold-business 的这个子包">
        <TextInput v-model="modelForm.moduleName" placeholder="如：order_detail" />
      </Field>
      <Field label="模型说明" span>
        <Textarea v-model="modelForm.modelDesc" :rows="2" placeholder="这个模型管什么数据" :maxlength="256" />
      </Field>
      <Field label="树表" group hint="本期不生成：勾选后发布会被拒绝，树形取数请在生成的扩展点类里手写">
        <Switch :model-value="modelForm.isTree === 1" :label="modelForm.isTree === 1 ? '是' : '否'" @update:model-value="modelForm.isTree = $event ? 1 : 0" />
      </Field>
      <Field label="逻辑删除" group>
        <Switch :model-value="modelForm.enableLogicDelete === 1" :label="modelForm.enableLogicDelete === 1 ? '生成 deleted 列' : '物理删除'" @update:model-value="modelForm.enableLogicDelete = $event ? 1 : 0" />
      </Field>
      <Field label="数据权限" group hint="本期不生成：勾选后发布会被拒绝，部门过滤请在生成的扩展点类里手写">
        <Switch :model-value="modelForm.enableDataScope === 1" :label="modelForm.enableDataScope === 1 ? '按部门过滤' : '不过滤'" @update:model-value="modelForm.enableDataScope = $event ? 1 : 0" />
      </Field>
      <Field label="审计字段" group>
        <Switch :model-value="modelForm.enableAuditField === 1" :label="modelForm.enableAuditField === 1 ? '带创建/更新人时间' : '不带'" @update:model-value="modelForm.enableAuditField = $event ? 1 : 0" />
      </Field>
      <Field label="模型状态" group>
        <Switch :model-value="modelForm.status === 1" :label="modelForm.status === 1 ? '正常' : '停用'" @update:model-value="modelForm.status = $event ? 1 : 0" />
      </Field>
    </FormDialog>

    <Drawer :open="Boolean(current)" :title="current?.modelName" :sub="current ? `${current.tableName} · ${fields.length} 个字段` : ''" :width="880" @close="current = null">
      <div class="drawer-toolbar">
        <span class="soft" style="font-size: 12px">字段顺序决定生成的实体属性、表单与列表列的顺序</span>
        <PermissionButton perm="lowcode:field:add" variant="primary" size="sm" icon="plus" @click="openFieldCreate">新增字段</PermissionButton>
      </div>
      <div class="m-surface panel" style="overflow: hidden">
        <DataTable :columns="fieldColumns" :data="fields" :loading="fieldsLoading" :min-width="760" empty-text="还没有字段，先加一个主键">
          <template #cell-fieldCode="{ row }"><span class="code-chip">{{ row.fieldCode }}</span></template>
          <template #cell-fieldName="{ row }"><span class="cell-title">{{ row.fieldName }}</span></template>
          <template #cell-fieldType="{ row }"><span class="muted">{{ row.fieldType }}</span></template>
          <template #cell-columnType="{ row }"><span class="code-chip">{{ row.columnType }}</span></template>
          <template #cell-javaType="{ row }"><span class="muted">{{ row.javaType }}</span></template>
          <template #cell-usage="{ row }">
            <span class="flag-row"><Tag v-for="part in usageText(row)" :key="part" tone="neutral">{{ part }}</Tag></span>
          </template>
          <template #cell-sortOrder="{ row }"><span class="tnum soft">{{ row.sortOrder }}</span></template>
          <template #cell-actions="{ row }">
            <div class="row-actions">
              <PermissionButton perm="lowcode:field:edit" icon-only icon="edit" label="编辑" @click="openFieldEdit(row)" />
              <PermissionButton perm="lowcode:field:remove" icon-only icon="trash" label="删除" danger @click="removeField(row)" />
            </div>
          </template>
        </DataTable>
      </div>
    </Drawer>

    <FormDialog :open="fieldDialog" :title="fieldForm.id ? '编辑字段' : '新增字段'" :sub="current?.modelName" :saving="fieldSaving" @close="fieldDialog = false" @submit="submitField">
      <Field label="字段类型" required :hint="derivedHint" :error="fieldErrors.fieldType">
        <Select v-model="fieldForm.fieldType" :options="meta.fieldTypes.map(item => ({ label: `${item.label}（${item.code}）`, value: item.code }))" />
      </Field>
      <Field label="字段编码" required hint="小驼峰，生成实体属性与前端字段名" :error="fieldErrors.fieldCode">
        <TextInput v-model="fieldForm.fieldCode" placeholder="如：orderNo" :invalid="!!fieldErrors.fieldCode" />
      </Field>
      <Field label="字段名称" required hint="表单标签与列表表头" :error="fieldErrors.fieldName">
        <TextInput v-model="fieldForm.fieldName" placeholder="如：订单号" />
      </Field>
      <Field v-if="showsLength" label="列长度" hint="留空用类型默认值">
        <TextInput v-model="fieldForm.columnLength" type="number" />
      </Field>
      <template v-if="showsDecimal">
        <Field label="精度" hint="总位数">
          <TextInput v-model="fieldForm.columnPrecision" type="number" placeholder="18" />
        </Field>
        <Field label="小数位">
          <TextInput v-model="fieldForm.columnScale" type="number" placeholder="2" />
        </Field>
      </template>
      <template v-if="showsReference">
        <Field label="关联模型" hint="只能指向同一项目内的模型">
          <Select v-model="fieldForm.refModelId" :options="[{ label: '请选择', value: '' }, ...refModelOptions.map(item => ({ label: `${item.modelName}（${item.modelCode}）`, value: String(item.id) }))]" />
        </Field>
        <Field label="展示字段" hint="下拉里显示目标模型的哪一列，留空显示主键">
          <Select v-model="fieldForm.refFieldId" :options="[{ label: '默认主键', value: '' }, ...refFieldOptions.map(item => ({ label: `${item.fieldName}（${item.fieldCode}）`, value: String(item.id) }))]" />
        </Field>
      </template>
      <Field label="字典类型" hint="列表与表单按字典翻译取值；字典枚举必填">
        <Select v-model="fieldForm.dictType" :options="[{ label: '不挂字典', value: '' }, ...dictTypes]" />
      </Field>
      <Field label="表单控件" :hint="`默认：${selectedType?.formType ?? 'INPUT'}`">
        <Select v-model="fieldForm.formType" :options="[{ label: `跟随类型（${effectiveFormType}）`, value: '' }, ...meta.formTypes.map(item => ({ label: `${item.label}（${item.code}）`, value: item.code }))]" />
      </Field>
      <Field v-if="showsOptions" label="固定选项" span hint="每行一条，写成「标签:值」；挂了字典时可以不填">
        <Textarea v-model="fieldForm.optionsText" :rows="3" placeholder="启用:1&#10;停用:0" />
      </Field>
      <Field label="默认值" hint="建表语句里的 DEFAULT">
        <TextInput v-model="fieldForm.defaultValue" placeholder="如：0" />
      </Field>
      <Field label="主键" group>
        <Switch :model-value="fieldForm.isPrimary === 1" :label="fieldForm.isPrimary === 1 ? '是主键' : '非主键'" @update:model-value="fieldForm.isPrimary = $event ? 1 : 0" />
      </Field>
      <Field v-if="fieldForm.isPrimary === 1" label="自增" group hint="自增主键固定 bigint / Long">
        <Switch :model-value="fieldForm.isAutoIncrement === 1" :label="fieldForm.isAutoIncrement === 1 ? '自增' : '不自增'" @update:model-value="fieldForm.isAutoIncrement = $event ? 1 : 0" />
      </Field>
      <Field label="可空" group>
        <Switch :model-value="fieldForm.isNullable === 1" :label="fieldForm.isNullable === 1 ? '允许为空' : '不允许为空'" @update:model-value="fieldForm.isNullable = $event ? 1 : 0" />
      </Field>
      <Field label="唯一" group>
        <Switch :model-value="fieldForm.isUnique === 1" :label="fieldForm.isUnique === 1 ? '唯一索引' : '不建唯一索引'" @update:model-value="fieldForm.isUnique = $event ? 1 : 0" />
      </Field>
      <Field label="必填" group>
        <Switch :model-value="fieldForm.isRequired === 1" :label="fieldForm.isRequired === 1 ? '生成校验' : '不校验'" @update:model-value="fieldForm.isRequired = $event ? 1 : 0" />
      </Field>
      <Field label="列表显示" group>
        <Switch :model-value="fieldForm.isList === 1" :label="fieldForm.isList === 1 ? '显示' : '隐藏'" @update:model-value="fieldForm.isList = $event ? 1 : 0" />
      </Field>
      <Field label="作为查询条件" group>
        <Switch :model-value="fieldForm.isQuery === 1" :label="fieldForm.isQuery === 1 ? '加入查询区' : '不加入'" @update:model-value="fieldForm.isQuery = $event ? 1 : 0" />
      </Field>
      <Field v-if="fieldForm.isQuery === 1" label="查询方式">
        <Select v-model="fieldForm.queryType" :options="meta.queryTypes.map(item => ({ label: `${item.label}（${item.operator}）`, value: item.code }))" />
      </Field>
      <Field label="新增表单出现" group>
        <Switch :model-value="fieldForm.isInsert === 1" :label="fieldForm.isInsert === 1 ? '出现' : '不出现'" @update:model-value="fieldForm.isInsert = $event ? 1 : 0" />
      </Field>
      <Field label="编辑表单出现" group>
        <Switch :model-value="fieldForm.isEdit === 1" :label="fieldForm.isEdit === 1 ? '出现' : '不出现'" @update:model-value="fieldForm.isEdit = $event ? 1 : 0" />
      </Field>
      <Field label="字段顺序">
        <TextInput v-model="fieldForm.sortOrder" type="number" />
      </Field>
      <Field label="字段说明" span>
        <TextInput v-model="fieldForm.fieldDesc" placeholder="给配置的人看的说明，不生成到代码里" :maxlength="256" />
      </Field>
    </FormDialog>
  </ListPage>
</template>

<style scoped>
.flag-row {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
