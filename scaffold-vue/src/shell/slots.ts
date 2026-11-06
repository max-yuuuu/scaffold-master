import type { Component, InjectionKey } from 'vue'

/**
 * 外壳扩展位（方案 §5.2 壳包化）。
 *
 * 外壳（`components/layout/**`）属交付文件，但它承载的交互未必只属于交付产品：
 * 「回到平台租户」那一项目前写死在 `Topbar.vue` 里，而交付项目恒为单租户，点下去只会被子
 * `PlatformBoundary` 挡回来 —— 读起来像「这里有入口但坏了」，而事实是「这个产品不提供这个动作」。
 * 与其在那份交付文件里埋六对 `scaffold:platform-only` 标记块（漏一对就留下半个引用），
 * 不如让外壳只渲染有人申报过的东西。
 *
 * 只开这一版真正用到的位置，不预先发明一排：位置名不在清单里就拒收，并记下是谁申报的 ——
 * 申报到一个没人渲染的位置，比不申报更坏，因为界面上一格都不会出现。
 */

export const SHELL_SLOT_NAMES = ['user-menu'] as const

/**
 * 外壳交给「用户菜单项」申报方的控制器。
 *
 * 申报方点完自己的动作（例如切完租户要跳回首页）必须能把菜单收起来 —— 不给这个口子，
 * 它就会自己去找外壳的内部状态，那等于把外壳的 DOM 结构变成对宿主的隐式契约。
 */
export const USER_MENU_API: InjectionKey<{ close: () => void }> = Symbol('scaffold-user-menu')

export type ShellSlotName = typeof SHELL_SLOT_NAMES[number]

export interface ShellSlotRefusal {
  owner: string
  slot: string
}

const buckets = new Map<ShellSlotName, Component[]>([['user-menu', []]])
const refusals: ShellSlotRefusal[] = []

function isKnownSlot(slot: string): slot is ShellSlotName {
  return (SHELL_SLOT_NAMES as readonly string[]).includes(slot)
}

/**
 * 申报一个组件进某个扩展位。
 *
 * @returns 是否收下。位置名不认识时拒收并记下是谁；同一组件重复申报同一位置不产生第二份
 * （装配被跑第二次，例如 dev 重载，不该冒出两个同名菜单项）
 */
export function registerShellSlot(owner: string, slot: string, component: Component): boolean {
  if (!isKnownSlot(slot)) {
    refusals.push({ owner, slot })
    return false
  }

  const bucket = buckets.get(slot) as Component[]
  if (!bucket.includes(component)) bucket.push(component)
  return true
}

/** 外壳渲染时取这一份，顺序就是申报顺序 */
export function shellSlotItems(slot: ShellSlotName): Component[] {
  return [...(buckets.get(slot) ?? [])]
}

/** 谁申报了不存在的位置：装配处要把这句写进 warnings */
export function shellSlotRefusals(): ShellSlotRefusal[] {
  return [...refusals]
}

export function resetShellSlots(): void {
  buckets.forEach(bucket => { bucket.length = 0 })
  refusals.length = 0
}
