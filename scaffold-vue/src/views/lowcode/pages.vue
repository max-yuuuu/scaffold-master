<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Checkbox, DataTable, Drawer, Field, Pagination, Select, StatusTag, Tag, TextInput, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { FormDialog, ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { pattern, required, validate } from '@yuanda-lowcode-ai/scaffold-ui/utils/validate'
import { platformApi, type FieldRow, type PageDetail, type PageRow, type PageVersionFileRow, type PageVersionRow, type ProjectOption } from '@/api'
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

const route = useRoute()
const router = useRouter()

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
    await platformApi.pages.create({
      projectId: Number(form.projectId),
      modelCode: form.modelCode,
      pageCode: form.pageCode,
      pageName: form.pageName,
      design: design as unknown as Record<string, unknown>,
    })
    dialog.value = false
    await list.load()
    // 不在这里开新标签页：window.open 跟在 await 之后会被浏览器的弹窗拦截挡掉，
    // 人点了「确定」却什么都没发生比多一次点击更难解释
    pushToast('页面已创建，在列表点「设计」会在新标签页打开画布')
  } catch (error) {
    toastError(error, '新建页面失败')
  } finally {
    saving.value = false
  }
}

/* ── 画布：开在新的浏览器标签页里，列表这一层不整屏接管 ── */

/**
 * 设计器是一条独立的全屏路由（`/lowcode/designer/:页面 ID`），开在新的浏览器标签页里，
 * 而不是把当前这一屏换掉，也不再套一层导航轨与控制台顶栏。
 *
 * <p>画布是一份要反复改、改完放那里的长活儿：接管列表的话，人一边调设计一边看不到「还有哪些页面没发」，
 * 想看就得先退出设计器（未保存的改动还得指望它别丢）。开新标签页之后列表与设计稿并排放着。
 * 地址带的是页面 ID 而不是查询参数，复制一条 `/lowcode/designer/59` 就能把这一页的设计交给另一个人
 * 或另一台机器，别人打开也不用先猜「design 参数是干什么的」。
 */
function openDesignerInNewTab(id: number) {
  const { href } = router.resolve({ name: 'designer', params: { id: String(id) } })
  window.open(href, '_blank', 'noopener')
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

/* ── 版本历史与回滚（PR 9，方案 §5.4）────────────────────────────────
   回滚产出的是一笔新提交（新的 v = 当前 + 1，内容等于选的那一版），所以当前版自己不该出现在
   「回滚到这一版」的候选里 —— 那等于什么都不做，后端也会拒，这里只是不摆一个注定失败的按钮。
   文件级回滚要先拿那一版的产物清单：哪个路径归 B 档、哪个是 C 档扩展点，判据在后端
   （GeneratedPaths.tier），前端不重算 —— 重算就是第二个真相来源。 */

const versionsOpen = ref(false)
const versionsLoading = ref(false)
const versionsOf = ref<PageRow | null>(null)
const versions = ref<PageVersionRow[]>([])
const versionsTotal = ref(0)
const versionsPage = ref(1)
const VERSION_PAGE_SIZE = 10

/** 展开着的那一版：一次只开一版，风琴行为与合并请求的清单一致 */
const pickedVersion = ref<number | null>(null)
const pickedFiles = ref<PageVersionFileRow[]>([])
const checkedPaths = ref<string[]>([])
const filesLoading = ref(false)
const rolling = ref(false)

const versionColumns: Column[] = [
  { key: 'version', title: '版本', width: 76, align: 'right' },
  { key: 'publishType', title: '动作', width: 92 },
  { key: 'tagName', title: '版本标记', minWidth: 170 },
  { key: 'commitSha', title: '提交', width: 104 },
  { key: 'fileCount', title: '文件', width: 70, align: 'right' },
  { key: 'createBy', title: '发布人', width: 100 },
  { key: 'createTime', title: '时间', width: 150 },
  { key: 'actions', title: '操作', width: 150, className: 'col-actions' },
]

/* Tag 只有四种语气（warn 会归进 danger）：回滚不是出错，给它中性灰，红留给真正要人处理的东西 */
const PUBLISH_TONE: Record<string, string> = { CREATE: 'ok', UPDATE: 'info', ROLLBACK: 'neutral' }
const PUBLISH_TEXT: Record<string, string> = { CREATE: '首次发布', UPDATE: '再发布', ROLLBACK: '回滚产出' }

const currentVersion = computed(() => versions.value.reduce((max, row) => Math.max(max, row.version), 0))
const checkedCount = computed(() => checkedPaths.value.length)

async function openVersions(row: PageRow) {
  versionsOf.value = row
  versionsOpen.value = true
  pickedVersion.value = null
  pickedFiles.value = []
  checkedPaths.value = []
  await loadVersions(1)
}

async function loadVersions(page = versionsPage.value) {
  if (!versionsOf.value) return
  versionsLoading.value = true
  try {
    const result = await platformApi.pages.versions(versionsOf.value.id, { pageNum: page, pageSize: VERSION_PAGE_SIZE })
    versions.value = result.rows
    versionsTotal.value = Number(result.total)
    versionsPage.value = result.pageNum
  } catch (error) {
    toastError(error, '读取版本历史失败')
  } finally {
    versionsLoading.value = false
  }
}

/** 展开某一版：拿它的产物清单，勾出来的就是文件级回滚的范围 */
async function toggleFiles(row: PageVersionRow) {
  if (pickedVersion.value === row.version) {
    pickedVersion.value = null
    pickedFiles.value = []
    checkedPaths.value = []
    return
  }
  pickedVersion.value = row.version
  pickedFiles.value = []
  checkedPaths.value = []
  if (!versionsOf.value) return
  filesLoading.value = true
  try {
    const detail = await platformApi.pages.version(versionsOf.value.id, row.version)
    pickedFiles.value = detail.files
    // 默认全选 B 档：多数时候「只还原这一版里的这几个」是少数派操作，先给一个能直接按下去的形状
    checkedPaths.value = detail.files.filter(file => file.rollbackable).map(file => file.path)
  } catch (error) {
    toastError(error, '读取这一版的产物清单失败')
  } finally {
    filesLoading.value = false
  }
}

function togglePath(path: string, on: boolean) {
  checkedPaths.value = on
    ? [...new Set([...checkedPaths.value, path])]
    : checkedPaths.value.filter(item => item !== path)
}

/** 回滚只用到版本号，所以参数是 number 而不是整行：文件级那一格没有「行」可传 */
async function rollback(version: number, files?: string[]) {
  if (!versionsOf.value) return
  const scope = files ? `只还原选中的 ${files.length} 个文件` : `整页还原`
  const ok = await confirm({
    title: `回滚到 v${version}`,
    message: `${scope}：平台会往发布分支推一笔新提交（内容回到 v${version}，版本号记成 v${currentVersion.value + 1}），`
      + '不改历史、不碰主干。要让主干也回到这一版，还得在合并请求里把这笔新提交由人合进去。',
    confirmText: '确认回滚',
    danger: true,
  })
  if (!ok) return
  rolling.value = true
  try {
    const result = await platformApi.pages.rollback(versionsOf.value.id, { version, files })
    pushToast(result.message || `已回滚到 v${version}`, result.status === 1 ? 'ok' : 'info')
    pickedVersion.value = null
    pickedFiles.value = []
    checkedPaths.value = []
    await loadVersions(1)
    await list.load()
  } catch (error) {
    toastError(error, '回滚失败')
  } finally {
    rolling.value = false
  }
}

/** 文件级回滚：勾中的那几个 B 档路径 */
function rollbackChecked() {
  if (pickedVersion.value === null || checkedPaths.value.length === 0) return
  void rollback(pickedVersion.value, [...checkedPaths.value])
}
</script>

<template>
  <ListPage
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
        <PermissionButton perm="lowcode:page:edit" icon-only icon="edit" label="设计" @click="openDesignerInNewTab(row.id)" />
        <PermissionButton perm="lowcode:page:list" icon-only icon="clock" label="版本历史" @click="openVersions(row)" />
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

  <!-- 版本历史（PR 9 §5.4）：一版一行，回滚就是点其中一行。当前版不给「回滚到这一版」—— 那等于什么都不做 -->
  <Drawer
    :open="versionsOpen"
    :title="`版本历史 · ${versionsOf?.pageName ?? ''}`"
    :sub="versionsOf ? `${versionsOf.pageCode} · 共 ${versionsTotal} 版` : ''"
    :width="880"
    @close="versionsOpen = false"
  >
    <DataTable
      :columns="versionColumns"
      :data="versions"
      row-key="version"
      :loading="versionsLoading"
      empty-text="这个页面还没有发布成功过：没有版本，也就没有可回滚的内容"
    >
      <template #cell-version="{ row }">
        <span class="tnum">v{{ row.version }}</span>
        <Tag v-if="row.version === currentVersion" tone="info" dot>当前</Tag>
      </template>
      <template #cell-publishType="{ row }">
        <Tag :tone="PUBLISH_TONE[row.publishType ?? ''] ?? 'neutral'">{{ PUBLISH_TEXT[row.publishType ?? ''] ?? (row.publishType ?? '—') }}</Tag>
      </template>
      <template #cell-tagName="{ row }"><span class="mono">{{ row.tagName ?? '—' }}</span></template>
      <template #cell-commitSha="{ row }"><span class="mono" :title="row.commitSha">{{ row.commitSha.slice(0, 10) }}</span></template>
      <template #cell-fileCount="{ row }"><span class="tnum">{{ row.fileCount ?? '—' }}</span></template>
      <template #cell-createBy="{ row }">{{ row.createBy ?? '—' }}</template>
      <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime ?? '—' }}</span></template>
      <template #cell-actions="{ row }">
        <div class="row-actions">
          <PermissionButton
            v-if="row.version !== currentVersion"
            perm="lowcode:page:rollback"
            icon-only
            icon="undo"
            label="整页回滚到这一版"
            :loading="rolling"
            @click="rollback(row.version)"
          />
          <PermissionButton
            perm="lowcode:page:list"
            icon-only
            icon="sliders"
            :label="pickedVersion === row.version ? '收起文件清单' : '只还原部分文件'"
            @click="toggleFiles(row)"
          />
        </div>
      </template>
    </DataTable>

    <Pagination :total="versionsTotal" :page="versionsPage" :page-size="VERSION_PAGE_SIZE" @update:page="loadVersions" />

    <!-- 文件级回滚：清单与档位是后端按路径判出来的（GeneratedPaths.tier），前端不重算 ——
         重算就是第二个真相来源。C 档不给勾：平台不生成它，也就无权还原人写在里面的代码 -->
    <section v-if="pickedVersion !== null" class="rv-files">
      <p class="form-note">v{{ pickedVersion }} 的产物清单，勾中的 {{ checkedCount }} 个只还原这几个文件。</p>
      <p v-if="filesLoading" class="form-note">读取清单…</p>
      <ul v-else class="rv-list">
        <li v-for="file in pickedFiles" :key="file.path">
          <Checkbox
            :model-value="checkedPaths.includes(file.path)"
            :disabled="!file.rollbackable"
            :label="file.path"
            @update:model-value="(on: boolean) => togglePath(file.path, on)"
          />
          <Tag v-if="!file.rollbackable" tone="neutral" dot>C 档不还原</Tag>
        </li>
      </ul>
      <div v-if="!filesLoading" class="rv-actions">
        <PermissionButton
          perm="lowcode:page:rollback"
          variant="primary"
          icon="undo"
          :loading="rolling"
          :disabled="checkedCount === 0"
          :title="checkedCount === 0 ? '一个文件都没勾：整页回滚请点那一行的「整页回滚到这一版」' : ''"
          @click="rollbackChecked()"
        >
          还原选中的 {{ checkedCount }} 个文件
        </PermissionButton>
      </div>
    </section>

    <template #footer>
      <p class="form-note">
        回滚往发布分支推一笔新提交，不改历史也不碰主干；要让主干也回到这一版，还得在合并请求里由人确认。
      </p>
    </template>
  </Drawer>
</template>

<style scoped>
.form-note {
  margin: 2px 0 0;
  color: var(--ink-faint);
  font-size: 12px;
}

/* 文件级回滚那一块跟在版本表后面，不是一条独立弹窗：勾哪几个文件与「这是哪一版」要在同一屏里看 */
.rv-files {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  background: var(--surface);
}

.rv-list {
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rv-list li {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

/* 路径可能很长：让勾选框那一段自己截断，别把「C 档不还原」那颗标签挤出抽屉 */
.rv-list li :deep(.checkbox) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.rv-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
