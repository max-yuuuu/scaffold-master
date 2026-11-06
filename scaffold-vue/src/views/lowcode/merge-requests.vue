<script setup lang="ts">
import { computed, ref } from 'vue'
import { Button, Drawer, Field, Select, Tag, type Column } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
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

/* 详情这一跳会 fetch + ls-remote + merge-base + 逐文件 diff + 三路合并预检，真机上实测 8.7 秒。
   转圈只给被点的那一行：一屏几十行，全局 loading 会让别的行看起来也能点。 */
const detailLoadingId = ref<number | null>(null)

/* 风琴式清单：一行展开就是它的行内差异，一次只开一个。
   差异按路径缓存，收起再展开不必重新拉 —— 审核时反复回看同两三个文件是常态。 */
const expanded = ref<string | null>(null)
const diffCache = ref<Record<string, string>>({})
const diffLoading = ref(false)
const diffFailed = ref(false)

/** 每次打开抽屉都重来：上一条请求展开着哪一格不该带到这一条上 */
function resetAccordion() {
  expanded.value = null
  diffCache.value = {}
  diffFailed.value = false
}

async function toggleFile(path: string) {
  if (expanded.value === path) {
    expanded.value = null
    return
  }
  expanded.value = path
  if (diffCache.value[path] !== undefined) {
    return
  }
  if (!detail.value) {
    return
  }
  diffLoading.value = true
  diffFailed.value = false
  try {
    // 一次只拉一个文件：几百个文件的请求整份补丁能到几 MB，把「点开看一眼」变成下载就没法用了
    const result = await platformApi.mergeRequests.fileDiff(detail.value.id, path)
    diffCache.value = { ...diffCache.value, [path]: result.diff }
  } catch (error) {
    diffFailed.value = true
    toastError(error, '读取差异失败')
  } finally {
    diffLoading.value = false
  }
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
  detailLoadingId.value = row.id;
  try {
    // 详情会重读一次远端：主干在人上一次看和这一次看之间前进过的话，这一条当场转陈旧
    detail.value = await platformApi.mergeRequests.detail(row.id)
    resetAccordion()
    detailOpen.value = true
  } catch (error) {
    toastError(error, '读取合并请求失败')
  } finally {
    detailLoadingId.value = null;
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
    resetAccordion()
    detailOpen.value = true
    pushToast(`已生成：${result.title}`, result.checkStatus === 2 ? 'danger' : 'ok')
    await list.search()
  } catch (error) {
    toastError(error, '重算合并请求失败')
  } finally {
    refreshing.value = false
  }
}

/* ADD 绿 / MODIFY 蓝 / DELETE 红；RENAME 走中性灰 —— Tag 的 TONE_MAP 把 purple 归进 info，
   原来配的紫色渲染不出来，改名与修改会同色分不开 */
const CHANGE_TONE: Record<string, string> = { ADD: 'ok', MODIFY: 'info', DELETE: 'danger', RENAME: 'neutral' }

/**
 * 展开那一行的补丁按前缀分类，颜色由 CSS 决定。
 * 后端给的就是 git 的 unified diff 原文：hunk 头（@@）与文件头（--- / +++）都要占自己的类，
 * 否则一个删除的文件头会被标成整行删除。
 */
const expandedLines = computed<Array<{ text: string, kind: string }>>(() => {
  const patch = expanded.value ? diffCache.value[expanded.value] ?? '' : ''
  return patch.split('\n').map(line => ({
    text: line,
    kind: line.startsWith('@@') ? 'hunk'
      : line.startsWith('+++') || line.startsWith('---') ? 'head'
        : line.startsWith('diff ') || line.startsWith('index ') || line.startsWith('new file') ? 'meta'
          : line.startsWith('+') ? 'add'
            : line.startsWith('-') ? 'del'
              : 'ctx',
  }))
})
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
        <PermissionButton perm="lowcode:merge-request:list" icon-only icon="eye" label="看清单" :loading="detailLoadingId === row.id" @click="openDetail(row)" />
      </div>
    </template>
  </ListPage>

  <Drawer
    :open="detailOpen"
    :title="detail?.title ?? '合并请求'"
    :sub="detail ? `${detail.sourceBranch} → ${detail.targetBranch} · ${detail.checkStatusText}` : ''"
    :width="720"
    @close="detailOpen = false; resetAccordion()"
  >
    <template v-if="detail">
      <dl class="mr-facts">
        <div><dt>分叉点</dt><dd class="mono">{{ detail.mergeBaseSha ?? '—' }}</dd></div>
        <div><dt>发布分支</dt><dd class="mono">{{ detail.sourceSha }}</dd></div>
        <div><dt>主干</dt><dd class="mono">{{ detail.targetSha }}</dd></div>
        <div><dt>结论</dt><dd>{{ detail.checkMessage ?? '—' }}</dd></div>
        <div v-if="detail.mergedBy"><dt>合入</dt><dd>{{ detail.mergedBy }} · {{ detail.mergedTime }}</dd></div>
      </dl>

      <!-- 风琴清单：行头 sticky，下一个文件的行头会把当前这个顶上去（与 GitHub 的文件区同一行为） -->
      <div class="mr-files">
        <p v-if="!detail.files.length" class="mr-note">清单是空的：主干已经包含发布分支的全部内容。</p>
        <article v-for="file in detail.files" :key="file.id" class="mr-file">
          <button
            type="button"
            class="mr-file-head"
            :aria-expanded="expanded === file.path"
            @click="toggleFile(file.path)"
          >
            <Icon class="mr-chev" :class="{ 'is-open': expanded === file.path }" name="chevronRight" :size="12" />
            <span class="nm mono">{{ file.path }}<span v-if="file.oldPath" class="dim">← {{ file.oldPath }}</span></span>
            <span class="col-flag">
              <Tag v-if="file.isConflict" tone="danger" dot>要人解</Tag>
            </span>
            <span class="col-type">
              <Tag :tone="CHANGE_TONE[file.changeType] ?? 'neutral'">{{ file.changeType }}</Tag>
            </span>
            <span class="col-num tnum add">+{{ file.additions }}</span>
            <span class="col-num tnum del">−{{ file.deletions }}</span>
          </button>
          <div v-if="expanded === file.path" class="mr-diff">
            <p v-if="diffLoading" class="mr-diff-empty">读取差异…</p>
            <p v-else-if="diffFailed" class="mr-diff-empty">这个文件的差异没取到，收起再展开可以重试。</p>
            <p v-else-if="!diffCache[file.path]" class="mr-diff-empty">没有拿到这份补丁。</p>
            <pre v-else-if="expandedLines.length" class="mr-diff-body"><div v-for="(line, i) in expandedLines" :key="i" :class="['dl', 'dl-' + line.kind]">{{ line.text || ' ' }}</div></pre>
            <p v-else class="mr-diff-empty">这个文件不在改动里（二进制或空补丁）。</p>
          </div>
        </article>
      </div>
    </template>

    <template #footer>
      <p class="mr-note">
        冲突文件只列出来，谁赢不替人决定；主干前进过就先重算，让人看的永远是最新那一份。
      </p>
      <PermissionButton
        v-if="detail && detail.status === 0"
        perm="lowcode:merge-request:merge"
        variant="primary"
        :disabled="!mergeable"
        :title="mergeable ? '把这条请求合入 ' + detail.targetBranch : mergeHint"
        :loading="merging"
        @click="doMerge"
      >
        确认合并
      </PermissionButton>
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

.mr-files {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--r-xs);
  /* clip 不是 hidden：hidden 会把自己变成一层滚动容器，行头的 sticky 就以它为参照，
     而它自己不滚动，于是行头根本钉不起来。clip 只裁圆角，滚动参照仍是抽屉体。 */
  overflow: clip;
}

.mr-files > .mr-note {
  margin: 0;
  padding: 10px;
}

.mr-file {
  border-top: 1px solid var(--line);
}

.mr-file:first-child {
  border-top: 0;
}

/* 行头 sticky：滚动读差异时当前文件的标题钉在抽屉体顶部，下一个文件的行头滑上来把它顶掉。
   这是 sticky 相对各自父块的自然行为，与 GitHub 的文件区同一套，不需要 JS 算位置。
   top 取负是因为 sticky 钉的是滚动容器的内边距下沿：.drawer-body 有 14px 上内边距，
   top:0 会让行头停在下面 14px，差异行从这条缝里穿过看着就像被上面那块内容遮住了。
   阴影落在紧邻的那条 1px 分隔线上，钉住时把从下面滑过去的差异行切开，平时看不出来。
   列宽写死成 grid：清单是一列一列对着读的。「要人解」只有冲突行才有，不给它自己的列，
   后面状态与增删数会逐行左右跳；数字右对齐 + .tnum 等宽数位，+167 与 +9 的个位才对得上。 */
.mr-file-head {
  position: sticky;
  top: -14px;
  z-index: 2;
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) 70px 74px 46px 46px;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  border: 0;
  background: var(--surface-solid);
  box-shadow: 0 1px 0 var(--line);
  color: var(--ink);
  cursor: pointer;
  font: inherit;
  text-align: left;
}

/* hover 只能加一层背景图：--hover-bg 是半透明的，直接换成它会击穿这层不透明底，
   钉住时下面滑过的差异行会透上来 */
.mr-file-head:hover {
  background-image: linear-gradient(var(--hover-bg), var(--hover-bg));
}

.mr-file-head .nm {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}

.col-flag,
.col-type {
  display: flex;
  align-items: center;
  min-width: 0;
}

.col-num {
  font-size: 11.5px;
  text-align: right;
}

.col-num.add {
  color: var(--leaf-text);
}

.col-num.del {
  color: var(--red-text);
}

.mr-chev {
  flex: none;
  color: var(--ink-faint);
  transition: transform .16s cubic-bezier(.32, .72, 0, 1);
}

.mr-chev.is-open {
  transform: rotate(90deg);
}

.mr-diff {
  border-top: 1px solid var(--line);
  overflow: hidden;
}

.mr-diff-empty {
  margin: 0;
  padding: 10px;
  color: var(--ink-muted);
  font-size: 12px;
}

.mr-diff-body {
  margin: 0;
  padding: 6px 0;
  background: var(--surface);
  overflow-x: auto;
  font-family: var(--font-mono);
  font-size: 11.5px;
  line-height: 1.55;
}

/* unified diff 按前缀着色；--- / +++ 是文件头，不能跟着标成整行删除。
   绿与红取底座语义色那一档（tag 的 ok / danger 用的就是它们），不在页面里另配一套色 */
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
