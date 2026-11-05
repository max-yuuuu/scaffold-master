<script setup lang="ts">
import { computed } from 'vue'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { usePrototype } from './context.ts'
import { appearance } from '@yuanda-lowcode-ai/scaffold-ui/composables/appearance'
import { APP } from '@/data'

/* 顶栏：品牌标 / 面包屑 + 发布态胶囊 / ⌘K / 撤销重做 / 结构代码 / 深浅色 / 预览 / 保存 / 发布。
   这一版是静态稿，保存与发布只改本地脏计数，不打接口 —— 真接口在「旧版（已接数据）」那条路上。
   没有返回按钮：设计器独占整个标签页，关闭就交给浏览器自己的标签页关闭动作，
   未保存时由 beforeunload 拦一道（见 ProtoDesigner）。 */
const proto = usePrototype()

const isMac = typeof navigator === 'undefined' || /^Mac|iPod|iPhone|iPad/.test(navigator.platform)
const dark = computed(() => appearance.theme === 'dark' || (appearance.theme === 'auto' && window.matchMedia('(prefers-color-scheme: dark)').matches))

function toggleTheme() {
  appearance.theme = dark.value ? 'light' : 'dark'
}
</script>

<template>
  <header :class="['pf-topbar', proto.canvasScrolled.value && 'is-condensed']">
    <div class="pf-tools">
      <span class="pf-brand" :title="`${APP.name} · ${APP.subtitle}`"><i /></span>
      <nav class="pf-crumbs" aria-label="页面位置面包屑">
        <span>宁德三期制造执行</span>
        <span class="sep">/</span>
        <span>工艺工作台设计器</span>
        <span class="sep">/</span>
        <span class="here">{{ proto.pageConfig.name }}</span>
        <span class="pf-tag tone-ok" style="margin-left:6px"><i />已发布 v2.4</span>
      </nav>
    </div>

    <button type="button" class="pf-search" aria-label="搜索组件、属性或指令" @click="proto.openOverlay('command')">
      <span class="left"><Icon name="search" :size="14" />搜索组件、属性或指令...</span>
      <span class="kbd">{{ isMac ? '⌘' : 'Ctrl' }} K</span>
    </button>

    <div class="pf-tools">
      <div class="pf-group">
        <button type="button" class="pf-icon-btn" :disabled="!proto.canUndo.value" aria-label="撤销上一步操作" title="撤销上一步操作" @click="proto.undo()"><Icon name="undo" :size="15" /></button>
        <button type="button" class="pf-icon-btn" :disabled="!proto.canRedo.value" aria-label="重做上一步操作" title="重做上一步操作" @click="proto.redo()"><Icon name="redo" :size="15" /></button>
        <button type="button" class="pf-icon-btn" aria-label="查看页面结构代码" title="查看页面结构代码" @click="proto.openOverlay('schema')"><Icon name="code" :size="15" /></button>
        <button type="button" class="pf-icon-btn" :aria-label="dark ? '切换浅色模式' : '切换深色模式'" :title="dark ? '切换浅色模式' : '切换深色模式'" @click="toggleTheme"><Icon :name="dark ? 'sun' : 'moon'" :size="15" /></button>
      </div>
      <button type="button" :class="['pf-btn', 'is-secondary', proto.previewMode.value && 'is-on']" @click="proto.previewMode.value = !proto.previewMode.value">
        <Icon name="eye" :size="15" />{{ proto.previewMode.value ? '退出运行预览' : '预览运行态' }}
      </button>
      <button type="button" class="pf-btn is-secondary" @click="proto.saveDraft()">保存草稿</button>
      <button type="button" class="pf-btn is-primary" @click="proto.openOverlay('publish')">发布上线</button>
    </div>
  </header>
</template>

<style scoped>
.pf-group { display: flex; align-items: center; gap: 4px; padding-right: 8px; border-right: 1px solid var(--line); }
.pf-search .left { display: inline-flex; align-items: center; gap: 8px; }
.pf-btn.is-on { border-color: var(--blue); color: var(--blue); }
</style>
