import { computed, inject, provide, ref, type ComputedRef, type InjectionKey, type Ref } from 'vue'
import { pushToast, toastError } from '@yuanda-lowcode-ai/scaffold-ui/composables/toast'
import { platformApi, type FieldRow, type PageDetail } from '@/api'
import { createDesigner, defaultPropsFor, type DesignJson, type Designer, type ModelField, type UINode } from './design.ts'

/**
 * 一次设计会话：一份 store、一条撤销栈、一个脏计数，加上浮层与预览这些跨栏共用的状态。
 *
 * 放在这里而不是留在 DesignerWorkspace 里，是因为顶栏、左栏、画布、右栏、浮层都要读同一份 ——
 * 用 props 往下传十几格，任何一处漏一格就是「画布动了但顶栏撤销还是灰的」这种查半天的错。
 */
export interface DesignerSession {
  page: PageDetail
  fields: ModelField[]
  store: Designer
  /** store 里是普通可变对象，Vue 看不见它的 splice，每个动作后 bump 一下让读数重算 */
  tick: Ref<number>
  act: (run: () => void) => void
  /** 只重算读数，不产生历史也不置脏：给「手势被当场拒了、高亮要抹掉」这种没改动树的情况 */
  refresh: () => void
  root: ComputedRef<UINode>
  blocks: ComputedRef<UINode[]>
  selected: ComputedRef<UINode | null>
  selectedParent: ComputedRef<UINode | null>
  selectedKey: ComputedRef<string | null>
  isPage: ComputedRef<boolean>
  referenced: ComputedRef<string[]>
  columns: ComputedRef<ReturnType<Designer['columns']>>
  canUndo: ComputedRef<boolean>
  canRedo: ComputedRef<boolean>

  dirty: ComputedRef<boolean>
  saving: Ref<boolean>
  save: () => Promise<void>
  publish: (body: { force: boolean, changeDesc: string }) => Promise<PublishResult>

  previewMode: Ref<boolean>
  togglePreview: () => void
  leftCollapsed: Ref<boolean>
  rightCollapsed: Ref<boolean>
  scrolled: Ref<boolean>

  /** 浮层只有一条栈：Escape 关最上层，⌘K 与顶栏按钮共用同一个入口 */
  overlay: Ref<Overlay | null>
  openOverlay: (kind: Overlay) => void
  closeOverlay: () => void

  /** 拿起一个组件等待落点：拖拽与点选共用，两条输入不可能漂开 */
  armed: Ref<string | null>
  arm: (type: string) => void
  dropArmed: () => void
  /** 正在拖的东西：物料卡发起 kind 'new'，画布上的方块发起 kind 'move'，落点由画布读这一个字段 */
  drag: Ref<Drag | null>
  /** 拖放与点选共用同一段落点逻辑，两条输入不可能漂开 */
  place: (item: Drag, target: string, index?: number) => void
  /** 选中不改树，所以它不在 act() 里；但视图照样要重算，不然点了方块右侧还是页面属性 */
  pick: (key: string | null) => void
}

export type Overlay = 'command' | 'schema' | 'publish'

/** 拖拽载荷：新组件从物料库来，已有节点从画布或层级树来 */
export type Drag = { kind: 'new', type: string } | { kind: 'move', type: string, key: string }

export interface PublishResult {
  status: number
  version: number | null
  tagName: string | null
  message: string
}

const KEY: InjectionKey<Ref<DesignerSession | null>> = Symbol('designer-session')

export function createSession(page: PageDetail, fieldRows: FieldRow[]): DesignerSession {
  const store = createDesigner(page.design as unknown as DesignJson)
  // 打开设计器时把库里的 modelCode 绑上：这是装配不是人做的一步，所以不进撤销栈
  store.bindModel(page.modelCode)

  const tick = ref(0)
  const act = (run: () => void) => {
    run()
    tick.value += 1
  }
  /** 只重算读数，不产生历史也不置脏：给「手势被当场拒了、高亮要抹掉」这种没改动树的情况 */
  const refresh = () => {
    tick.value += 1
  }

  const reading = <T>(pick: () => T) => computed(() => {
    void tick.value
    return pick()
  })

  /**
   * 有没有未保存改动 = 当前这份设计与库里那一份是否逐字相同，不是「改了几下」。
   * 拿计数器会在撤销后还挂着「有 1 项未保存」，而那时画布明明已经回到了保存时的样子。
   */
  const baseline = ref(JSON.stringify(store.json()))
  const dirty = reading(() => JSON.stringify(store.json()) !== baseline.value)

  const saving = ref(false)

  async function save() {
    saving.value = true
    try {
      await platformApi.pages.update(page.id, {
        pageName: page.pageName,
        routePath: page.routePath,
        menuIcon: page.menuIcon,
        menuSort: page.menuSort,
        design: store.json() as unknown as Record<string, unknown>,
      })
      baseline.value = JSON.stringify(store.json())
      pushToast('设计已保存，下次发布会生成这一页')
    } catch (error) {
      // 后端那句 400 带着 layout.children[x] 的路径，这里原样抛给人，不翻译成第二套话术
      toastError(error, '保存失败')
      throw error
    } finally {
      saving.value = false
    }
  }

  async function publish(body: { force: boolean, changeDesc: string }): Promise<PublishResult> {
    try {
      const result = await platformApi.pages.publish(page.id, body)
      baseline.value = JSON.stringify(store.json())
      return {
        status: Number(result.status ?? 0),
        version: result.version === undefined || result.version === null ? null : Number(result.version),
        tagName: result.tagName ? String(result.tagName) : null,
        message: String(result.message ?? ''),
      }
    } catch (error) {
      toastError(error, '发布失败')
      throw error
    }
  }

  const overlay = ref<Overlay | null>(null)

  const session: DesignerSession = {
    page,
    fields: fieldRows as unknown as ModelField[],
    store,
    tick,
    act,
    refresh,
    // 必须每次给一个新引用：store 里是普通可变对象，就地改 locked/props 之后
    // `store.root.children` 还是那个数组、那个节点，computed 认为值没变就不通知下游，
    // 于是 tick 明明涨了、画布却一动不动 —— 浅克隆一层把「数据变了」翻译成「引用也变了」。
    root: reading(() => ({ ...store.root })),
    blocks: reading(() => [...store.root.children]),
    selected: reading(() => {
      const node = store.selected ? store.find(store.selected) : null
      return node ? { ...node } : null
    }),
    selectedParent: reading(() => {
      const node = store.selected ? store.parentOf(store.selected) : null
      return node ? { ...node } : null
    }),
    selectedKey: reading(() => store.selected),
    isPage: reading(() => store.selected === null),
    referenced: reading(() => store.referencedFields()),
    columns: reading(() => store.columns()),
    canUndo: reading(() => store.canUndo),
    canRedo: reading(() => store.canRedo),

    dirty,
    saving,
    save,
    publish,

    previewMode: ref(false),
    togglePreview() {
      session.previewMode.value = !session.previewMode.value
      pushToast(session.previewMode.value ? '运行预览：两侧面板已收起，画布按发布后的样子渲染' : '回到编辑', 'info')
    },
    leftCollapsed: ref(false),
    rightCollapsed: ref(false),
    scrolled: ref(false),

    overlay,
    openOverlay(kind) {
      overlay.value = kind
    },
    closeOverlay() {
      overlay.value = null
    },

    armed: ref(null),
    drag: ref<Drag | null>(null),
    arm(type) {
      session.armed.value = type
      pushToast(`已拿起「${type}」，点画布上的容器放下去`)
    },
    dropArmed() {
      session.armed.value = null
    },
    pick(key) {
      store.select(key)
      tick.value += 1
    },
    place(item, target, index) {
      try {
        if (item.kind === 'new') {
          act(() => store.insert(target, { type: item.type, props: defaultPropsFor(item.type) }, index))
        } else {
          act(() => store.move(item.key, target, index))
        }
      } catch (error) {
        // 落点非法当场说清为什么；树没动，所以只重算读数，不往撤销栈和脏计数里塞一步空操作
        pushToast((error as Error).message, 'danger')
        refresh()
      } finally {
        session.drag.value = null
      }
    },
  }

  return session
}

export function provideSession(holder: Ref<DesignerSession | null>) {
  provide(KEY, holder)
}

/** 取用会话：只有在数据到位、DesignerView 把组件挂出来之后才拿得到，早拿就是装配顺序错了 */
export function useSession(): DesignerSession {
  const holder = inject(KEY)
  const session = holder?.value
  if (!session) throw new Error('设计器组件必须在 DesignerView 之下、且页面数据已加载')
  return session
}
