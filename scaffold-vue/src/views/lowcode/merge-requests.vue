<script setup lang="ts">
import { computed, ref } from 'vue'
import { DataTable, Drawer, Field, Select, Tag, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { platformApi, type MergeRequestDetail, type MergeRequestRow, type ProjectOption } from '@/api'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'

/* ============================================================
   合并请求（PR 8 第一刀，方案 §5.4）：看清「发布分支比主干多了什么、能不能自动合」。

   这一页只读 —— 没有「批准」也没有「合并」按钮，那是第二刀，要在人确实看过清单之后才加。
   重算一次会真连远端仓库（fetch 与 ls-remote），所以它是独立权限点，也放在动作区而不是每次查询都做：
   列表读的是库里上一次算好的结论，一眼列表不该变成一次网络风暴。
   ============================================================ */

/* 筛选只有「归属项目」一条：一份仓库一个项目，请求的标题是平台按分支与文件数生成的，
   对它做关键字过滤没有读者 —— 与其摆一个后端不认的输入框，不如不放。 */
const list = useListPage<MergeRequestRow, { projectId: string }>({
  query: { projectId: '' },
  fetch: params => platformApi.mergeRequests.list(params) as Promise<any>,
})

const columns: Column[] = [
  { key: 'title', title: '请求', minWidth: 220 },
  { key: 'branches', title: '分支', minWidth: 200 },
  { key: 'fileCount', title: '文件', width: 78 },
  { key: 'conflictCount', title: '冲突', width: 78 },
  { key: 'checkStatusText', title: '预检', width: 106 },
  { key: 'statusText', title: '状态', width: 88 },
  { key: 'checkTime', title: '算于', width: 150 },
  { key: 'actions', title: '操作', width: 84, className: 'col-actions' },
]

const fileColumns: Column[] = [
  { key: 'path', title: '文件', minWidth: 260 },
  { key: 'changeType', title: '档位', width: 78 },
  { key: 'lines', title: '增删', width: 108 },
]

const projects = ref<ProjectOption[]>([])
platformApi.projects.options().then(rows => {
  projects.value = rows
}).catch(() => {
  // 选项拉不到不影响看已经算好的请求：筛选框空着，人 still 能进详情
})

const projectOptions = computed(() => projects.value.map(item => ({ label: item.projectName, value: String(item.id) })))

const detail = ref<MergeRequestDetail | null>(null)
const detailOpen = ref(false)
const refreshing = ref(false)

async function openDetail(row: MergeRequestRow) {
  try {
    // 详情会重读一次远端：主干在人上一次看和这一次看之间前进过的话，这一条当场转陈旧
    detail.value = await platformApi.mergeRequests.detail(row.id)
    detailOpen.value = true
  } catch (error) {
    toastError(error, '读取合并请求失败')
  }
}

/** 重算按项目走：一个项目一份仓库，选哪个项目就算哪个 */
async function refresh() {
  if (!list.query.projectId) {
    pushToast('先选项目再重算：合并请求是按项目的仓库算的', 'info')
    return
  }
  refreshing.value = true
  try {
    const result = await platformApi.mergeRequests.refresh({ projectId: Number(list.query.projectId) })
    detail.value = result
    detailOpen.value = true
    pushToast(`已重算：${result.title}`, result.checkStatus === 2 ? 'danger' : 'ok')
    await list.search()
  } catch (error) {
    toastError(error, '重算合并请求失败')
  } finally {
    refreshing.value = false
  }
}

const CHANGE_TONE: Record<string, string> = { ADD: 'ok', MODIFY: 'info', DELETE: 'danger', RENAME: 'purple' }
</script>

<template>
  <ListPage
    title="合并请求"
    desc="平台只把生成物推到发布分支；主干上的每一笔都得人看过清单、确认过能不能自动合，才由合并进去。"
    table-title="合并请求列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="lowcode:merge-request:sync" variant="primary" icon="refresh" :loading="refreshing" @click="refresh">
        重算所选项目
      </PermissionButton>
    </template>

    <template #search>
      <Field label="归属项目">
        <Select v-model="list.query.projectId" :options="projectOptions" placeholder="全部项目" clearable @update:model-value="list.search()" />
      </Field>
    </template>

    <template #cell-fileCount="{ row }"><span class="tnum">{{ row.fileCount }}</span></template>
    <template #cell-conflictCount="{ row }">
      <span v-if="row.conflictCount" class="tnum danger">{{ row.conflictCount }}</span>
      <span v-else class="tnum soft">0</span>
    </template>
    <template #cell-checkStatusText="{ row }">
      <Tag :tone="row.checkStatus === 2 ? 'danger' : 'ok'" dot>{{ row.checkStatusText }}</Tag>
    </template>
    <template #cell-statusText="{ row }">
      <Tag :tone="row.status === 1 ? 'warn' : row.status === 0 ? 'info' : 'neutral'" dot>{{ row.statusText }}</Tag>
    </template>
    <template #cell-checkTime="{ row }"><span class="tnum soft">{{ row.checkTime ?? '—' }}</span></template>

    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="lowcode:merge-request:list" icon-only icon="eye" label="看清单" @click="openDetail(row)" />
      </div>
    </template>
  </ListPage>

  <Drawer
    :open="detailOpen"
    :title="detail?.title ?? '合并请求'"
    :sub="detail ? `${detail.sourceBranch} → ${detail.targetBranch} · ${detail.checkStatusText}` : ''"
    :width="720"
    @close="detailOpen = false"
  >
    <template v-if="detail">
      <dl class="mr-facts">
        <div><dt>分叉点</dt><dd class="mono">{{ detail.mergeBaseSha ?? '—' }}</dd></div>
        <div><dt>发布分支</dt><dd class="mono">{{ detail.sourceSha }}</dd></div>
        <div><dt>主干</dt><dd class="mono">{{ detail.targetSha }}</dd></div>
        <div><dt>结论</dt><dd>{{ detail.checkMessage ?? '—' }}</dd></div>
      </dl>

      <DataTable :columns="fileColumns" :data="detail.files" row-key="id" empty-text="清单是空的：主干已经包含发布分支的全部内容">
        <template #cell-path="{ row }">
          <span class="mono">{{ row.path }}</span>
          <span v-if="row.oldPath" class="mono dim">← {{ row.oldPath }}</span>
          <Tag v-if="row.isConflict" tone="danger" dot>要人解</Tag>
        </template>
        <template #cell-changeType="{ row }"><Tag :tone="CHANGE_TONE[row.changeType] ?? 'neutral'">{{ row.changeType }}</Tag></template>
        <template #cell-lines="{ row }"><span class="tnum">+{{ row.additions }} −{{ row.deletions }}</span></template>
      </DataTable>

      <p class="mr-note">
        这一刀的清单只说明「差了什么」，不做合并：冲突文件列出来了，谁赢还是要人决定，
        平台不替人选。确认后合进主干是下一刀的动作。
      </p>
    </template>
  </Drawer>
</template>

<style scoped>
.mr-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0 0 14px;
}

.mr-facts dt {
  color: var(--ink-faint);
  font-size: 11.5px;
}

.mr-facts dd {
  margin: 2px 0 0;
  color: var(--ink);
  font-size: 12.5px;
  overflow-wrap: anywhere;
}

.mono {
  font-family: var(--font-mono);
  font-size: 12px;
}

.dim {
  margin-left: 6px;
  color: var(--ink-faint);
}

.danger {
  color: var(--red);
}

.mr-note {
  margin: 14px 0 0;
  color: var(--ink-muted);
  font-size: 12px;
  line-height: 1.6;
}
</style>
