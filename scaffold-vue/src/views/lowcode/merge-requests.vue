<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button, DataTable, Drawer, Field, Select, Tag, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { ListPage, PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { platformApi, type MergeRequestDetail, type MergeRequestRow, type ProjectOption } from '@/api'
import { useListPage } from '@yuanda-lowcode-ai/scaffold-ui/composables/list'
import { confirm } from '@yuanda-lowcode-ai/scaffold-ui/composables/confirm'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'

/* ============================================================
   合并请求（PR 8 第一刀，方案 §5.4）：看清「发布分支比主干多了什么、能不能自动合」。

   这一页只读 —— 没有「批准」也没有「合并」按钮，那是第二刀，要在人确实看过清单之后才加。
   重算一次会真连远端仓库（fetch 与 ls-remote），所以它是独立权限点，也放在动作区而不是每次查询都做：
   列表读的是库里上一次算好的结论，一眼列表不该变成一次网络风暴。
   ============================================================ */

/* 筛选两条：归属项目 + 待我审核（按项目负责人过滤）。请求的标题是平台按分支与文件数生成的，
   对它做关键字过滤没有读者 —— 与其摆一个后端不认的输入框，不如不放。 */
const list = useListPage<MergeRequestRow, { projectId: string; onlyMine: string }>({
  query: { projectId: '', onlyMine: '' },
  fetch: params => platformApi.mergeRequests.list(params) as Promise<any>,
})

const onlyMine = computed({
  get: () => list.query.onlyMine === '1',
  set: (value: boolean) => {
    list.query.onlyMine = value ? '1' : ''
    list.search()
  },
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
const merging = ref(false)

/* 一次只看一个文件的差异：整份补丁在几百个文件的请求里能有几 MB，
   抽屉首屏等它就是把「点开看一眼」变成一次下载。 */
const diffPath = ref<string | null>(null)
const diffText = ref('')
const diffLoading = ref(false)

async function openDiff(path: string) {
  if (!detail.value) {
    return
  }
  diffPath.value = path
  diffLoading.value = true
  try {
    diffText.value = (await platformApi.mergeRequests.fileDiff(detail.value.id, path)).diff
  } catch (error) {
    diffText.value = ''
    toastError(error, '读取差异失败')
  } finally {
    diffLoading.value = false
  }
}

function closeDiff() {
  diffPath.value = null
  diffText.value = ''
}

/** 能不能合由后端判（负责人或平台超管、状态待审、预检干净），这里只负责别让人点了才发现 */
const mergeable = computed(() => !!detail.value
  && detail.value.status === 0 && detail.value.checkStatus === 1)

const mergeHint = computed(() => {
  if (!detail.value) return ''
  if (detail.value.status === 1) return '主干已经前进了，这条请求算的是旧内容：先重算再合'
  if (detail.value.status !== 0) return '这条请求已经收口了'
  return detail.value.checkStatus === 2 ? '有冲突的文件必须先由人解掉，平台不替人取舍' : '还没预检，先重算一次'
})

async function doMerge() {
  if (!detail.value || !mergeable.value) {
    return
  }
  const row = detail.value
  const go = await confirm({
    title: '确认合并这条请求',
    message: `把 ${row.sourceBranch} 的 ${row.fileCount} 个文件合入 ${row.targetBranch} 并推送到远端。`
      + '这是平台唯一会写人工主干的动作，推上去之后只能靠一笔新的反向提交回退。',
    confirmText: '确认合并',
    danger: true,
  })
  if (!go) {
    return
  }
  merging.value = true
  try {
    detail.value = await platformApi.mergeRequests.merge(row.id)
    pushToast(`已合入 ${row.targetBranch}：${detail.value.checkMessage ?? ''}`, 'ok')
    await list.search()
  } catch (error) {
    toastError(error, '合并失败')
  } finally {
    merging.value = false
  }
}

async function openDetail(row: MergeRequestRow) {
  try {
    // 详情会重读一次远端：主干在人上一次看和这一次看之间前进过的话，这一条当场转陈旧
    detail.value = await platformApi.mergeRequests.detail(row.id)
    detailOpen.value = true
  } catch (error) {
    toastError(error, '读取合并请求失败')
  }
}

/** 按项目算：一个项目一份仓库，选哪个项目就算哪个 —— 这一笔会真连远端仓库 */
async function refresh() {
  if (!list.query.projectId) {
    pushToast('先选项目再生成：合并请求是按项目的仓库算的', 'info')
    return
  }
  refreshing.value = true
  try {
    const result = await platformApi.mergeRequests.refresh({ projectId: Number(list.query.projectId) })
    detail.value = result
    detailOpen.value = true
    pushToast(`已生成：${result.title}`, result.checkStatus === 2 ? 'danger' : 'ok')
    await list.search()
  } catch (error) {
    toastError(error, '重算合并请求失败')
  } finally {
    refreshing.value = false
  }
}

const CHANGE_TONE: Record<string, string> = { ADD: 'ok', MODIFY: 'info', DELETE: 'danger', RENAME: 'purple' }

/**
 * 按前缀给每一行分个类，颜色由 CSS 决定。
 * 后端给的就是 git 的 unified diff 原文：hunk 头（@@）、文件头（--- / +++）都要占自己的样式，
 * 不然一个删除的 "--- a/x.ts" 会被当成一整行删除标红。
 */
const diffLines = computed<Array<{ text: string, kind: string }>>(() => diffText.value.split('\n').map(line => ({
  text: line,
  kind: line.startsWith('@@') ? 'hunk'
    : line.startsWith('+++') || line.startsWith('---') ? 'head'
      : line.startsWith('diff ') || line.startsWith('index ') || line.startsWith('new file') ? 'meta'
        : line.startsWith('+') ? 'add'
          : line.startsWith('-') ? 'del'
            : 'ctx',
})))
</script>

<template>
  <ListPage
    title="合并请求"
    desc="平台只把生成物推到发布分支；主干上的每一笔都得人看过清单、确认过能不能自动合，才由合并进去。这一页没有单独的「新建」：选一个项目，点「生成合并请求」就是发起 —— 它按项目仓库现算一份清单与预检结论，再点一次是就地刷新同一条（同一分支对只留一条待审请求）。"
    table-title="合并请求列表"
    :list="list"
    :columns="columns"
  >
    <template #actions>
      <PermissionButton perm="lowcode:merge-request:sync" variant="primary" icon="refresh" :loading="refreshing" @click="refresh">
        生成合并请求
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
    <template #cell-branches="{ row }">
      <span class="mono">{{ row.sourceBranch }}</span>
      <span class="dim"> → </span>
      <span class="mono">{{ row.targetBranch }}</span>
    </template>

    <template #tools>
      <Button :variant="onlyMine ? 'primary' : 'secondary'" size="sm" icon="filter" @click="onlyMine = !onlyMine">
        待我审核
      </Button>
    </template>

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
    @close="detailOpen = false; closeDiff()"
  >
    <template v-if="detail">
      <dl class="mr-facts">
        <div><dt>分叉点</dt><dd class="mono">{{ detail.mergeBaseSha ?? '—' }}</dd></div>
        <div><dt>发布分支</dt><dd class="mono">{{ detail.sourceSha }}</dd></div>
        <div><dt>主干</dt><dd class="mono">{{ detail.targetSha }}</dd></div>
        <div><dt>结论</dt><dd>{{ detail.checkMessage ?? '—' }}</dd></div>
        <div v-if="detail.mergedBy"><dt>合入</dt><dd>{{ detail.mergedBy }} · {{ detail.mergedTime }}</dd></div>
      </dl>

      <!-- 点开一个文件就换成它的行内差异：GitHub 式「清单 → 单文件」，一次只拉一份补丁 -->
      <section v-if="diffPath" class="mr-diff">
        <header class="mr-diff-bar">
          <Button size="sm" variant="text" icon="chevronLeft" @click="closeDiff">返回清单</Button>
          <span class="mono">{{ diffPath }}</span>
        </header>
        <p v-if="diffLoading" class="mr-note">读取差异…</p>
        <p v-else-if="!diffText" class="mr-note">这个文件不在改动里（或清单和实际内容不一致）。</p>
        <pre v-else class="mr-diff-body"><div v-for="(line, i) in diffLines" :key="i" :class="'dl dl-' + line.kind">{{ line.text || ' ' }}</div></pre>
      </section>

      <DataTable v-else :columns="fileColumns" :data="detail.files" row-key="id" empty-text="清单是空的：主干已经包含发布分支的全部内容">
        <template #cell-path="{ row }">
          <button type="button" class="mr-file" :title="'查看 ' + row.path + ' 的差异'" @click="openDiff(row.path)">
            <span class="mono">{{ row.path }}</span>
            <span v-if="row.oldPath" class="mono dim">← {{ row.oldPath }}</span>
          </button>
          <Tag v-if="row.isConflict" tone="danger" dot>要人解</Tag>
        </template>
        <template #cell-changeType="{ row }"><Tag :tone="CHANGE_TONE[row.changeType] ?? 'neutral'">{{ row.changeType }}</Tag></template>
        <template #cell-lines="{ row }"><span class="tnum">+{{ row.additions }} −{{ row.deletions }}</span></template>
      </DataTable>

      <footer class="mr-foot">
        <p class="mr-note">
          冲突文件只列出来，谁赢不替人决定；主干前进过就先重算，让人看的永远是最新那一份。
        </p>
        <PermissionButton
          v-if="detail.status === 0"
          perm="lowcode:merge-request:merge"
          variant="primary"
          :disabled="!mergeable"
          :title="mergeable ? '把这条请求合入 ' + detail.targetBranch : mergeHint"
          :loading="merging"
          @click="doMerge"
        >
          确认合并
        </PermissionButton>
      </footer>
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

.mr-foot {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  margin-top: 6px;
}

.mr-foot .mr-note {
  flex: 1;
  min-width: 0;
  margin: 0;
}

.mr-file {
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.mr-file:hover {
  color: var(--blue);
  text-decoration: underline;
}

.mr-diff-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.mr-diff-body {
  margin: 0;
  padding: 6px 0;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  background: var(--surface);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.55;
}

/* unified diff 按前缀着色；--- / +++ 两行是文件头，不能跟着标成整行删除。
   绿与红都取底座语义色那一档（tag 的 ok / danger 用的就是它们），不在页面里另配一套色 */
.dl {
  padding: 0 10px;
  white-space: pre;
}

.dl-add {
  background: var(--lime-soft);
  color: var(--leaf-text);
}

.dl-del {
  background: var(--red-soft);
  color: var(--red-text);
}

.dl-hunk {
  background: var(--blue-soft);
  color: var(--ink-muted);
}

.dl-head {
  color: var(--ink);
  font-weight: 620;
}

.dl-meta {
  color: var(--ink-faint);
}

.dl-ctx {
  color: var(--ink-muted);
}
</style>
