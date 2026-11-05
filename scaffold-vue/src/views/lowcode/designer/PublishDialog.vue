<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import Modal from '@yuanda-lowcode-ai/scaffold-ui/components/ui/Modal.vue'
import { Button, Textarea } from '@yuanda-lowcode-ai/scaffold-ui/components/ui'
import type { PageDetail } from '@/api'
import type { DesignerSession } from './session.ts'

/* 发布核准弹窗。
   送出去的是库里那一份设计，不是画布上这份 —— 所以脏的时候必须先说清楚，
   否则人改完画布直接点发布，看到的「发布成功」说的是上一次保存的那版。 */
const props = defineProps<{ open: boolean, page: PageDetail, session: DesignerSession }>()
const emit = defineEmits<{ (e: 'close'): void, (e: 'published'): void }>()

const changeDesc = ref('')
const busy = ref(false)
const result = ref<Awaited<ReturnType<DesignerSession['publish']>> | null>(null)
const failure = ref('')

const needsForce = computed(() => failure.value.includes('带 force 再来一次'))
const dirty = computed(() => props.session.dirty.value)

watch(() => props.open, (open) => {
  if (!open) {
    changeDesc.value = ''
    result.value = null
    failure.value = ''
  }
})

async function submit(force: boolean) {
  busy.value = true
  failure.value = ''
  try {
    result.value = await props.session.publish({ force, changeDesc: changeDesc.value.trim() })
    emit('published')
  } catch (error) {
    // 后端那句报错带着远端提交号与分支名，原样给人看，不翻译成第二套话术
    failure.value = (error as Error).message || '发布失败'
  } finally {
    busy.value = false
  }
}

const outcome = computed(() => {
  if (!result.value) return ''
  if (result.value.status === 1) return `已签发：${result.value.tagName ?? `v${result.value.version}`}`
  if (result.value.status === 2) return '内容与仓库一致，这一版没有产生新提交'
  return result.value.message
})

const title = computed(() => `发布「${props.page.pageName}」`)
const projectLabel = computed(() => props.page.projectName || '未归属项目')
</script>

<template>
  <Modal :open="open" :title="title" :width="620" @close="emit('close')">
    <p class="dz-pub-note">
      发布会把<strong>库里已保存</strong>的这份设计生成到项目「{{ projectLabel }}」的发布分支并打上版本标记。
      版本号由平台在这条链上递增，不在这里手填。
    </p>

    <p v-if="dirty" class="dz-pub-warn">
      画布上有未保存改动 —— 现在发出去的是上一次保存的那版。先关掉这个弹窗点「保存草稿」，或者直接：
      <Button variant="text" size="sm" :loading="session.saving.value" @click="session.save()">保存并发布</Button>
    </p>

    <label class="dz-pub-field">
      <span class="dz-pub-label">发布说明</span>
      <Textarea v-model="changeDesc" :rows="3" :maxlength="512" show-count placeholder="这一版为什么发，半年后回滚时只有这句话还读得懂" />
    </label>

    <p v-if="failure" class="dz-pub-fail">
      <span class="dz-pub-fail-text">{{ failure }}</span>
      <Button v-if="needsForce" variant="danger" size="sm" :loading="busy" @click="submit(true)">我已确认，仍然发布</Button>
    </p>
    <p v-else-if="outcome" class="dz-pub-ok">{{ outcome }}</p>

    <template #footer>
      <Button variant="text" @click="emit('close')">{{ result ? '完成' : '取消' }}</Button>
      <Button v-if="!result" variant="primary" :loading="busy" :disabled="dirty" @click="submit(false)">确认签发上线</Button>
    </template>
  </Modal>
</template>

<style scoped>
.dz-pub-note,
.dz-pub-warn,
.dz-pub-fail,
.dz-pub-ok {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.7;
}

.dz-pub-note {
  color: var(--ink-soft);
}

.dz-pub-warn {
  padding: 8px 10px;
  border-left: 2px solid var(--red);
  border-radius: var(--r-xs);
  background: var(--red-soft);
  color: var(--red-text);
}

.dz-pub-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.dz-pub-label {
  color: var(--ink-soft);
  font-size: 12px;
}

.dz-pub-fail {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
  padding: 8px 10px;
  border-left: 2px solid var(--red);
  border-radius: var(--r-xs);
  background: var(--red-soft);
  color: var(--red-text);
}

.dz-pub-fail-text {
  white-space: pre-wrap;
}

.dz-pub-ok {
  padding: 8px 10px;
  border-left: 2px solid var(--lime);
  border-radius: var(--r-xs);
  background: var(--lime-soft);
  color: var(--leaf-text);
}
</style>
