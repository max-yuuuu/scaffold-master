import { ref } from 'vue'

/* 按钮级权限的宿主侧状态：权限码来自 /api/auth/me 的 permissions，'*' 表示全部。
   @scaffold/ui 里的 PermissionButton / RowMenuItem 不直接读这里，
   而是经 main.ts 的 installScaffoldUi 把 hasPermission 作为 permission.has 注入。 */
export const permissions = ref<Set<string>>(new Set())

export function setPermissions(perms: string[]) {
  permissions.value = new Set(perms)
}

export function hasPermission(permission?: string | string[]): boolean {
  if (!permission || (Array.isArray(permission) && permission.length === 0)) return true
  const set = permissions.value
  if (set.has('*')) return true
  const list = Array.isArray(permission) ? permission : [permission]
  return list.some(item => set.has(item))
}
