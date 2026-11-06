import type { Component } from 'vue'

/**
 * 视图注册表：菜单里的 `component` 标识 → 组件加载函数。
 *
 * 为什么要有这一层（方案 §5.2 壳包化）：解析表原本写死在 `composables/auth.ts` 里，只能覆盖
 * 底座自己那一个 `views` 目录。那样宿主（平台前端，以及将来二开的交付项目）想加页面就得把文件
 * 塞进底座目录 —— 依赖方向刚好反了。这里把它变成可注入的多张表，且**不静默覆盖**：
 * 同一个标识由先注册的一方占住，后来的记进冲突清单，与 C 档「存在即跳过」同一口径。
 *
 * 本模块不调 `import.meta.glob`：那张表由装配处递进来，所以这里能在 node 下直接测。
 */

export type ViewLoader = () => Promise<{ default: Component }>

export interface ViewContribution {
  /** 装配处的 glob 结果，键是相对装配处的模块路径 */
  modules: Record<string, ViewLoader>
  /**
   * 菜单 `component` 标识 → modules 里的键。
   *
   * 只有这张表里的标识会参与认领与冲突判定；其余标识按 `prefix + 标识 + suffix` 现场拼
   * （历史命名如 `system/user/index` 指向 `views/system/Users.vue`，只能靠它兜）。
   */
  alias?: Record<string, string>
  prefix?: string
  suffix?: string
}

export interface ViewConflict {
  component: string
  /** 已占住这个标识的一方 */
  owner: string
  /** 被拒的那一方 */
  refused: string
}

const DEFAULT_PREFIX = '../views/'
const DEFAULT_SUFFIX = '.vue'

interface Registered {
  owner: string
  loader: ViewLoader
}

let sources: Array<{ owner: string; contribution: ViewContribution }> = []
const claims = new Map<string, Registered>()
const conflicts: ViewConflict[] = []

/** 标识 → 该方 modules 里对应的键 */
function moduleKeyOf(component: string, contribution: ViewContribution) {
  return contribution.alias?.[component]
    ?? `${contribution.prefix ?? DEFAULT_PREFIX}${component}${contribution.suffix ?? DEFAULT_SUFFIX}`
}

/**
 * 登记一张解析表。
 *
 * @param owner 这张表的归属（`base`、`platform` 等），只用于冲突时说清是谁被拒
 * @returns 本次被拒的标识；空数组表示这张表没有跟已登记的一方抢位置
 */
export function registerViews(owner: string, contribution: ViewContribution): ViewConflict[] {
  sources = [...sources, { owner, contribution }]
  const refused: ViewConflict[] = []

  Object.keys(contribution.alias ?? {}).forEach((component) => {
    const loader = contribution.modules[moduleKeyOf(component, contribution)]
    if (!loader) return

    const claimed = claims.get(component)
    if (claimed) {
      const conflict = { component, owner: claimed.owner, refused: owner }
      conflicts.push(conflict)
      refused.push(conflict)
      return
    }
    claims.set(component, { owner, loader })
  })

  return refused
}

/**
 * 解析菜单标识。
 *
 * 先认已认领的显式标识，再按登记顺序逐张表现拼约定路径 —— 先注册的表优先，
 * 这样底座不会因为宿主后登记了一张同名的表而被换掉页面。
 */
export function resolveView(component: string | undefined): ViewLoader | undefined {
  if (!component) return undefined
  const claimed = claims.get(component)
  if (claimed) return claimed.loader

  for (const { contribution } of sources) {
    const loader = contribution.modules[moduleKeyOf(component, contribution)]
    if (loader) return loader
  }
  return undefined
}

/** 被拒的覆盖尝试：装配处要把这句说出来，不能让人以为宿主页面生效了 */
export function viewConflicts(): ViewConflict[] {
  return [...conflicts]
}

export function resetViewRegistry(): void {
  sources = []
  claims.clear()
  conflicts.length = 0
}
