package com.scaffold.lowcode.design;

/**
 * 生成一次的「落到哪个页面」（PR 6B）。
 *
 * <p>设计只管画布上的形；页面这一行还带着菜单要用的三件事：名字、图标、排序，
 * 以及可选的 {@code routePath}。它们不进 design_json 是因为库里已经有了，
 * 再往 JSON 里抄一份就是第二处事实 —— 改页面名时不会想到去改 JSON。
 *
 * @param pageName 菜单名；生成页面顶部的标题走设计里的 {@code props.title}
 * @param routePath 菜单与路由落点；{@code null} 表示由生成器用接口路径派生
 * @param menuIcon 菜单图标；{@code null} 用底座的 grid
 * @param menuSort 菜单排序；{@code null} 排 1
 */
public record PageTarget(String pageName, String routePath, String menuIcon, Integer menuSort, PageDesign design) {

    public PageTarget {
        if (design == null) {
            throw new com.scaffold.common.exception.BusinessException("页面目标必须带一份设计：没有设计就按模型的形状生成");
        }
    }
}
