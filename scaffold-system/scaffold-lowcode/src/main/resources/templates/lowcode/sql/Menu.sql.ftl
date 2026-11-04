-- ${menuName}的菜单与权限点
-- 由低代码设计器按模型定义生成，属 B 档：每次发布整文件覆盖。权限点由模型编码派生
-- （${permissionPrefix}:*），与生成的 Controller 上的 @RequiresPermission 一一对应 ——
-- 只改一边就会出现「页面在、点进去 403」。
--
-- 菜单 ID 交给数据库自增分配（底座原来按「租户各自 MAX+1」分配，两个租户会算出同一个 ID 撞主键，
-- V4 已改成自增），所以这里不能沿用底座的「固定 ID + INSERT IGNORE」写法，改成按 permission 判重：
-- 本文件重复执行不会产生第二条菜单，也不用猜本项目已经用到哪个 ID。
--
-- 菜单挂在顶层（parent_id = 0），要归到某个目录下请在项目侧改 parent_id；图标与排序取自页面
-- （lc_page.menu_icon / menu_sort，留空用 grid 与 1），图标可选值见物料包 data/icons.ts。
-- component 就是前端路由解析的相对路径：${menuComponent} → src/views/${menuComponent}.vue，
-- 该文件同样由平台生成并在同一次发布里推上去。

INSERT INTO sys_menu (parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
SELECT 0, '${menuName}', 'menu', '${menuIcon}', '${menuPath}', '${menuComponent}', '${permissionPrefix}:list', ${menuSort}, 1, 1, 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = '${permissionPrefix}:list');

SET @${resourceSlug}_menu := (SELECT id FROM sys_menu WHERE permission = '${permissionPrefix}:list' LIMIT 1);
<#list menuButtons as b>

INSERT INTO sys_menu (parent_id, menu_name, menu_type, icon, path, component, permission, sort, visible, status, create_by, update_by)
SELECT @${resourceSlug}_menu, '${b.action}${modelName}', 'btn', '', '', '', '${permissionPrefix}:${b.suffix}', ${b.sort}, 1, 1, 'system', 'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE permission = '${permissionPrefix}:${b.suffix}');
</#list>

-- 授给内置管理员角色（role_id = 1，与 V2 种子里的角色同一个），同样按已存在判重。
-- 其它角色要不要看到这个页面，请在项目的角色管理里勾，不由平台代做。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, m.id
FROM sys_menu m
WHERE m.permission IN (${menuPermissionList})
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);
