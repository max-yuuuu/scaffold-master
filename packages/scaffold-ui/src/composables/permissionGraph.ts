import { shallowRef, watch, type Ref } from 'vue'
import { materialApi } from '../api'
import { scaffoldUi } from '../providers.ts'
import { indexPermissionGraph, type PermissionIndex } from '../utils/permissionGraph'

/* 权限关系图：溯源视图、菜单星图、问答规则共用一份；账号切换或写操作后 invalidate 重新取。 */
export const permissionIndex = shallowRef<PermissionIndex | null>(null)
let pending: Promise<PermissionIndex> | null = null
let watching = false

/* 会话版本变化（登录、退出、切换租户）后丢弃索引；首次取数时才注册，避免模块加载早于 installScaffoldUi */
function watchSession(session: Ref<number>) {
  if (watching) return
  watching = true
  watch(session, invalidatePermissionGraph)
}

export function loadPermissionGraph(force = false) {
  const session = scaffoldUi().permission.sessionVersion
  watchSession(session)
  if (!force && permissionIndex.value) return Promise.resolve(permissionIndex.value)
  if (!force && pending) return pending
  const version = session.value
  pending = materialApi.permissionGraph()
    .then((graph) => {
      const index = indexPermissionGraph(graph)
      if (version === session.value) permissionIndex.value = index
      return index
    })
    .finally(() => { pending = null })
  return pending
}

export function invalidatePermissionGraph() {
  permissionIndex.value = null
}
