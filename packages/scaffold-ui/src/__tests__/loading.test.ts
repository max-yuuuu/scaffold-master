import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const HERE = dirname(fileURLToPath(import.meta.url))
const ICON_BUTTON = join(HERE, '../components/ui/IconButton.vue')
const PERMISSION_BUTTON = join(HERE, '../components/business/PermissionButton.vue')
const COMPONENTS_CSS = join(HERE, '../styles/components.css')

/**
 * 图标按钮的执行态契约。
 *
 * Button 早就有 loading（转圈换掉图标、期间禁用），IconButton 没有 —— 而 PermissionButton
 * 在 iconOnly 那一支渲染的是 IconButton。列表的行操作几乎都是图标按钮，于是「点了但接口
 * 还在跑」这一段时间界面上什么都没有：不转圈、不禁用，连第二次点都拦得住。
 * 这一类失效是静默的：类型检查只看调用方传没传 prop，分支里把它丢掉没人管。
 */

const SRC = {
  icon: readFileSync(ICON_BUTTON, 'utf8'),
  perm: readFileSync(PERMISSION_BUTTON, 'utf8'),
  css: readFileSync(COMPONENTS_CSS, 'utf8'),
}

test('IconButton 有 loading：转圈换掉图标、期间禁用并标 aria-busy', () => {
  assert.match(SRC.icon, /loading\?:\s*boolean/, 'IconButton 必须声明 loading prop')
  assert.match(SRC.icon, /:disabled="disabled \|\| loading"/, '执行中必须禁用，否则连点会并发打同一个请求')
  assert.match(SRC.icon, /:aria-busy="loading \|\| undefined"/, '读屏要能听出这一颗在忙')
  assert.match(SRC.icon, /v-if="loading"[\s\S]*class="spinner"/, '执行中画 spinner 而不是原来的图标')
  assert.match(SRC.icon, /v-if="loading"[\s\S]*class="spinner"[\s\S]*<Icon\s+v-else/, '执行中画 spinner，图标让位、两者不并存')
})

test('PermissionButton 的两个分支都转发 loading —— iconOnly 那一支以前把它丢了', () => {
  const iconOnlyBranch = SRC.perm.match(/<IconButton[^>]*>/s)?.[0]
  assert.ok(iconOnlyBranch, '找不到 iconOnly 分支的 IconButton')
  assert.match(iconOnlyBranch, /:loading="loading"/, '行内图标按钮的 loading 必须传下去，不然行操作点了没反馈')
  const buttonBranch = SRC.perm.match(/<Button[^>]*>/s)?.[0]
  assert.ok(buttonBranch, '找不到文字分支的 Button')
  assert.match(buttonBranch, /:loading="loading"/)
})

test('图标按钮里的 spinner 有尺寸：.spinner 本身没有默认宽高', () => {
  // 不写这一条，spinner 会按内容算成 0×0 —— 边框画不出来，界面上只剩一颗空按钮在闪
  assert.match(SRC.css, /\.icon-btn \.spinner\s*\{[^}]*width:/, '.icon-btn .spinner 必须给宽高')
  assert.match(SRC.css, /\.icon-btn-sm \.spinner\s*\{[^}]*width:/, '小尺寸那颗单独给一档')
})

test('执行中的图标按钮不许被 disabled 的半透明压掉：转圈就是要给正在点的人看的', () => {
  assert.match(SRC.css, /\.icon-btn:disabled\s*\{\s*opacity:\s*\.45/, '禁用态整体压暗是既有口径，别改它')
  assert.match(SRC.css, /\.icon-btn\[aria-busy='true'\][^{]*\{[^}]*opacity:\s*1/, '但 busy 那颗要保持可见')
})
