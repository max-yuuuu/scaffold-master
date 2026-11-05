<script setup lang="ts">
import { computed } from 'vue'
import Drawer from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Drawer.vue'
import Button from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Button.vue'
import { pushToast } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { copyText } from '@yuanda-lowcode-ai/scaffold-ui/utils/clipboard'
import type { DesignJson } from './design.ts'

/* 结构代码抽屉：画布上摆的每一格最终就是这份 JSON，发布时生成器读它。
   给人看一眼它是为了让人相信「保存的就是我看到的」，所以这里不做第二套渲染，直接序列化当前会话。 */
const props = defineProps<{ open: boolean, json: DesignJson }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const text = computed(() => JSON.stringify(props.json, null, 2))

async function copy() {
  const ok = await copyText(text.value)
  pushToast(ok ? '结构代码已复制到剪贴板' : '浏览器拒绝了剪贴板写入，请在代码块里手工全选', ok ? 'ok' : 'danger')
}
</script>

<template>
  <Drawer :open="open" title="页面结构代码（design_json）" sub="画布上摆的每一格最终就是这份 JSON，发布时生成器读它" :width="680" @close="emit('close')">
    <pre class="dz-schema">{{ text }}</pre>
    <template #footer>
      <div class="dz-schema-foot">
        <span class="dz-schema-meta">协议版本 {{ json.version }} · {{ json.pageType === 'list' ? '列表页' : json.pageType }} · 模型 {{ json.modelRef || '未绑定' }}</span>
        <Button variant="primary" size="sm" icon="copy" @click="copy">复制结构代码</Button>
      </div>
    </template>
  </Drawer>
</template>

<style scoped>
.dz-schema {
  /* 深色代码面与页面主题无关：两种外观下都是同一块铭牌材质，跟规格铭牌一个道理 */
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border-radius: var(--r-md);
  background: var(--plate-bg);
  color: var(--plate-fg);
  font-family: var(--font-mono);
  font-size: 12px;
  line-height: 1.7;
  white-space: pre;
}

.dz-schema-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.dz-schema-meta {
  color: var(--ink-faint);
  font-size: 12px;
}
</style>
