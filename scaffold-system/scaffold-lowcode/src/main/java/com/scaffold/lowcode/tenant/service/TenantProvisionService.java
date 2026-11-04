package com.scaffold.lowcode.tenant.service;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import com.scaffold.framework.security.PlatformBoundary;
import com.scaffold.lowcode.tenant.mapper.LcProvisionMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 租户资产开通：把平台租户（tenant_id = 1）的业务菜单、字典与内置参数复制给某个租户，
 * 并保证其两个内置角色存在且拿到对应授权：
 * <ul>
 *   <li><code>tenant_admin</code> 租户管理员 —— 本租户全部业务菜单，含写操作按钮；</li>
 *   <li><code>tenant_member</code> 租户成员 —— 只有页面与目录（即可查看的列表），不含任何按钮，
 *       新租户建普通用户时不必再从零手搓一个角色。</li>
 * </ul>
 *
 * <p>两个调用方共用这套逻辑：
 * <ul>
 *   <li>自助注册 —— 新租户从零开通；</li>
 *   <li>「补齐资产」—— 平台加了新菜单或新字典之后（例如 V5 的「低代码」段），已有租户不会自动获得，
 *       由平台管理员按租户补一次；缺什么补什么，重复执行安全。</li>
 * </ul>
 *
 * <p>菜单没有跨租户唯一键，靠<b>身份键</b>判断「这条模板菜单该租户有了没有」：
 * 优先 permission，其次 path，最后 menu_type + menu_name。
 */
@Service
public class TenantProvisionService {

    private static final String ADMIN_ROLE_NAME = "租户管理员";
    private static final String MEMBER_ROLE_NAME = "租户成员";
    private static final long TEMPLATE_TENANT_ID = TenantContext.PLATFORM_TENANT_ID;

    private final LcProvisionMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public TenantProvisionService(LcProvisionMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 补齐菜单 / 字典 / 内置参数，并把业务菜单授给该租户的内置角色：
     * 管理员拿全量，成员只拿页面与目录（只读）。
     *
     * @return 各项新增数量与两个内置角色 ID，供接口回显
     */
    @Transactional
    public Map<String, Object> syncAssets(long tenantId, String operator) {
        MenuCopyResult menus = copyMenus(tenantId, operator);
        int dictTypes = mapper.copyDictTypes(tenantId, TEMPLATE_TENANT_ID, operator);
        int dictData = mapper.copyDictData(tenantId, TEMPLATE_TENANT_ID, operator);
        int configs = mapper.copyConfigs(tenantId, TEMPLATE_TENANT_ID, operator);
        long roleId = ensureAdminRole(tenantId, operator);
        long memberRoleId = ensureMemberRole(tenantId, operator);
        grantMenus(roleId, menus.menuIds());
        grantMenus(memberRoleId, menus.readMenuIds());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("tenantId", tenantId);
        summary.put("menuAdded", menus.added());
        summary.put("dictTypeAdded", dictTypes);
        summary.put("dictDataAdded", dictData);
        summary.put("configAdded", configs);
        summary.put("roleId", roleId);
        summary.put("memberRoleId", memberRoleId);
        return summary;
    }

    /** 新租户的内置管理员角色（已存在则复用） */
    public long ensureAdminRole(long tenantId, String operator) {
        return ensureBuiltinRole(tenantId, PlatformBoundary.TENANT_ADMIN_ROLE_CODE, ADMIN_ROLE_NAME,
                "租户内置管理员，拥有本租户全部业务菜单", operator);
    }

    /** 新租户的内置只读成员角色（已存在则复用） */
    public long ensureMemberRole(long tenantId, String operator) {
        return ensureBuiltinRole(tenantId, PlatformBoundary.TENANT_MEMBER_ROLE_CODE, MEMBER_ROLE_NAME,
                "租户内置只读角色，可查看本租户全部业务页面，不含任何写操作按钮", operator);
    }

    private long ensureBuiltinRole(long tenantId, String roleCode, String roleName, String remark, String operator) {
        Long existing = mapper.selectRoleIdByCode(tenantId, roleCode);
        if (existing != null) {
            return existing;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantId", tenantId);
        row.put("roleName", roleName);
        row.put("roleCode", roleCode);
        row.put("remark", remark);
        mapper.insertBuiltinRole(row, operator);
        return numberAsLong(row.get("id"));
    }

    /** 建该租户的管理员账号并绑内置管理员角色；账号需全局唯一 */
    public long createAdminUser(long tenantId, String username, String rawPassword, long roleId, String operator) {
        if (mapper.countUsername(username) > 0) {
            throw new BusinessException("管理员账号已被使用，请换一个");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("tenantId", tenantId);
        row.put("username", username);
        row.put("password", passwordEncoder.encode(rawPassword));
        row.put("nickname", ADMIN_ROLE_NAME);
        mapper.insertAdminUser(row, operator);
        long userId = numberAsLong(row.get("id"));
        mapper.insertUserRole(userId, roleId);
        return userId;
    }

    /** 管理员账号是否已被占用（跨租户）：注册与手工开通都要先过这一关 */
    public long countUsername(String username) {
        return mapper.countUsername(username);
    }

    /** 注册开通写一条登录日志，平台管理员在「登录日志」里能看到租户是自己注册进来的 */
    public void recordRegistration(long tenantId, String username) {
        mapper.insertRegisterLog(tenantId, username);
    }

    /** 按平台模板逐条复制菜单，返回该租户当前持有的全部业务菜单 ID 与本次新增条数 */
    private MenuCopyResult copyMenus(long tenantId, String operator) {
        List<Map<String, Object>> templates = mapper.selectTemplateMenus(TEMPLATE_TENANT_ID);
        if (templates.isEmpty()) {
            throw new BusinessException("平台菜单模板为空，无法开通租户");
        }
        Map<String, Long> existingByKey = new LinkedHashMap<>();
        for (Map<String, Object> menu : mapper.selectTenantMenus(tenantId)) {
            existingByKey.putIfAbsent(identityKey(menu), numberAsLong(menu.get("id")));
        }
        // 模板按 id 升序，父级一定先于子级处理，因此 parentMap 里总能查到父级的新 ID
        Map<Long, Long> parentMap = new LinkedHashMap<>();
        List<Long> menuIds = new ArrayList<>();
        List<Long> readMenuIds = new ArrayList<>();
        int added = 0;
        for (Map<String, Object> template : templates) {
            long templateId = numberAsLong(template.get("id"));
            long templateParent = numberAsLong(template.get("parentId"));
            boolean writeButton = "btn".equals(text(template.get("menuType")));
            Long existingId = existingByKey.get(identityKey(template));
            if (existingId != null) {
                parentMap.put(templateId, existingId);
                menuIds.add(existingId);
                if (!writeButton) {
                    readMenuIds.add(existingId);
                }
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("tenantId", tenantId);
            row.put("parentId", templateParent == 0L ? 0L : parentMap.getOrDefault(templateParent, 0L));
            row.put("menuName", template.get("menuName"));
            row.put("menuType", template.get("menuType"));
            row.put("icon", template.get("icon"));
            row.put("path", template.get("path"));
            row.put("component", template.get("component"));
            row.put("permission", template.get("permission"));
            row.put("sort", template.get("sort"));
            row.put("visible", template.get("visible"));
            row.put("status", template.get("status"));
            mapper.insertMenu(row, operator);
            long newId = numberAsLong(row.get("id"));
            parentMap.put(templateId, newId);
            menuIds.add(newId);
            if (!writeButton) {
                readMenuIds.add(newId);
            }
            added++;
        }
        return new MenuCopyResult(menuIds, readMenuIds, added);
    }

    private void grantMenus(long roleId, List<Long> menuIds) {
        Set<Long> distinct = new HashSet<>(menuIds);
        distinct.forEach(menuId -> mapper.insertRoleMenu(roleId, menuId));
    }

    private static String identityKey(Map<String, Object> menu) {
        String permission = text(menu.get("permission"));
        if (!permission.isEmpty()) {
            return "perm:" + permission;
        }
        String path = text(menu.get("path"));
        if (!path.isEmpty()) {
            return "path:" + path;
        }
        return "name:" + text(menu.get("menuType")) + ":" + text(menu.get("menuName"));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long numberAsLong(Object value) {
        if (value == null) {
            throw new BusinessException("租户资产补齐失败：未取到自增主键");
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    /** 该租户当前持有的业务菜单 ID、其中可只读的页面节点 ID 与本次新增条数 */
    private record MenuCopyResult(List<Long> menuIds, List<Long> readMenuIds, int added) {
    }
}
