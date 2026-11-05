import { onScopeDispose } from 'vue'

/* ============================================================
   离开拦截：编辑页有未保存改动时，关标签页与刷新都要拦下。

   这里只能拦、不能画。浏览器对 `beforeunload` 的规定是：网页可以取消这次离开，
   但那条确认框一律由浏览器自己画，页面无权在这里塞自定义 UI ——
   所以「与平台 Confirm 一致」这条在关标签页这个动作上做不到，不是实现选择。
   站内换路由倒是能接管（用 `onBeforeRouteLeave` + 平台 `confirm()`），
   可设计器是 `window.open` 出去的一个独立标签页、历史记录只有这一条，
   没有任何站内入口会离开它，那条钩子挂上去也永远不会被触发，所以不写。
   ============================================================ */
export function useUnsavedGuard(dirty: () => boolean) {
  function onBeforeUnload(event: BeforeUnloadEvent) {
    if (!dirty()) return
    event.preventDefault()
    // Chrome 要求同时置空 returnValue 才认这次拦截
    event.returnValue = ''
  }

  window.addEventListener('beforeunload', onBeforeUnload)
  onScopeDispose(() => window.removeEventListener('beforeunload', onBeforeUnload))
}
