package com.scaffold.lowcode.project.service;

import com.scaffold.common.domain.PageQuery;
import com.scaffold.common.domain.PageResult;
import com.scaffold.common.exception.BusinessException;
import com.scaffold.common.export.CsvUtils;
import com.scaffold.framework.security.SecurityUtils;
import com.scaffold.lowcode.project.domain.ProjectStatusRequest;
import com.scaffold.lowcode.project.domain.ProjectSyncStatus;
import com.scaffold.lowcode.project.domain.ProjectWriteRequest;
import com.scaffold.lowcode.model.mapper.LcModelMapper;
import com.scaffold.lowcode.project.mapper.LcProjectMapper;
import com.scaffold.lowcode.repo.RepoUrlValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目管理。项目是租户名下的一个 Git 仓库（方案 §1.1），本期只到「绑定仓库」为止：
 * 仓库连通性检查与初始化快照推送在 PR 4 落地，这里只保证 repo_url 形态合法、分支名合法。
 */
@Service
public class LcProjectService {

    private static final String DEFAULT_MAIN_BRANCH = "main";
    private static final String DEFAULT_GEN_BRANCH = "platform-generated";

    private final LcProjectMapper mapper;
    private final LcModelMapper modelMapper;

    public LcProjectService(LcProjectMapper mapper, LcModelMapper modelMapper) {
        this.mapper = mapper;
        this.modelMapper = modelMapper;
    }

    public PageResult<Map<String, Object>> projects(PageQuery query, String keyword, Integer status) {
        String key = trimToNull(keyword);
        long total = mapper.countProjects(key, status);
        List<Map<String, Object>> rows = total == 0
                ? List.of()
                : mapper.selectProjects(key, status, query.offset(), query.normalizedPageSize());
        rows.forEach(this::withSyncStatusText);
        return PageResult.of(rows, total, query.normalizedPageNum(), query.normalizedPageSize());
    }

    /** sync_status 的文案只在 ProjectSyncStatus 里定义一次，SQL 里不再抄一份 CASE */
    private Map<String, Object> withSyncStatusText(Map<String, Object> row) {
        Object value = row.get("syncStatus");
        int syncStatus = value instanceof Number number ? number.intValue() : 0;
        row.put("syncStatusText", ProjectSyncStatus.text(syncStatus));
        return row;
    }

    public String exportProjects(String keyword, Integer status) {
        List<Map<String, Object>> rows = mapper.selectProjects(trimToNull(keyword), status, 0, 5000);
        List<List<Object>> data = rows.stream()
                .map(row -> CsvUtils.row(row.get("projectCode"), row.get("projectName"), row.get("repoUrl"),
                        row.get("mainBranch"), row.get("genBranch"), row.get("syncStatusText"),
                        row.get("lastPublishTime"), statusText(row.get("status")), row.get("createTime"), row.get("remark")))
                .toList();
        return CsvUtils.csv(List.of("项目编码", "项目名称", "仓库地址", "主干分支", "发布分支", "同步状态", "最后发布", "状态", "创建时间", "备注"), data);
    }

    /** 模型/页面模块选择项目用的精简列表：只回启用中的项目 */
    public List<Map<String, Object>> options() {
        return mapper.selectProjects(null, 1, 0, 200).stream().map(this::withSyncStatusText).map(row -> {
            Map<String, Object> option = new LinkedHashMap<>();
            for (String key : List.of("id", "projectCode", "projectName", "mainBranch", "genBranch", "syncStatus", "syncStatusText", "workspacePath")) {
                option.put(key, row.get(key));
            }
            return option;
        }).toList();
    }

    @Transactional
    public Map<String, Object> create(ProjectWriteRequest request) {
        String projectCode = required(request.getProjectCode(), "项目编码不能为空");
        if (mapper.countProjectCode(projectCode, null) > 0) {
            throw new BusinessException("项目编码已存在");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectCode", projectCode);
        row.put("projectName", required(request.getProjectName(), "项目名称不能为空"));
        row.put("projectDesc", trimToNull(request.getProjectDesc()));
        row.put("repoUrl", RepoUrlValidator.normalize(request.getRepoUrl()));
        row.put("mainBranch", branchOrDefault(request.getMainBranch(), DEFAULT_MAIN_BRANCH));
        row.put("genBranch", branchOrDefault(request.getGenBranch(), DEFAULT_GEN_BRANCH));
        row.put("status", request.getStatus() == null ? 1 : request.getStatus());
        row.put("remark", trimToNull(request.getRemark()));
        requireDistinctBranches(row);
        mapper.insertProject(row, operator());
        return Map.of("id", row.get("id"));
    }

    @Transactional
    public void update(Long id, ProjectWriteRequest request) {
        requireProject(id);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("projectName", trimToNull(request.getProjectName()));
        row.put("projectDesc", trimToNull(request.getProjectDesc()));
        row.put("repoUrl", RepoUrlValidator.normalize(request.getRepoUrl()));
        row.put("mainBranch", trimToNull(request.getMainBranch()));
        row.put("genBranch", trimToNull(request.getGenBranch()));
        row.put("status", request.getStatus());
        row.put("remark", trimToNull(request.getRemark()));
        requireDistinctBranches(row);
        if (mapper.updateProject(id, row, operator()) == 0) {
            throw new BusinessException("项目不存在或已删除");
        }
    }

    @Transactional
    public void updateStatus(Long id, ProjectStatusRequest request) {
        int status = request.getStatus();
        if (status != 0 && status != 1) {
            throw new BusinessException("状态只能为 1 正常或 0 停用");
        }
        requireProject(id);
        if (mapper.updateProjectStatus(id, status, operator()) == 0) {
            throw new BusinessException("项目不存在或已删除");
        }
    }

    @Transactional
    public void delete(Long id) {
        requireProject(id);
        if (modelMapper.countModelsByProject(id) > 0) {
            throw new BusinessException("项目下还有模型，请先删除模型");
        }
        if (mapper.softDeleteProject(id, operator()) == 0) {
            throw new BusinessException("项目不存在或已删除");
        }
    }

    private void requireProject(Long id) {
        if (mapper.selectProjectBrief(id) == null) {
            throw new BusinessException("项目不存在");
        }
    }

    /** 主干与发布分支必须不同：同一条分支既被人工写又被平台推，合并请求就没有意义了 */
    private static void requireDistinctBranches(Map<String, Object> row) {
        Object main = row.get("mainBranch");
        Object gen = row.get("genBranch");
        if (main != null && main.equals(gen)) {
            throw new BusinessException("主干分支与发布分支不能相同");
        }
    }

    private static String branchOrDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private static String statusText(Object value) {
        if (value == null) {
            return "";
        }
        long number = value instanceof Number numberValue ? numberValue.longValue() : Long.parseLong(String.valueOf(value));
        return number == 1L ? "正常" : "停用";
    }

    private String operator() {
        return SecurityUtils.getUsername();
    }

    private static String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
