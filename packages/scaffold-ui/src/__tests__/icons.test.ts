import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ICON_PATHS, MENU_ICON_OPTIONS } from '../data/icons.ts'

/* 图标注册表与菜单图标选择器的一致性。
   Icon.vue 查不到名字时静默画 file —— 不报错、类型检查也过，表现是菜单上那一格
   跟其他所有图标长得一样。菜单图标名还会被 SQL 种子数据引用（sys_menu.icon），
   那条链路离了浏览器没人复核，所以只能在这里钉住「选择器里列出的都能解析」。 */

test('菜单图标选择器列出的每一个名字都在注册表里', () => {
  const missing = MENU_ICON_OPTIONS.filter(name => !ICON_PATHS[name])
  assert.deepEqual(missing, [], `这些菜单图标名查不到路径，选上就是静默兜底成 file：${missing.join(', ')}`)
})

test('注册表里的路径都是可画的 path 数据，不留空串', () => {
  const empty = Object.entries(ICON_PATHS).filter(([, d]) => !d || !/^M|m|L|H|V|a|c/.test(d)).map(([name]) => name)
  assert.deepEqual(empty, [], `这些图标的路径为空或不以绘图命令开头，stroke 渲染出来是什么都没有：${empty.join(', ')}`)
})
