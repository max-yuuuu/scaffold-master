import { createApp } from 'vue'
import App from './App.vue'
import { createScaffoldApp } from './shell'
import { installScaffoldUi } from '@yuanda-lowcode-ai/scaffold-ui'
import { installHoverLift, installPressFeedback, installRipple } from '@yuanda-lowcode-ai/scaffold-ui/composables/motion'
import { scaffoldDict, scaffoldHttp, scaffoldPermission } from './api/scaffold-providers'

// scaffold:platform-only:start 平台侧的页面与路由由这里申报；交付项目没有 views/lowcode 这个目录
import { platformContribution } from './platform'
// scaffold:platform-only:end

// 珍珠玻璃主题：设计令牌 → 基础 → 材质 → 控件 → 外壳 → 页面，层叠顺序由包内 theme.css 固定
import '@yuanda-lowcode-ai/scaffold-ui/theme.css'

// 外观设置在挂载前写入 data-theme / data-density，避免首屏闪色
import '@yuanda-lowcode-ai/scaffold-ui/composables/appearance'

/* 装配顺序即语义：底座先登记自己那张视图与路由表，宿主贡献排在后面，
   所以平台不会因为后登记而盖掉底座的同名标识（撞了会被记进 warnings 说出来的）。

   参数单独包一对标记块而不是只在 import 上包：只删 import 那一行，这里的调用还会引用
   一个不存在的名字 —— 裁完必须还能编译。 */
const { router, warnings } = createScaffoldApp(
  // scaffold:platform-only:start
  [platformContribution]
  // scaffold:platform-only:end
)

if (warnings.length) {
  // 申报被挡不是致命错，但静默吞掉会让人以为自己的页面没生效 —— 至少控制台要说清
  console.warn('[scaffold] 部分宿主申报未生效：\n' + warnings.join('\n'))
}

const app = createApp(App)
// 三项能力必须在挂载前注入：包内组件与 composable 首次取数时就会读取
installScaffoldUi({ http: scaffoldHttp, permission: scaffoldPermission, dict: scaffoldDict })
app.use(router).mount('#app')

installPressFeedback()
installHoverLift()
installRipple()
