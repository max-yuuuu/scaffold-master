/* ============================================================
   品牌与全局配置：换品牌只改这里。
   导航与业务行类型随物料包提供，这里按原路径转出，宿主页面继续从 @/data 引用。
   ============================================================ */
export type { BaseRow, Dept, Menu, NavGroup, NavLeaf } from '@yuanda-lowcode-ai/scaffold-ui/types'

export interface BeianItem {
  /** 备案号显示文本，须与工信部 / 公安备案截图一致 */
  text: string
  /** 官方查询地址 */
  link: string
}

export const APP = Object.freeze({
  name: '元搭低代码平台',
  shortName: '元搭低代码',
  subtitle: 'Admin Console',
  version: 'v1.0.0',
  /** 登录页左侧介绍位：接入具体业务平台时改这一段 */
  platform: Object.freeze({
    eyebrow: '通用后台底座',
    slogan: ['统一的后台底座，', '专注业务本身。'],
    lead: '用户、角色、菜单、部门、字典与审计开箱即用，新业务模块只需关注字段与流程。',
  }),
  /** 登录页页脚：版权主体公司名称 */
  company: '通用后台管理系统',
  /** 登录页页脚备案条目；脚手架不带备案信息，接入实际站点时按 BeianItem 填写 */
  beian: Object.freeze([] as readonly BeianItem[]),
})
