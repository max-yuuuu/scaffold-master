<script setup lang="ts">
import { computed, inject } from 'vue'
import { useRouter } from 'vue-router'
import Icon from '@yuanda-lowcode-ai/scaffold-ui/components/Icon.vue'
import { currentUser, switchTenant } from '@/composables/auth'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { USER_MENU_API } from '@/shell/slots'

/* 平台运营交互：管理员切到别的租户查看数据后要能一步回去。
   交付项目恒为单租户，这一项点下去只会被 PlatformBoundary 挡回来，所以它整体住在平台侧申报里，
   不进交付文件 —— 而不是在 Topbar.vue 里埋一对标记块把它挖掉。 */

const router = useRouter()
const userMenu = inject(USER_MENU_API, { close: () => {} })

const isSwitched = computed(() => {
  const user = currentUser.value
  return !!user && user.homeTenantId !== undefined && user.tenantId !== user.homeTenantId
})

async function backToPlatformTenant() {
  userMenu.close()
  try {
    await switchTenant(null)
    pushToast('已回到平台租户')
    await router.replace('/')
  } catch (error) {
    toastError(error, '回到平台租户失败')
  }
}
</script>

<template>
  <button v-if="isSwitched" type="button" class="menu-item" role="menuitem" @click="backToPlatformTenant()">
    <Icon name="home" :size="16" />回到平台租户
  </button>
</template>
