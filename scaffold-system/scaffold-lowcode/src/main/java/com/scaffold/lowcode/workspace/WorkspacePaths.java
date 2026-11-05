package com.scaffold.lowcode.workspace;

import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 工作区目录的定位规则（方案 §6.1）：{@code {工作区根}/{租户 ID}/{项目编码}}。
 *
 * <p>规则单独一个组件而不是留在 {@link ProjectRepositoryService} 里，是因为发布链路也要定位同一份目录：
 * 两处各拼一遍，一旦「租户 ID」与「项目编码」的取法漂开，就会出现「初始化铺在 A 目录、发布写进 B 目录」
 * 这种两边都自洽、真机上什么都没有的错。
 */
@Component
public class WorkspacePaths {

    private final Path root;

    public WorkspacePaths(@Value("${scaffold.lowcode.workspace-root:./lowcode-workspace}") String workspaceRoot) {
        this.root = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    /** 工作区根目录（容器里就是那个工作区卷） */
    public Path root() {
        return root;
    }

    /** 相对工作区根的路径，写进 {@code lc_project.workspace_path} 给人读 */
    public String relative(String projectCode) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文，无法定位工作区");
        }
        // 项目编码在新增时就按这条正则校验过，这里再判一次：路径是从库里读出来的，不能假定它没被别的入口写脏
        if (projectCode == null || !projectCode.matches("^[a-z][a-z0-9-]{1,31}$")) {
            throw new BusinessException("项目编码不符合约定，无法定位工作区目录：" + projectCode);
        }
        return tenantId + "/" + projectCode;
    }

    /** 绝对路径 */
    public Path of(String projectCode) {
        return root.resolve(relative(projectCode));
    }
}
