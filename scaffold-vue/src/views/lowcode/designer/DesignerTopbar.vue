<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { Button, IconButton, Tag } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'
import { appearance } from '@yuanda-lowcode-ai/scaffold-ui/composables/appearance'
import { APP } from '@/data'

/* 设计器顶栏：这一条就是设计器页面的外壳，标签页里没有导航轨也没有控制台顶栏，
   所以面包屑、⌘K、深浅色都得在这里自己给一份 —— 用的仍是外壳那套 .brand-mark / .crumbs / .cmd-trigger 全局类。 */
const props = defineProps<{
  projectName: string
  pageName: string
  currentVersion: number
  publishStatus: number
  canUndo: boolean
  canRedo: boolean
  scrolled: boolean
  previewMode: boolean
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'command'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'schema'): void
  (e: 'theme'): void
  (e: 'preview'): void
  (e: 'save'): void
  (e: 'publish'): void
}>()

const isMac = typeof navigator === 'undefined' || /^Mac|iPod|iPhone|iPad/.test(navigator.platform)
const dark = computed(() => appearance.theme === 'dark' || (appearance.theme === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches))

/**
 * 胶囊说的是「这份设计最近一次发出去的结果」，取自 lc_page.publish_status（0 未发布 1 已发布 2 内容与远端一致 3 上次失败）。
 * 它和 page.status（页面启不启用）是两件事：停用的页面也可能刚发过，发布失败的页面也可能还在启用着。
 */
const state = computed(() => {
  const version = props.currentVersion ? ` v${props.currentVersion}` : ''
  switch (props.publishStatus) {
    case 1: return { tone: 'ok', text: `已发布${version}` }
    case 2: return { tone: 'ok', text: `与仓库一致${version}` }
    case 3: return { tone: 'danger', text: '上次发布失败' }
    default: return { tone: 'neutral', text: '未发布' }
  }
})
</script>

<template>
  <header :class="['dz-topbar', scrolled ? 'm-rail is-scrolled' : 'is-plain']">
    <div class="dz-tb-left">
      <span class="brand-mark" :title="`${APP.name} · ${APP.subtitle}`" aria-hidden="true"><span class="brand-dot" /></span>
      <div class="crumbs" aria-label="页面位置">
        <span>{{ projectName || '未归属项目' }}</span>
        <Icon class="crumb-sep" name="chevronRight" :size="12" />
        <span>页面设计器</span>
        <Icon class="crumb-sep" name="chevronRight" :size="12" />
        <span class="crumb-current">{{ pageName || '未命名页面' }}</span>
        <Tag class="dz-state" :tone="state.tone" dot>{{ state.text }}</Tag>
      </div>
    </div>

    <button type="button" class="cmd-trigger m-pill" aria-label="搜索组件、属性或指令" @click="emit('command')">
      <Icon name="search" :size="15" />
      <span class="cmd-text">搜索组件、属性或指令</span>
      <span class="kbd">{{ isMac ? '⌘' : 'Ctrl' }} K</span>
    </button>

    <div class="dz-tb-right">
      <div class="dz-tb-group">
        <IconButton name="undo" label="撤销上一步改动" :disabled="!canUndo" @click="emit('undo')" />
        <IconButton name="redo" label="重做上一步改动" :disabled="!canRedo" @click="emit('redo')" />
        <IconButton name="code" label="查看页面结构代码" @click="emit('schema')" />
        <IconButton
          :name="dark ? 'sun' : 'moon'"
          :label="dark ? '切换浅色模式' : '切换深色模式'"
          @click="emit('theme')"
        />
      </div>

      <button
        type="button"
        :class="['btn', 'btn-secondary', 'm-pill', previewMode && 'is-active']"
        @click="emit('preview')"
      >
        <Icon name="eye" :size="15" />{{ previewMode ? '退出运行预览' : '预览运行态' }}
      </button>

      <PermissionButton perm="lowcode:page:edit" variant="secondary" :loading="saving" @click="emit('save')">保存草稿</PermissionButton>
      <PermissionButton perm="lowcode:page:publish" variant="primary" @click="emit('publish')">发布上线</PermissionButton>
    </div>
  </header>
</template>

<style scoped>
.dz-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: var(--topbar-h);
  flex: none;
  padding: 0 20px;
  z-index: 20;
  transition: background .3s var(--ease-premium), box-shadow .3s var(--ease-premium), border-color .3s var(--ease-premium);
}

/* 平时只有一条下边线，画布滚出首屏后凝结成导航轨那种接近实色的面 + 接触阴影 */
.dz-topbar.is-plain {
  border-bottom: 1px solid var(--line);
  background: transparent;
}

.dz-topbar.is-scrolled {
  border-top: 0;
  border-right: 0;
  border-left: 0;
  border-bottom: 1px solid var(--line);
  box-shadow: var(--e1);
}

.dz-tb-left,
.dz-tb-right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.dz-tb-group {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-right: 8px;
  border-right: 1px solid var(--line);
}

.dz-state {
  margin-left: 6px;
}

.dz-tb-right .btn.is-active {
  border-color: var(--blue);
  color: var(--blue);
}

/* 窄屏（narrow.ts 那条 820px）：先丢装饰、保住动作。
   面包屑和 ⌘K 搜索在 627px 下会把预览/保存/发布三颗推出可视区，
   而 .designer 那层是 overflow: clip —— 被裁掉的按钮不是滚动看不见，是点不到。 */
@media (max-width: 820px) {
  .crumbs,
  .cmd-trigger { display: none; }
  .dz-topbar { padding: 0 10px; gap: 8px; }
  .dz-tb-right .btn { padding: 0 10px; }
}
</style>
