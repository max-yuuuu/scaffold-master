import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { installScaffoldUi } from '@scaffold/ui'
import { installHoverLift, installPressFeedback, installRipple } from '@scaffold/ui/composables/motion'
import { scaffoldDict, scaffoldHttp, scaffoldPermission } from './api/scaffold-providers'

// 珍珠玻璃主题：设计令牌 → 基础 → 材质 → 控件 → 外壳 → 页面，层叠顺序由包内 theme.css 固定
import '@scaffold/ui/theme.css'

// 外观设置在挂载前写入 data-theme / data-density，避免首屏闪色
import '@scaffold/ui/composables/appearance'

const app = createApp(App)
// 三项能力必须在挂载前注入：包内组件与 composable 首次取数时就会读取
installScaffoldUi({ http: scaffoldHttp, permission: scaffoldPermission, dict: scaffoldDict })
app.use(router).mount('#app')

installPressFeedback()
installHoverLift()
installRipple()
