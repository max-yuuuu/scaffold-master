import { onScopeDispose, ref, type Ref } from 'vue'

/* ============================================================
   窄屏判定：平台只有这一个断点（820px），视图偏好与设计器都读它。
   外壳导航（AppShell）与各处 CSS 的 @media 是同一数值的手抄副本 —— 包发到 npm 上，
   交付项目装的是已发布版本，还没发版的导出它 import 不到，所以那条链路只能写死字面量。
   这几处不一致不会报错，只会窄屏下行为不一致，由 proto.test.ts 那条用例钉住同一个数。
   ============================================================ */
export const NARROW_QUERY = '(max-width: 820px)'

export function isNarrow(): boolean {
  return window.matchMedia(NARROW_QUERY).matches
}

/** 响应式的窄屏标志；离开作用域自动摘掉监听 */
export function useNarrowScreen(): Ref<boolean> {
  const narrow = ref(isNarrow())
  const query = window.matchMedia(NARROW_QUERY)
  const onChange = (event: MediaQueryListEvent) => { narrow.value = event.matches }
  query.addEventListener('change', onChange)
  onScopeDispose(() => query.removeEventListener('change', onChange))
  return narrow
}
