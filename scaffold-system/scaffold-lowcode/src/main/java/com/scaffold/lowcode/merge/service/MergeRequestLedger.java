package com.scaffold.lowcode.merge.service;

import com.scaffold.lowcode.merge.mapper.LcMergeRequestFileMapper;
import com.scaffold.lowcode.merge.mapper.LcMergeRequestMapper;
import com.scaffold.lowcode.workspace.ChangedFile;
import com.scaffold.lowcode.workspace.MergeCheck;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合并请求的账（PR 8 第一刀）。
 *
 * <p>单独一个类而不是服务里的私有方法，只为让 {@code @Transactional} 真的生效：Spring 按代理织入，
 * 自己调自己不穿过代理。要事务的理由和 {@code PublishLedger} 一样 —— 请求行与文件清单是一次结论的两半，
 * 中途失败留下「有请求但清单是上一次的」比整体不写更坏：页面上那两个数与下面的清单会说相反的话。
 */
@Service
public class MergeRequestLedger {

    private final LcMergeRequestMapper requestMapper;
    private final LcMergeRequestFileMapper fileMapper;

    public MergeRequestLedger(LcMergeRequestMapper requestMapper, LcMergeRequestFileMapper fileMapper) {
        this.requestMapper = requestMapper;
        this.fileMapper = fileMapper;
    }

    /**
     * 落一次重算的结论：有活着的旧请求就地覆盖，没有就新开一条。
     *
     * @param existing 同一分支对上还活着的请求行，没有时传 null
     */
    @Transactional
    public long record(Long projectId, Map<String, Object> existing, String title, String sourceBranch,
                       String targetBranch, String sourceSha, String targetSha, String mergeBaseSha,
                       int status, int checkStatus, String checkMessage, List<ChangedFile> files,
                       MergeCheck check, String operator) {
        long requestId;
        if (existing != null) {
            requestId = ((Number) existing.get("id")).longValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", requestId);
            row.put("title", title);
            row.put("sourceSha", sourceSha);
            row.put("targetSha", targetSha);
            row.put("mergeBaseSha", mergeBaseSha);
            row.put("status", status);
            row.put("checkStatus", checkStatus);
            row.put("checkMessage", checkMessage);
            requestMapper.updateOnRefresh(row, operator);
            fileMapper.deleteByRequest(requestId);
        } else {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("projectId", projectId);
            row.put("title", title);
            row.put("description", null);
            row.put("sourceBranch", sourceBranch);
            row.put("targetBranch", targetBranch);
            row.put("sourceSha", sourceSha);
            row.put("targetSha", targetSha);
            row.put("mergeBaseSha", mergeBaseSha);
            row.put("status", status);
            row.put("checkStatus", checkStatus);
            row.put("checkMessage", checkMessage);
            requestMapper.insert(row, operator);
            requestId = ((Number) row.get("id")).longValue();
        }
        if (!files.isEmpty()) {
            fileMapper.insertBatch(fileRows(requestId, files, check));
        }
        return requestId;
    }

    /** 只标陈旧，不动提交号与清单 */
    @Transactional
    public void markStale(long requestId, String message, String operator) {
        requestMapper.markStale(requestId, message, operator);
    }

    private List<Map<String, Object>> fileRows(long requestId, List<ChangedFile> files, MergeCheck check) {
        List<String> conflicts = check == null ? List.of() : check.conflictedPaths();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ChangedFile file : files) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("requestId", requestId);
            row.put("path", file.path());
            row.put("oldPath", file.oldPath());
            row.put("changeType", file.change().name());
            row.put("isConflict", conflicts.contains(file.path()) ? 1 : 0);
            row.put("additions", file.additions());
            row.put("deletions", file.deletions());
            rows.add(row);
        }
        return rows;
    }
}
