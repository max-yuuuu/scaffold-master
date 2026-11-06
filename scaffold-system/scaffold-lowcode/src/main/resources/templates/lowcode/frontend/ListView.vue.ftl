<script setup lang="ts">
import { reactive, ref } from 'vue'
import { <#list pageImports.ui as c>${c}<#if c?has_next>, </#if></#list>, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { <#list pageImports.business as c>${c}<#if c?has_next>, </#if></#list> } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'
import { ${apiVar}, type ${className}Row, type ${className}WriteRequest } from '@/api/${module}/${resourceSlug}'

/* ============================================================
   ${pageTitle}。由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖。
   版式固定走 ListPage + useListPage（查询 / 重置 / 分页 / 导出 / 行操作都是公共能力），
   页面只声明字段、列与动作。要加区块写到 src/views/${module}/overrides/ 里；
   要长期人工接管这一页，就把它改名并让发布不再产出它 —— 现在它每次发布都会被整文件覆盖。
   ============================================================ */

type ${className}Query = {
<#list queryParams as q>
  ${q.name}: ${q.tsType}<#if q.tsType == 'number'> | null</#if>
</#list>
}

const list = useListPage<${className}Row, ${className}Query>({
  query: {
<#list queryParams as q>
    ${q.name}: <#if q.tsType == 'number'>null<#else>''</#if>,
</#list>
  },
  fetch: params => ${apiVar}.list(params),
<#if hasExport>
  exporter: params => ${apiVar}.export(params),
  exportName: '${exportName}',
</#if>
})

const columns: Column[] = [
<#list tableColumns as f>
  { key: '${f.javaField}', title: '${f.descJs}'<#if f.colWidth??>, width: ${f.colWidth}<#else>, minWidth: ${f.colMinWidth}</#if><#if f.colAlign??>, align: '${f.colAlign}'</#if> },
</#list>
<#if audit>
  { key: 'createTime', title: '创建时间', width: 160 },
</#if>
<#-- 设计没放行任何行操作时，连「操作」这一列一起不出：出一个 112px 的表头加每行一格空白，
     读起来像「这里有入口但坏了」，而事实是「这个页面不提供行内动作」 -->
<#if rowButtons?size gt 0>
  { key: 'actions', title: '操作', width: 112, className: 'col-actions' },
</#if>
]

/* ── 新增 / 编辑 ── */
type ${className}Form = {
<#list formFields as f>
  ${f.javaField}: ${f.tsType}<#if f.tsType == 'number' || f.nullableTs> | null</#if>
</#list>
}

/** 正在编辑哪一行的主键；空着就是新增。不借表单里某个字段的值来猜：主键可能由人填（业务编号），也可能根本不进表单（自增） */
const editing = ref<number | null>(null)
const dialog = ref(false)
const saving = ref(false)
const form = reactive<${className}Form>({
<#list formFields as f>
  ${f.javaField}: <#if f.tsType == 'number'>null<#else>''</#if>,
</#list>
})
const errors = ref<Record<string, string>>({})

function emptyForm(): ${className}Form {
  return {
<#list formFields as f>
    ${f.javaField}: <#if f.tsType == 'number'>null<#else>''</#if>,
</#list>
  }
}

function openCreate() {
  editing.value = null
  Object.assign(form, emptyForm())
  errors.value = {}
  dialog.value = true
}

/**
 * 编辑前先取整行：列表行只带勾选进列表的字段，直接拿 row 填表单，
 * 保存时会把没显示出来的字段当空值覆盖掉。
 */
async function openEdit(row: ${className}Row) {
  try {
    const detail = await ${apiVar}.detail(row.${primaryField.javaField})
    editing.value = row.${primaryField.javaField}
    Object.assign(form, emptyForm(), detail)
    errors.value = {}
    dialog.value = true
  } catch (error) {
    toastError(error, '${modelNameJs}加载失败')
  }
}

async function submit() {
  errors.value = validate(form, {
<#list formFields as f>
<#if f.inputRequired>
    ${f.javaField}: [required('${f.inputVerb}${f.descJs}')],
</#if>
</#list>
  })
  if (Object.keys(errors.value).length) return
  const payload: ${className}WriteRequest = {
<#list formFields as f>
<#assign value>form.${f.javaField}<#if f.tsType == 'string' && f.nullableTs>?.trim() ?? null<#elseif f.valueTrim>.trim()</#if></#assign>
    ${f.javaField}: ${value},<#-- 可空的文本字段用 ?.trim() ?? null：空值就传 null，不是空字符串 -->
</#list>
  }
  saving.value = true
  try {
    if (editing.value !== null) await ${apiVar}.update(editing.value, payload)
    else await ${apiVar}.create(payload)
    pushToast(editing.value !== null ? '${modelNameJs}已更新' : '${modelNameJs}已创建')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '${modelNameJs}保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: ${className}Row) {
  const ok = await confirm({
    title: '删除${modelNameJs}',
    message: '删除后平台不会自动回滚已发布到仓库的代码。确认删除这条${modelNameJs}吗？',
    confirmText: '删除',
    danger: true,
  })
  if (!ok) return
  try {
    await ${apiVar}.remove(row.${primaryField.javaField})
    pushToast('${modelNameJs}已删除')
    await list.load()
  } catch (error) {
    toastError(error, '${modelNameJs}删除失败')
  }
}
</script>

<template>
  <ListPage
    title="${pageTitle}"
    desc="${pageDesc}"
    table-title="${modelName}列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
<#list toolbarButtons as b>
      <PermissionButton perm="${b.perm}"<#if b.variant??> variant="${b.variant}"</#if> icon="${b.icon}" @click="${b.click}">${b.label}</PermissionButton>
</#list>
    </template>
<#if queryParams?size gt 0>

    <template #search>
<#list queryParams as q>
      <Field label="${q.desc}"<#if q.name?starts_with("keyword")> wide</#if>>
<#if q.formControl == 'dict'>
        <DictSelect v-model="list.query.${q.name}" type="${q.dictType}" all-label="全部" />
<#elseif q.formControl == 'number'>
        <NumberInput v-model="list.query.${q.name}"<#if q.numberPrecision??> :precision="${q.numberPrecision}"</#if><#if q.placeholder??> placeholder="${q.placeholder}"</#if> />
<#elseif q.formControl == 'date'>
        <DateInput v-model="list.query.${q.name}" type="date" />
<#elseif q.formControl == 'datetime'>
        <DateInput v-model="list.query.${q.name}" type="datetime" />
<#elseif q.formControl == 'select'>
        <Select v-model="list.query.${q.name}" :options="[]" />
<#else>
        <TextInput v-model="list.query.${q.name}"<#if q.placeholder??> placeholder="${q.placeholder}"</#if> clearable @enter="list.search()" />
</#if>
      </Field>
</#list>
    </template>
</#if>
<#list tableColumns as f>
<#if f.render == 'tag'>
    <template #cell-${f.javaField}="{ row }"><DictTag type="${f.dictType}" :value="row.${f.javaField}" /></template>
<#elseif f.render == 'money'>
    <template #cell-${f.javaField}="{ row }"><span class="tnum">{{ row.${f.javaField} ?? '—' }}</span></template>
<#elseif f.render == 'datetime'>
    <template #cell-${f.javaField}="{ row }"><span class="tnum soft">{{ row.${f.javaField} ?? '—' }}</span></template>
</#if>
</#list>
<#if audit>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime ?? '—' }}</span></template>
</#if>

<#if rowButtons?size gt 0>
    <template #cell-actions="{ row }">
      <div class="row-actions">
<#list rowButtons as b>
        <PermissionButton perm="${b.perm}" icon-only icon="${b.icon}" label="${b.label}"<#if b.danger> danger</#if> @click="${b.click}" />
</#list>
      </div>
    </template>
</#if>
  </ListPage>

  <FormDialog :open="dialog" :title="editing !== null ? '编辑${modelName}' : '新增${modelName}'" :saving="saving" @close="dialog = false" @submit="submit">
<#list formFields as f>
    <Field label="${f.desc}"<#if f.required> required</#if><#if f.formControl == 'textarea' || f.valueTrim> wide</#if> :error="errors.${f.javaField}">
<#if f.formControl == 'dict'>
      <DictSelect v-model="form.${f.javaField}" type="${f.dictType}" />
<#elseif f.formControl == 'textarea'>
      <Textarea v-model="form.${f.javaField}" :rows="3" />
<#elseif f.formControl == 'number'>
      <NumberInput v-model="form.${f.javaField}"<#if f.numberPrecision??> :precision="${f.numberPrecision}"</#if> />
<#elseif f.formControl == 'date'>
      <DateInput v-model="form.${f.javaField}" type="date" />
<#elseif f.formControl == 'datetime'>
      <DateInput v-model="form.${f.javaField}" type="datetime" />
<#elseif f.formControl == 'select'>
      <!-- 没挂字典的单选 / 多选：选项来源由项目侧决定，先在 overrides/ 里换成 Select 或挂上字典 -->
      <TextInput v-model="form.${f.javaField}" />
<#else>
      <TextInput v-model="form.${f.javaField}"<#if f.placeholder??> placeholder="${f.placeholder}"</#if> />
</#if>
    </Field>
</#list>
  </FormDialog>
</template>
