<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { Button } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { appearance } from '@yuanda-lowcode-ai/scaffold-ui/composables/appearance'
import { floatBarEnter, floatBarLeave } from '@yuanda-lowcode-ai/scaffold-ui/composables/motion'
import { platformApi, type FieldRow, type PageDetail } from '@/api'
import { createSession, provideSession, type DesignerSession } from './session.ts'
import DesignerTopbar from './DesignerTopbar.vue'
import DesignerWorkspace from './DesignerWorkspace.vue'
import DesignerCommandDock from './DesignerCommandDock.vue'
import SchemaDrawer from './SchemaDrawer.vue'
import PublishDialog from './PublishDialog.vue'

/**
 * 页面设计器的路由入口（`/lowcode/designer/:页面 ID`）。
 *
 * 它挂在 AppShell 之外：整个标签页就是设计器，没有导航轨也没有控制台顶栏。
 * 地址带的是页面 ID 而不是查询参数 —— 设计器是一份要放那里反复改的长活儿，
 * 复制链接交给别人要能一眼看出这是哪一页。
 */
const route = useRoute()
const router = useRouter()

const session = shallowRef<DesignerSession | null>(null)
const page = ref<PageDetail | null>(null)
const fields = ref<FieldRow[]>([])
const failed = ref('')

provideSession(session)

async function load() {
  const id = Number(route.params.id)
  if (!Number.isInteger(id) || id <= 0) {
    failed.value = '地址里的页面 ID 读不出来，请从页面列表点「设计」进来'
    return
  }
  try {
    const detail = await platformApi.pages.detail(id)
    const rows = await platformApi.models.fields(detail.modelId)
    page.value = detail
    fields.value = rows
    session.value = createSession(detail, rows)
  } catch (error) {
    // 打不开就留在原地说明原因，不悄悄跳走：人刚点的是「设计」，跳回列表会让他以为是自己没点到
    failed.value = (error as Error).message || '打开设计器失败'
    toastError(error, '打开设计器失败')
  }
}

function reload() {
  // 「放弃改动」与发布后回读都走这条路：重新按库里那份装配会话，撤销栈与脏计数一起归零
  session.value = null
  void load()
}

function goBack() {
  router.push('/lowcode/page')
}

/** 深浅色写进平台那一份 appearance：设计器和控制台共用同一个偏好，切一次不会两边不一致 */
function toggleTheme() {
  const dark = appearance.theme === 'dark' || (appearance.theme === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches)
  appearance.theme = dark ? 'light' : 'dark'
}

function onKeydown(event: KeyboardEvent) {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    session.value?.openOverlay('command')
  }
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  void load()
})
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))

function discardChanges() {
  reload()
  pushToast('已还原到库里那一份设计', 'info')
}
</script>

<template>
  <div class="stage">
    <div class="app-frame dz-frame">
      <p v-if="failed" class="dz-fail">{{ failed }}</p>
      <template v-else-if="session && page">
        <DesignerTopbar
          :project-name="page.projectName ?? ''"
          :page-name="page.pageName"
          :current-version="page.currentVersion"
          :publish-status="page.publishStatus"
          :can-undo="session.canUndo.value"
          :can-redo="session.canRedo.value"
          :scrolled="session.scrolled.value"
          :preview-mode="session.previewMode.value"
          :saving="session.saving.value"
          @close="goBack"
          @command="session.openOverlay('command')"
          @undo="session.act(() => session!.store.undo())"
          @redo="session.act(() => session!.store.redo())"
          @schema="session.openOverlay('schema')"
          @theme="toggleTheme"
          @preview="session.togglePreview()"
          @save="session.save()"
          @publish="session.openOverlay('publish')"
        />

        <DesignerWorkspace />

        <DesignerCommandDock
          :open="session.overlay.value === 'command'"
          @close="session.closeOverlay()"
        />
        <SchemaDrawer
          :open="session.overlay.value === 'schema'"
          :json="session.store.json()"
          @close="session.closeOverlay()"
        />
        <PublishDialog
          :open="session.overlay.value === 'publish'"
          :page="page"
          :session="session"
          @close="session.closeOverlay()"
          @published="reload"
        />

        <!-- 未保存改动常驻底部浮条：基线是库里那一份，比对的是设计原文，刷新就回到基线 -->
        <Transition :css="false" @enter="floatBarEnter" @leave="floatBarLeave">
          <div v-if="session.dirty.value" class="float-bar" role="region" aria-label="未保存改动">
            <span class="float-bar-count">画布上有未保存改动</span>
            <span class="float-bar-sep" aria-hidden="true" />
            <Button variant="text" size="sm" class="is-danger" @click="discardChanges">放弃改动</Button>
            <Button variant="text" size="sm" :loading="session.saving.value" @click="session.save()">立即保存草稿</Button>
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

<style scoped>
/* 机身沿用外壳的 .app-frame 材质（10px 深色边 + 38px 圆角 + 两团漫射光），
   只把它的两列导航轨换成「顶栏 + 工作区」两行 */
.dz-frame {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dz-fail {
  margin: auto;
  color: var(--ink-muted);
  font-size: 13px;
}
</style>
