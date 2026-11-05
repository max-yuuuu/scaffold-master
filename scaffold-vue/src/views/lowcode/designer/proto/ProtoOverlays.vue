<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button, Drawer } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { usePrototype } from './context.ts'
import { copyText } from '@yuanda-lowcode-ai/scaffold-ui/utils/clipboard'
import { pushToast } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { AVAILABLE_TRIGGERS_BY_KIND, EVENT_ACTION_OPTIONS, type EventActionType } from './data.ts'

/* 四类浮层共用一条焦点栈：Escape 关最上层，⌘K 唤起命令面板。
   遮罩 z-index 200、rgba(16,20,24,.26) + blur 3px，与原型同一层。 */
const proto = usePrototype()

const publishVersion = ref('v2.4.1-release')
const publishEnv = ref('生产环境（宁德三期 A/B/C 线）')
const publishDesc = ref('校准一次化成直通率读数台采样周期，新增压降超限隔离批次复核入口')
const drawerTrigger = ref('')
const drawerAction = ref<EventActionType>('showToast')
const drawerTarget = ref('')
const drawerEnabled = ref(true)

const draft = computed(() => proto.eventDrawer.value?.draft ?? null)

/** 打开抽屉时把草稿回填到控件上；直接改 draft 对象会让「取消」也生效，所以本地复制一份 */
watch(draft, (value) => {
  if (!value) return
  drawerTrigger.value = value.triggerCode
  drawerAction.value = value.actionType
  drawerTarget.value = value.targetDesc
  drawerEnabled.value = value.enabled
}, { immediate: true })

const triggerOptions = computed(() => {
  const state = proto.eventDrawer.value
  if (!state) return []
  return state.scope === 'node' && proto.selectedNode.value
    ? AVAILABLE_TRIGGERS_BY_KIND[proto.selectedNode.value.kind] || []
    : AVAILABLE_TRIGGERS_BY_KIND.page
})

const previewJson = computed(() => JSON.stringify({
  trigger: drawerTrigger.value,
  action: drawerAction.value,
  target: drawerTarget.value,
  enabled: drawerEnabled.value,
}, null, 2))

function saveDrawer() {
  const state = proto.eventDrawer.value
  if (!state) return
  const picked = triggerOptions.value.find(item => item.code === drawerTrigger.value) || { code: drawerTrigger.value, name: '点击组件时' }
  state.draft = { ...state.draft, triggerCode: picked.code, triggerName: picked.name, actionType: drawerAction.value, targetDesc: drawerTarget.value, enabled: drawerEnabled.value }
  proto.saveEventDrawer()
}

const schemaJson = computed(() => JSON.stringify({
  pageCode: 'mes_formation_release_station',
  version: '2.4.0',
  styleSystem: 'pearl-glass',
  nodes: proto.nodes.value,
}, null, 2))

const commandMatches = computed(() => {
  const key = proto.commandQuery.value.trim().toLowerCase()
  const items = [
    { code: 'cmd:insert-card', label: '向画布末尾插入「面板卡片」基础组件', run: () => { const m = MATERIAL_CARD; if (m) proto.insertMaterial(m, proto.nodes.value.length); proto.closeOverlay() } },
    { code: 'cmd:toggle-theme', label: '切换浅色 / 深色珍珠玻璃材质主题', run: () => { proto.pushToast('已切换主题（原型稿走平台外观设置）', 'info'); proto.closeOverlay() } },
    { code: 'cmd:open-schema', label: '打开页面完整 JSON Schema 抽屉', run: () => proto.openOverlay('schema') },
    { code: 'cmd:save-draft', label: '立即保存当前画布草稿并清零未保存计数', run: () => { proto.saveDraft(); proto.closeOverlay() } },
  ]
  return key ? items.filter(item => item.label.toLowerCase().includes(key)) : items
})

const MATERIAL_CARD = { id: 'mat-card', kind: 'card', name: '面板卡片', category: '布局容器', categoryTone: 'sky', code: 'Panel', desc: '带标题栏与内边距的标准内容面板' } as const

function copySchema() {
  void copyText(schemaJson.value).then(ok => pushToast(ok ? '结构代码已复制' : '浏览器拒绝了剪贴板写入', ok ? 'ok' : 'danger'))
}

function onScrimKey(event: KeyboardEvent) {
  if (event.key === 'Escape') proto.closeOverlay()
}
</script>

<template>
  <div v-if="proto.topOverlay.value" class="pf-scrim" tabindex="-1" @keydown="onScrimKey" @click.self="proto.closeOverlay()">
    <!-- 1. ⌘K 命令面板 -->
    <div v-if="proto.topOverlay.value === 'command'" class="pf-scrim is-center" style="background:none">
      <div class="pf-modal" @click.stop>
        <div class="pf-modal-head">
          <h2 class="pf-modal-title">快速指令与组件跳转</h2>
          <span class="pf-chip">ESC 关闭</span>
        </div>
        <div class="pf-modal-body" style="gap:8px">
          <input v-model="proto.commandQuery.value" class="pf-input" type="text" autofocus placeholder="输入指令名称，例如：插入面板卡片、打开 Schema...">
          <div class="pf-cmd-list">
            <button v-for="item in commandMatches" :key="item.code" type="button" class="pf-cmd-row" @click="item.run()">
              <span>{{ item.label }}</span><span class="pf-chip">{{ item.code }}</span>
            </button>
            <p v-if="!commandMatches.length" class="pf-cmd-empty">没有匹配的指令。</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 发布核准弹窗 -->
    <div v-else-if="proto.topOverlay.value === 'publish'" class="pf-modal" style="max-width:620px" @click.stop>
      <div class="pf-modal-head" style="border-bottom:0;padding-bottom:6px">
        <div>
          <h2 class="pf-modal-title">发布{{ proto.pageConfig.name }}</h2>
          <p class="pf-sub">发布后将同步更新宁德三期车间终端的页面布局与服务端权限规则</p>
        </div>
      </div>
      <div class="pf-modal-body">
        <div class="pf-modal-grid">
          <label class="pf-field"><span>发布版本号</span><input v-model="publishVersion" class="pf-input mono" type="text"></label>
          <label class="pf-field"><span>目标运行环境</span><input v-model="publishEnv" class="pf-input" type="text" readonly></label>
        </div>
        <label class="pf-field"><span>变更说明</span><input v-model="publishDesc" class="pf-input" type="text"></label>
      </div>
      <div class="pf-modal-foot">
        <button type="button" class="pf-btn is-text" @click="proto.closeOverlay()">取消</button>
        <button type="button" class="pf-btn is-primary" @click="proto.pushToast(`已按 ${publishVersion} 签发（静态稿不发接口）`); proto.closeOverlay()">确认签发上线</button>
      </div>
    </div>
  </div>

  <!-- 3–4. 两条抽屉走平台 Drawer：自己 Teleport 到 body、贴右边满高，不套在这层遮罩里 -->
  <Drawer
    :open="proto.topOverlay.value === 'schema'"
    title="页面低代码声明式结构（Schema）"
    sub="实时映射三栏工作台内所有节点的属性、令牌与 RBAC 权限标识"
    :width="560"
    @close="proto.closeOverlay()"
  >
    <pre class="pf-code">{{ schemaJson }}</pre>
    <template #footer>
      <span class="pf-chip is-lead">UTF-8 · JSON Schema v2</span>
      <Button variant="primary" size="sm" icon="copy" @click="copySchema">复制结构代码</Button>
    </template>
  </Drawer>

  <Drawer
    :open="proto.topOverlay.value === 'eventDrawer' && !!draft"
    :title="`${proto.eventDrawer.value?.mode === 'create' ? '新增' : '编辑'}组件交互事件`"
    :sub="`所属对象：${proto.eventDrawer.value?.scope === 'page' ? proto.pageConfig.name : proto.selectedNode.value?.name}（${proto.eventDrawer.value?.scope === 'page' ? proto.pageConfig.code : proto.selectedNode.value?.code}）`"
    :width="420"
    @close="proto.closeOverlay()"
  >
    <section class="pf-sec">
      <p class="pf-section">选择触发时机</p>
      <button
        v-for="option in triggerOptions"
        :key="option.code"
        type="button"
        :class="['pf-trigger', drawerTrigger === option.code && 'is-on']"
        @click="drawerTrigger = option.code"
      >
        <span>{{ option.name }}</span><span class="pf-chip">{{ option.code }}</span>
      </button>
    </section>

    <section class="pf-sec">
      <p class="pf-section">选择响应动作</p>
      <select v-model="drawerAction" class="pf-input">
        <option v-for="option in EVENT_ACTION_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
      <label class="pf-field"><span>动作目标 / 接口地址 / 提示内容</span><input v-model="drawerTarget" class="pf-input" type="text"></label>
    </section>

    <section class="pf-sec">
      <div class="pf-switch-line">
        <div><strong>启用该事件绑定</strong><p class="pf-sub">停用后保留配置但不在运行态触发</p></div>
        <button type="button" class="pf-switch" role="switch" :aria-checked="drawerEnabled" @click="drawerEnabled = !drawerEnabled"><i /></button>
      </div>
    </section>

    <section class="pf-sec">
      <div class="pf-label-line"><span class="pf-section" style="font-size:13px">事件处理器声明预览</span><button type="button" class="pf-link" @click="proto.pushToast(`已仿真触发「${drawerTrigger}」`, 'info')">测试触发效果</button></div>
      <pre class="pf-code">{{ previewJson }}</pre>
    </section>

    <template #footer>
      <Button variant="text" @click="proto.closeOverlay()">取消</Button>
      <Button variant="primary" @click="saveDrawer">保存事件配置</Button>
    </template>
  </Drawer>
</template>

<style scoped>
.pf-cmd-list { display: flex; flex-direction: column; gap: 2px; max-height: 300px; overflow-y: auto; }
.pf-cmd-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; height: 36px; padding: 0 12px; border: 0; border-radius: var(--r-xs); background: none; color: var(--ink); cursor: pointer; font-size: 13px; text-align: left; }
.pf-cmd-row:hover { background: var(--hover-bg); }
.pf-cmd-empty { padding: 18px 0; color: var(--ink-faint); font-size: 12.5px; text-align: center; }
.pf-sec { display: flex; flex-direction: column; gap: 10px; }
/* 抽屉脚上那颗编码徽标顶到最左，按钮仍按平台 .drawer-foot 的靠右排 */
.pf-chip.is-lead { margin-right: auto; }
.pf-link { border: 0; background: none; color: var(--blue); cursor: pointer; font-size: 12px; }
.pf-switch-line div strong { font-size: 13px; font-weight: 620; color: var(--ink); }
</style>
