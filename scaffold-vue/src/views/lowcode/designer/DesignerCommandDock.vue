<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import Modal from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Modal.vue'
import { useSession } from './session.ts'
import { BLOCK_LABELS, PALETTE } from './design.ts'

/* 设计器自己的 ⌘K：找组件、跳节点、下指令。
   控制台那条命令面板管的是「去哪个页面」，这里管的是「这一页的设计怎么改」，两份不合并。 */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ (e: 'close'): void }>()

const session = useSession()
const query = ref('')
const cursor = ref(0)
const input = ref<HTMLInputElement | null>(null)

interface Entry {
  group: string
  label: string
  code: string
  run: () => void
}

const entries = computed<Entry[]>(() => {
  const items: Entry[] = [
    { group: '指令', label: '保存草稿', code: 'cmd:save', run: () => void session.save() },
    { group: '指令', label: session.previewMode.value ? '退出运行预览' : '进入运行预览', code: 'cmd:preview', run: () => session.togglePreview() },
    { group: '指令', label: '查看页面结构代码', code: 'cmd:schema', run: () => session.openOverlay('schema') },
    { group: '指令', label: '发布上线', code: 'cmd:publish', run: () => session.openOverlay('publish') },
    { group: '指令', label: '收起或展开左侧组件面板', code: 'cmd:rail', run: () => { session.leftCollapsed.value = !session.leftCollapsed.value } },
    { group: '指令', label: '收起或展开右侧配置面板', code: 'cmd:inspector', run: () => { session.rightCollapsed.value = !session.rightCollapsed.value } },
  ]
  if (session.canUndo.value) items.push({ group: '指令', label: '撤销上一步改动', code: 'cmd:undo', run: () => session.act(() => session.store.undo()) })
  if (session.canRedo.value) items.push({ group: '指令', label: '重做上一步改动', code: 'cmd:redo', run: () => session.act(() => session.store.redo()) })

  PALETTE.forEach(group => group.items.forEach(item => items.push({
    group: '组件物料',
    label: `拿起「${item.label}」，再点画布上的容器放下`,
    code: item.type,
    run: () => session.arm(item.type),
  })))

  session.blocks.value.forEach(block => {
    items.push({ group: '画布节点', label: `选中区块「${BLOCK_LABELS[block.type] ?? block.type}」`, code: block.type, run: () => session.pick(block.key) })
    block.children.forEach(child => items.push({
      group: '画布节点',
      label: `选中控件「${String(child.props.label ?? child.type)}」`,
      code: child.type,
      run: () => session.pick(child.key),
    }))
  })
  return items
})

const matched = computed(() => {
  const key = query.value.trim().toLowerCase()
  if (!key) return entries.value
  return entries.value.filter(item => item.label.toLowerCase().includes(key) || item.code.toLowerCase().includes(key) || item.group.includes(key))
})

/** 分组渲染但光标走的是扁平序列，所以两边必须用同一个顺序 —— 各排一次就会「按到第 5 条高亮在第 3 组」 */
const grouped = computed(() => {
  const order: string[] = []
  const buckets: Record<string, Array<{ entry: Entry, index: number }>> = {}
  matched.value.forEach((entry, index) => {
    if (!buckets[entry.group]) {
      buckets[entry.group] = []
      order.push(entry.group)
    }
    buckets[entry.group].push({ entry, index })
  })
  return order.map(group => ({ group, rows: buckets[group] }))
})

watch(matched, () => { cursor.value = 0 })

watch(() => props.open, async (open) => {
  if (!open) {
    query.value = ''
    return
  }
  await nextTick()
  input.value?.focus()
})

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    cursor.value = Math.min(cursor.value + 1, matched.value.length - 1)
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    cursor.value = Math.max(cursor.value - 1, 0)
  } else if (event.key === 'Enter') {
    event.preventDefault()
    run(matched.value[cursor.value])
  }
}

function run(entry: Entry | undefined) {
  if (!entry) return
  entry.run()
  // 打开结构代码与发布要留在这条之上，其余动作执行完就该回到画布
  if (entry.code !== 'cmd:schema' && entry.code !== 'cmd:publish') emit('close')
}
</script>

<template>
  <Modal :open="props.open" :width="540" :close-on-overlay="true" @close="emit('close')">
    <div class="dz-cmd">
      <input
        ref="input"
        v-model="query"
        class="dz-cmd-input"
        type="text"
        placeholder="输入组件、节点或指令，例如：查询区、DictSelect、发布"
        aria-label="搜索组件、属性或指令"
        @keydown="onKeydown"
      >
      <div class="dz-cmd-list">
        <template v-for="section in grouped" :key="section.group">
          <p class="dz-cmd-group">{{ section.group }}</p>
          <button
            v-for="row in section.rows"
            :key="`${row.entry.code}-${row.index}`"
            type="button"
            :class="['dz-cmd-row', cursor === row.index && 'is-cursor']"
            @click="run(row.entry)"
            @mousemove="cursor = row.index"
          >
            <span>{{ row.entry.label }}</span>
            <span class="code-chip">{{ row.entry.code }}</span>
          </button>
        </template>
        <p v-if="!matched.length" class="dz-cmd-empty">没有匹配的组件、节点或指令。</p>
      </div>
    </div>
  </Modal>
</template>

<style scoped>
.dz-cmd {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.dz-cmd-input {
  width: 100%;
  height: var(--control-h);
  padding: 0 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--input-bg);
  color: var(--ink);
  font: inherit;
  font-size: 13px;
}

.dz-cmd-input:focus-visible {
  outline: 2px solid var(--focus);
  outline-offset: 1px;
}

.dz-cmd-list {
  max-height: 320px;
  overflow-y: auto;
}

.dz-cmd-group {
  margin: 8px 0 2px;
  color: var(--ink-faint);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.dz-cmd-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--r-xs);
  background: none;
  color: var(--ink);
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}

.dz-cmd-row.is-cursor,
.dz-cmd-row:hover {
  background: var(--hover-bg);
}

.dz-cmd-empty {
  padding: 18px 0;
  color: var(--ink-faint);
  font-size: 12.5px;
  text-align: center;
}
</style>
