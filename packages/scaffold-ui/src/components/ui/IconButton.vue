<script setup lang="ts">
import Icon from '../Icon.vue'

withDefaults(defineProps<{
  name: string
  label: string
  /**
   * 悬浮提示与无障碍名分开：aria-label 要短（「解锁这一格」），
   * 而 tooltip 该说清后果（「已锁定：移动、删除、往里拖放都会被拒」），
   * 合成一个字段就只能二选一。
   */
  hint?: string
  size?: number
  bordered?: boolean
  danger?: boolean
  small?: boolean
  active?: boolean
  disabled?: boolean
  /** 执行中：转圈换掉图标并禁用。图标按钮多是行操作，没有这一步就只剩「点了没反应」 */
  loading?: boolean
}>(), {
  size: 17,
})
</script>

<template>
  <button
    type="button"
    :class="['icon-btn', bordered && 'is-bordered', danger && 'is-danger', active && 'is-active', small && 'icon-btn-sm']"
    :title="hint ?? label"
    :aria-label="label"
    :aria-pressed="active"
    :disabled="disabled || loading"
    :aria-busy="loading || undefined"
  >
    <span v-if="loading" class="spinner" aria-hidden="true" />
    <Icon v-else :name="name" :size="size" />
  </button>
</template>
