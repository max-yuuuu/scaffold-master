<script setup lang="ts">
import { IconButton } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import { PermissionButton } from '@yuanda-lowcode-ai/scaffold-ui/components/business'

/* 设计器工作区工具条。
   面包屑、深浅色、⌘K 归平台外壳，这里只放「改这份设计」要用的动作；
   页面名与模型编码留在这一条里，是因为设计器与列表同路由，外壳面包屑只到「页面管理」这一层。 */
defineProps<{
  pageName: string
  modelCode: string
  pageCode: string
  canUndo: boolean
  canRedo: boolean
  saving: boolean
  condensed: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'undo'): void
  (e: 'redo'): void
  (e: 'save'): void
}>()
</script>

<template>
  <header class="dz-bar" :class="{ 'is-condensed': condensed }">
    <button class="dz-back" type="button" @click="emit('close')">← 返回页面列表</button>
    <div class="dz-identity">
      <strong>{{ pageName || '未命名页面' }}</strong>
      <span class="dz-sub">{{ modelCode || '还没挂模型' }} · {{ pageCode }}</span>
    </div>

    <div class="dz-actions">
      <IconButton name="undo" label="撤销上一步改动" :disabled="!canUndo" @click="emit('undo')" />
      <IconButton name="redo" label="重做上一步改动" :disabled="!canRedo" @click="emit('redo')" />
      <PermissionButton perm="lowcode:page:edit" variant="primary" :loading="saving" @click="emit('save')">保存设计</PermissionButton>
    </div>
  </header>
</template>

<style scoped>
.dz-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-lg);
  background: var(--surface-solid);
  transition: box-shadow .22s var(--ease-premium), border-color .22s var(--ease-premium);
}

/* 画布滚出首屏后凝结：与外壳顶栏同一手法，用阴影与描边说明这一条已经浮在内容之上 */
.dz-bar.is-condensed {
  border-color: var(--line-strong);
  box-shadow: var(--e2);
}

.dz-back {
  border: 0;
  background: none;
  color: var(--ink-muted);
  cursor: pointer;
  font-size: 13px;
}

.dz-back:hover {
  color: var(--ink);
}

.dz-identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  margin-right: auto;
}

.dz-identity strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dz-sub {
  color: var(--ink-faint);
  font-size: 12px;
}

.dz-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
